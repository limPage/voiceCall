package com.hjict.voiceai;

import android.annotation.SuppressLint;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.util.Base64;
import android.util.Log;

import org.json.JSONObject;

import java.io.InputStreamReader;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedList;
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.ArrayList;
import java.util.List;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class VoiceAIService extends Service {
    static {
        System.loadLibrary("native-lib");
    }
    private static final String TAG = "VoiceAIService";
    private static final int REQUEST_CODE_PERMISSIONS = 2;
    private AudioRecord audioRecord;
    private boolean isRecording = false;
    private static final int SAMPLE_RATE = 16000;
    private static final int CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO;
    private static final int AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT;
    private Thread recordingThread;
    private Handler handler = new Handler(Looper.getMainLooper());
    private ExecutorService analysisExecutor = Executors.newSingleThreadExecutor();

    private boolean isContinuousRecording = false;
    private Queue<String> audioFileQueue = new LinkedList<>();
    private static final int MAX_AUDIO_FILES = 100;
    private String currentRecordingFile = null;
    private boolean isFirstRecording = true; // 첫 녹음 여부 추적

    //    private static final String SELECTED_ENCODER = "whisper_encoder_base_10s.rknn";
//    private static final String SELECTED_ENCODER = "static_whisper_base_ko_10s_encoder_ver05.rknn";
    private static final String SELECTED_ENCODER = "static_whisper_base_ko_merged_spelling_ver0.2_10s_encoder.rknn";
    //    private static final String SELECTED_DECODER = "whisper_decoder_base_10s.rknn";
//    private static final String SELECTED_DECODER = "static_whisper_base_ko_10s_decoder_ver05.rknn";
    private static final String SELECTED_DECODER = "static_whisper_base_ko_merged_spelling_ver0.2_10s_decoder.rknn";
    private static final String SELECTED_VOCAB = "vocab_ko.txt";

    public static MyBroadcastReceiver receiver = null;

    public static IntentFilter intentFilter = null;
    // OkHttpClient를 클래스 멤버로 선언하여 단일 인스턴스 유지
    private OkHttpClient okHttpClient;

    private static final String LOG_SEPARATOR = "========= End of sending voice messages =========";

    private List<String> garbageKeywords = null; // Cache for file-based keywords

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "Service created");
        setupFiles();

        // OkHttpClient 초기화
        okHttpClient = new OkHttpClient();

        // IntentFilter 초기화
        if (intentFilter == null) {
            intentFilter = new IntentFilter();
            intentFilter.addAction(H500.Intent.ACTION_PJSIP_END_CALL);

            try{
                Process process = Runtime.getRuntime().exec("chmod -R 777 " + getFilesDir());
                process.waitFor();
                if (process.exitValue() != 0) {
                    Log.e(TAG, "Failed to set execute permission");
                    return;
                }
            }catch (Exception e){
                Log.d(TAG, "onCreate: permission error - "+e.getMessage());
            }

        }
        // Load garbage keywords from file
        loadGarbageKeywords();
    }
    // Method to load keywords from /sdcard/Download/detected.txt or assets/detected.txt
    private void loadGarbageKeywords() {
        List<String> keywords = new ArrayList<>();

        // Try reading from /sdcard/Download/detected.txt
        try {
            File file = new File("/sdcard/Download/detected.txt");
            if (file.exists()) {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file)))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        line = line.trim();
                        if (!line.isEmpty()) {
                            keywords.add(line);
                        }
                    }
                }
                if (!keywords.isEmpty()) {
                    garbageKeywords = keywords;
                    Log.d(TAG, "Loaded " + garbageKeywords.size() + " garbage keywords from /sdcard/Download/detected.txt");
                    return;
                } else {
                    Log.w(TAG, "/sdcard/Download/detected.txt is empty, falling back to assets");
                }
            } else {
                Log.w(TAG, "/sdcard/Download/detected.txt not found, falling back to assets");
            }
        } catch (SecurityException e) {
            Log.e(TAG, "Permission denied reading /sdcard/Download/detected.txt: " + e.getMessage());
        } catch (IOException e) {
            Log.e(TAG, "Error reading /sdcard/Download/detected.txt: " + e.getMessage());
        }

        // Try reading from assets/detected.txt
        try {
            try (InputStream is = getAssets().open("detected.txt");
                 BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (!line.isEmpty()) {
                        keywords.add(line);
                    }
                }
            }
            if (!keywords.isEmpty()) {
                garbageKeywords = keywords;
                Log.d(TAG, "Loaded " + garbageKeywords.size() + " garbage keywords from assets/detected.txt");
                return;
            } else {
                Log.w(TAG, "assets/detected.txt is empty, using default keywords");
            }
        } catch (IOException e) {
            Log.e(TAG, "Error reading assets/detected.txt: " + e.getMessage());
        }

        // Fall back to default keywords
        garbageKeywords = new ArrayList<>(Arrays.asList(
            "응응", "아아", "어어", "으으", "음음", "아이아이", "오오", "헤헤", "지금지금", "에에", "와우와우","그그"
        ));
        Log.d(TAG, "Using default " + garbageKeywords.size() + " garbage keywords");
    }
        private Boolean getDetectedGarbageKeyword(String result) {
        if (garbageKeywords == null) {
            Log.w(TAG, "Garbage keywords not loaded, reloading");
            loadGarbageKeywords();
        }

        for (String keyword : garbageKeywords) {
            if (result.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "Service started");

        if (receiver == null) {
            receiver = new VoiceAIService.MyBroadcastReceiver();
            registerReceiver(receiver, intentFilter, Context.RECEIVER_EXPORTED);
        }

        if (!isContinuousRecording) {
            toggleContinuousRecording();
        }
        return START_STICKY; // 시스템에 의해 종료되면 재시작
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void listAssets(String path, int indent) throws IOException {
        String[] items = getAssets().list(path);
        if (items == null || items.length == 0) {
            Log.d(TAG, "[Assets] " + "  " + (path.isEmpty() ? "/" : path) + ": (empty)");
            return;
        }

        for (String item : items) {
            String itemPath = path.isEmpty() ? item : path + "/" + item;
            String[] subItems = getAssets().list(itemPath);
            if (subItems != null && subItems.length > 0) {
                Log.d(TAG, "[Assets] " + "  " + itemPath + "/");
                listAssets(itemPath, indent + 1);
            } else {
                Log.d(TAG, "[Assets] " + "  " + itemPath);
            }
        }
    }

    private void setupFiles() {

        try{
            Log.d(TAG, "setupFiles: ");
            listAssets("",0);
        }catch (IOException e){
            Log.e(TAG, "setupFiles: "+e.getMessage() );
        }
        // 파일 설정 로직 (변경 없음)
        File execDir = new File(getFilesDir(), "exec");
        if (!execDir.exists()) execDir.mkdirs();
        File modelDir = new File(getFilesDir(), "model");
        if (!modelDir.exists()) modelDir.mkdirs();
        File libDir = new File(getFilesDir(), "lib");
        if (!libDir.exists()) libDir.mkdirs();
        File audioDir = new File(getFilesDir(), "audio");
        if (!audioDir.exists()) audioDir.mkdirs();

        try {
            String[] libFiles = getAssets().list("lib");
            if (libFiles != null) {
                for (String fileName : libFiles) {
                    Log.d(TAG, " - " + fileName);
                }
            } else {
                Log.d(TAG, "assets/lib is empty or does not exist.");
            }
        } catch (IOException e) {
            Log.e(TAG, "Failed to list assets/lib", e);
        }

        try {
            String[] assetList = getAssets().list("");
            if (assetList != null) {
                for (String name : assetList) {
                    if (name.startsWith("rknn")) {
                        // 파일인지 확인 (폴더면 open 시도 시 IOException 발생)
                        try (InputStream is = getAssets().open(name)) {
                            // 파일로 간주하고 작업 진행
                            File execFile = new File(execDir, name);
                            if (!execFile.exists()) {
                                try (FileOutputStream fos = new FileOutputStream(execFile)) {
                                    byte[] buffer = new byte[1024];
                                    int len;
                                    while ((len = is.read(buffer)) != -1) {
                                        fos.write(buffer, 0, len);
                                    }
                                }
                                Log.d(TAG, "Copied exec file: " + name);
                            }
                        } catch (IOException e) {
                            // 폴더거나 파일이 아니므로 무시
                            Log.d(TAG, name + " is not a file, skipped.");
                        }
                    }
                }
            }


            String[] modelFiles = getAssets().list("model");
            if (modelFiles != null) {
                for (String modelFile : modelFiles) {
                    File destModelFile = new File(modelDir, modelFile);
                    if (!destModelFile.exists()) {
                        try (InputStream is = getAssets().open("model/" + modelFile);
                             FileOutputStream fos = new FileOutputStream(destModelFile)) {
                            byte[] buffer = new byte[1024];
                            int len;
                            while ((len = is.read(buffer)) != -1) {
                                fos.write(buffer, 0, len);
                            }
                            Log.d(TAG, "Copied model file: " + modelFile);
                        }
                    }
                }
            }

            String[] libFiles = {"librga.so", "librknnrt.so"};
            for (String libFile : libFiles) {
                File destLibFile = new File(libDir, libFile);
                if (!destLibFile.exists()) {
                    try (InputStream is = getAssets().open("lib/" + libFile);
                         FileOutputStream fos = new FileOutputStream(destLibFile)) {
                        byte[] buffer = new byte[1024];
                        int len;
                        while ((len = is.read(buffer)) != -1) {
                            fos.write(buffer, 0, len);
                        }
                    }
                }
            }
            Process process = Runtime.getRuntime().exec("chmod -R 777 " + getFilesDir());
            process.waitFor();
            if (process.exitValue() != 0) {
                Log.e(TAG, "Failed to set execute permission");
            }
        } catch (Exception e) {
            Log.e(TAG, "Setup failed: " + e.getMessage());
        }
    }


    private String generateAudioFileName() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd_HH-mm-ss");
        String timestamp = sdf.format(new Date());
        return "voice_" + timestamp + ".wav";
    }

    private void manageAudioFiles() {
        File audioDir = new File(getFilesDir(), "audio");
        File[] files = audioDir.listFiles((d, name) -> name.endsWith(".wav"));
        if (files != null && files.length > MAX_AUDIO_FILES) {
            Arrays.sort(files, Comparator.comparingLong(File::lastModified));
            for (int i = 0; i < files.length - MAX_AUDIO_FILES; i++) {
                files[i].delete();
            }
        }
    }

    private void toggleContinuousRecording() {
        if (!isContinuousRecording) {
            if (isRecording) {
                return;
            }
            isContinuousRecording = true;
            isFirstRecording = true; // 첫 녹음 플래그 설정
            Log.d(TAG, "Continuous recording started at " + System.currentTimeMillis());
            handler.post(() -> {
                startRecordingAndAnalysis(); // 초기 녹음 및 분석 시작
            });
        } else {
            isContinuousRecording = false;
            handler.removeCallbacksAndMessages(null);
            stopRecording(null);
            Log.d(TAG, "Continuous recording stopped at " + System.currentTimeMillis());
            handler.post(() -> {
            });
        }
    }

    private void startRecordingAndAnalysis() {
        if (!isContinuousRecording) return;

        // 이전 녹음 파일을 큐에 추가 (첫 녹음 제외)
        if (currentRecordingFile != null && !isFirstRecording) {
            audioFileQueue.offer(currentRecordingFile);
            manageAudioFiles();
            analyzeNextAudio(); // 분석 시작
        }

        // 새 녹음 시작
        String audioFileName = generateAudioFileName();
        currentRecordingFile = audioFileName;
//        Log.d(TAG, "Starting recording: " + audioFileName);
        startRecording(audioFileName);

        // 첫 녹음 시 3초 보장
        if (isFirstRecording) {
            handler.postDelayed(() -> {
                if (isContinuousRecording && isRecording) {
                    stopRecording(currentRecordingFile);
                    isFirstRecording = false; // 첫 녹음 완료
                    startRecordingAndAnalysis(); // 다음 녹음 및 분석
                }
            }, 3000); // 3초 후 종료
        }
    }

    @SuppressLint("MissingPermission")// 빨간줄 나오는 경고 무시
    private void startRecording(String fileName) {
        // 기존 startRecording() 그대로 유지
        int bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT) * 4;
        audioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC, SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT, bufferSize);
        if (audioRecord.getState() != AudioRecord.STATE_INITIALIZED) {
            Log.e(TAG, "AudioRecord initialization failed at " + System.currentTimeMillis());
            return;
        }
        audioRecord.startRecording();
        isRecording = true;

        File audioDir = new File(getFilesDir(), "audio");
        File outputFile = new File(audioDir, fileName);
        recordingThread = new Thread(() -> recordAudio(outputFile));
        recordingThread.start();
//        Log.d(TAG, "Recording started for " + fileName + " at " + System.currentTimeMillis() + " with buffer size: " + bufferSize);
    }

    private void stopRecording(String fileName) {
        // 기존 stopRecording() 그대로 유지
//         if (audioRecord != null) {
//             isRecording = false;
//             audioRecord.stop();
//             audioRecord.release();
//             audioRecord = null;
//             try {
//                 recordingThread.join(1000);
//             } catch (InterruptedException e) {
//                 Log.e(TAG, "Recording thread interrupted: " + e.getMessage());
//             }
// //            Log.d(TAG, "Recording stopped for " + (fileName != null ? fileName : "unknown") + " at " + System.currentTimeMillis());
//         }

        if (audioRecord != null) {
            isRecording = false;
            try {
                // Only call stop() if the AudioRecord is initialized and recording
                if (audioRecord.getState() == AudioRecord.STATE_INITIALIZED &&
                    audioRecord.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING) {
                    audioRecord.stop();
                }
            } catch (IllegalStateException e) {
                Log.e(TAG, "Failed to stop AudioRecord: " + e.getMessage());
            }
            try {
                audioRecord.release();
            } catch (Exception e) {
                Log.e(TAG, "Failed to release AudioRecord: " + e.getMessage());
            }
            audioRecord = null;
            if (recordingThread != null) {
                try {
                    recordingThread.join(1000);
                    recordingThread = null;
                } catch (InterruptedException e) {
                    Log.e(TAG, "Recording thread interrupted: " + e.getMessage());
                }
            }
//            Log.d(TAG, "Recording stopped for " + (fileName != null ? fileName : "unknown") + " at " + System.currentTimeMillis());
        }
    }

    private void recordAudio(File outputFile) {
        // 기존 recordAudio()에서 첫 녹음 3초 제한 제거
        try (FileOutputStream fos = new FileOutputStream(outputFile)) {
            byte[] header = new byte[44];
            fos.write(header);

            int bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT) * 4;
            short[] audioData = new short[bufferSize];
            long totalDataLen = 0;
            long startTime = System.currentTimeMillis();
            int totalSamples = 0;

            while (isRecording) { // 분석 완료 시 isRecording=false로 종료
                int read = audioRecord.read(audioData, 0, bufferSize);
                if (read > 0) {
                    ByteBuffer buffer = ByteBuffer.allocate(read * 2);
                    buffer.order(ByteOrder.LITTLE_ENDIAN);
                    for (int i = 0; i < read; i++) {
                        buffer.putShort(audioData[i]);
                    }
                    fos.write(buffer.array());
                    totalDataLen += read * 2;
                    totalSamples += read;
//                    Log.d(TAG, "Wrote " + read + " samples to " + outputFile.getName() + ", total samples: " + totalSamples + " at " + (System.currentTimeMillis() - startTime) + "ms");
                } else if (read < 0) {
                    Log.w(TAG, "Audio read error for " + outputFile.getName() + " at " + (System.currentTimeMillis() - startTime) + "ms, read: " + read);
                }
            }
//            Log.d(TAG, "Recording loop exited after " + (System.currentTimeMillis() - startTime) + "ms with total samples: " + totalSamples + " for " + outputFile.getName());

            writeWavHeader(outputFile, totalDataLen);
//            Log.d(TAG, "File writing completed for " + outputFile.getName() + " with total length: " + totalDataLen + " bytes");
        } catch (Exception e) {
            Log.e(TAG, "Recording failed: " + e.getMessage() + " at " + System.currentTimeMillis());
        }
    }

    private void writeWavHeader(File file, long totalAudioLen) throws Exception {
        // 기존 writeWavHeader() 그대로 유지
        long totalDataLen = totalAudioLen + 36;
        long byteRate = SAMPLE_RATE * 2;
        try (RandomAccessFile raf = new RandomAccessFile(file, "rw")) {
            raf.seek(0);
            raf.writeBytes("RIFF");
            raf.writeInt(Integer.reverseBytes((int) totalDataLen));
            raf.writeBytes("WAVE");
            raf.writeBytes("fmt ");
            raf.writeInt(Integer.reverseBytes(16));
            raf.writeShort(Short.reverseBytes((short) 1));
            raf.writeShort(Short.reverseBytes((short) 1));
            raf.writeInt(Integer.reverseBytes(SAMPLE_RATE));
            raf.writeInt(Integer.reverseBytes((int) byteRate));
            raf.writeShort(Short.reverseBytes((short) 2));
            raf.writeShort(Short.reverseBytes((short) 16));
            raf.writeBytes("data");
            raf.writeInt(Integer.reverseBytes((int) totalAudioLen));
//            Log.d(TAG ,"WAV header written for " + file.getName() + " with totalDataLen: " + totalDataLen + ", byteRate: " + byteRate + ", sampleRate: " + SAMPLE_RATE);
        }
    }

    private void analyzeNextAudio() {
        if (audioFileQueue.isEmpty()) {
            return;
        }

        String audioFile = audioFileQueue.poll();
        if (audioFile == null) {
            return;
        }
        // === 파일 크기 검사 추가 ===
        File audioPathFile = new File(getFilesDir(), "audio/" + audioFile);

        // === 파일 크기 검사 추가 ===
        long fileSize = audioPathFile.length();
        if (fileSize == 0) {
            Log.e(TAG, "분석할 음성 파일이 비어있음: " + audioFile);
            return;
        } else if (fileSize < 100) {
            Log.e(TAG, "분석할 음성 파일 크기가 비정상적으로 작음 (" + fileSize + " bytes): " + audioFile);
            return;
        }

//        Log.d(TAG, "Submitting analysis for: " + audioFile + " at " + System.currentTimeMillis());
        analysisExecutor.submit(() -> {
            File execFile = new File(getFilesDir(), "exec/rknn_whisper_demo_base_10s_static");
            String execPath = execFile.getAbsolutePath();
            File modelDir = new File(getFilesDir(), "model");
            String encoderPath = new File(modelDir, SELECTED_ENCODER).getAbsolutePath();
            String decoderPath = new File(modelDir, SELECTED_DECODER).getAbsolutePath();
            String lang = SELECTED_VOCAB.replace("vocab_", "").replace(".txt", "");
            String audioPath = new File(new File(getFilesDir(), "audio"), audioFile).getAbsolutePath();
            String libPath = new File(getFilesDir(), "lib").getAbsolutePath();

            if (!execFile.exists()) {
                Log.e(TAG, "Executable not found: " + execPath);
                return;
            }

            long startTime = System.currentTimeMillis();
            try {
                String result = executeWhisperDemo(execPath, encoderPath, decoderPath, lang, audioPath, libPath);
                String cleanedResult = result.replace(" ", "").replace(".", "").replace("?", "").replace("!", "");
                long duration = (System.currentTimeMillis() - startTime) / 1000;
                //2초가 넘는 유의미한 텍스트인지 확인
                if (duration != 1 && !result.startsWith("No Whisper") && !result.startsWith("Error:") && cleanedResult.length() >= 2){
                    // 위험 감지 키워드 검사
                    if (containsEmergencyKeyword(cleanedResult)) {
                        Log.d(TAG, "Emergency keyword detected:" + result);
                        String detectedKeyword = getDetectedKeyword(cleanedResult); // 검출된 키워드 추출
                        sendToServer(audioFile, result, detectedKeyword, duration);
                        saveToAudio2Folder(audioFile, result); // audio2 폴더에 만들어서 위험감지 wav를 저장하는 기능. 디버그 용
                        // toggleContinuousRecording(); // 통화를 한다면 녹음 및 분석 중지
                        // sendBroadcast(new Intent(H500.Intent.ACTION_PJSIP_MAKE_CALL), H500.Permission.H500_BROADCAST_PERMISSION);
                        // Log.w(TAG,"관제센터로 연결합니다.");
                    // 필요없는 텍스트 감지인지 확인하고 서버 전송
                    } else if (!getDetectedGarbageKeyword(cleanedResult)){
                        sendToServer(audioFile, result, "not_detected", duration);
                        saveToAudio2Folder(audioFile, result);
                    } else {
                        Log.i(TAG, audioFile.replace(".wav", "") + " - " + result + " [Invalid message] [ Analysis delay: "  + duration + "s]");
                    }
                } else {
                    Log.i(TAG, audioFile.replace(".wav", "") + " - " + (result.isEmpty() ? "No output" : result) + " [ Analysis delay: "  + duration + "s]");
                }
            } catch (Exception e) {
                Log.e(TAG, "Analysis failed for " + audioFile + ": " + e.getMessage());
            }

            // 분석 시간 측정
            long endTime = System.currentTimeMillis();
            long elapsedTime = endTime - startTime;
            long delay = Math.max(0, 3000 - elapsedTime);// 최소 녹음시간 3초 보장. 학습용으로

            // 분석 완료 후 최소 3초 보장 후 녹음 종료 및 재시작
            handler.postDelayed(() -> {
                if (isContinuousRecording && isRecording) {
                    stopRecording(currentRecordingFile);
                    startRecordingAndAnalysis(); // 분석 끝난 후 즉시 재녹음 및 분석
                }
            }, delay);
//            });
        });
    }
    private boolean containsEmergencyKeyword(String result) {
        String[] keywords = {"살려", "살려줘", "살려주", "사람살려", "산려주", "산려줘", "선려주", "선려줘", "살여주", "살여줘", "설여주", "설여줘", "사려주", "사려줘","살리어주","살리어줘",
                             "서려주", "서려줘", "살요주", "살요줘", "쌀려주", "쌀여주", "산여주", "산여줘", "설려주", "설려줘", "할려주", "할려줘", "사리어주","사리어줘", "쌀려줘", "쌀여줘",
                             "도와", "도와주", "도와줘", "도아주", "도아줘" , "동아주", "도하주", "도하줘", "더워주", "더워줘", "도마주", "도마줘", "도워주", "도워줘", "동아줘",
                             "더와주","더와줘" };
        for (String keyword : keywords) {
            if (result.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private String getDetectedKeyword(String result) {

        // 살려주세요
        String[] saveKeywords = {"살려", "살려주", "산려주", "살리어주", "선려주", "살여주", "설여주", "사려주", "사리어주", "서려주", "살요주", "쌀려주", "쌀여주", "할려주", "산여주", "설려주"};

        // 살려줘요
        String[] saveKeywords2 = {"살려줘", "산려줘", "살리어줘", "선려줘", "살여줘", "설여줘", "사려줘", "사리어줘", "서려줘", "살요줘", "쌀려줘", "쌀여줘", "할려줘", "산여줘", "설려줘"};

        // "도와" 관련 키워드 그룹
        String[] helpKeywords = {"도와", "도와주", "도아주", "동아주", "도하주", "더워주", "도마주", "더와주", "도워주"};

        String[] helpKeywords2 = {"도와줘", "도아줘", "동아줘", "도하줘", "더워줘", "도마줘", "더와줘", "도워줘"};


        //1. "살려" 그룹 체크
        for (String keyword : saveKeywords) {
            if (result.contains(keyword)) {
                return "살려주세요"; // "살려" 관련 키워드 감지 시 "살려주세요" 반환
            }
        }
        for (String keyword : saveKeywords2) {
            if (result.contains(keyword)) {
                return "살려줘요"; // "살려" 관련 키워드 감지 시 "살려주세요" 반환
            }
        }
        //2. "도와" 그룹 체크
        for (String keyword : helpKeywords) {
            if (result.contains(keyword)) {
                return "도와주세요"; // "도와주" 관련 키워드 감지 시 "도와주세요" 반환
            }
        }
        for (String keyword : helpKeywords2) {
            if (result.contains(keyword)) {
                return "도와줘요"; // "도와줘" 관련 키워드 감지 시 "도와줘요" 반환
            }
        }
        if (result.contains("사람살려")){
            return "사람살려";
        } 
        return ""; // 매칭되는 키워드가 없으면 빈 문자열 반환
    }

    // private Boolean getDetectedGarbageKeyword(String result) {
    //     String[] GarbageKeyword = {"응응", "아아", "어어", "으으", "음음", "아이아이", "오오", "헤헤", "지금지금", "에에", "아이아이","와우와우"};
    //     Boolean isGarbageTranscripts = false;
    //     for (String keyword : GarbageKeyword) {
    //         if (result.contains(keyword)) {
    //             isGarbageTranscripts = true;
    //             break;
    //         }
    //     }
    //     return isGarbageTranscripts;
    // }

    private void saveToAudio2Folder(String audioFile, String detectedKeyword) {
        try {
            // audio2 폴더 생성
            File audio2Dir = new File(getFilesDir(), "audio2");
            if (!audio2Dir.exists()) {
                audio2Dir.mkdirs();
            }

            // 원본 파일
            File sourceFile = new File(getFilesDir(), "audio/" + audioFile);

            // 새 파일명 생성 (검출 키워드 추가)
            String newFileName = audioFile.replace(".wav", "_" + detectedKeyword + ".wav");
            File destFile = new File(audio2Dir, newFileName);

            // 파일 이동
            if (sourceFile.renameTo(destFile)) {
                Log.d(TAG, "File moved to audio2 folder: " + destFile.getAbsolutePath());
            } else {
                Log.e(TAG, "Failed to move file to audio2 folder: " + newFileName);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error saving to audio2 folder: " + e.getMessage());
        }
    }

/*    private void saveToAudio2Folder(String audioFile, String detectedKeyword) { // 기기에도 wav를 저장한다면 파일 이동 부분 주석해제
        try {
            // audio2 폴더 생성
//            File audio2Dir = new File(getFilesDir(), "audio2");
//            if (!audio2Dir.exists()) {
//                audio2Dir.mkdirs();
//            }

            // 원본 파일
            File sourceFile = new File(getFilesDir(), "audio/" + audioFile);

//            // 새 파일명 생성 (검출 키워드 추가)
//            String newFileName = audioFile.replace(".wav", "_" + detectedKeyword + ".wav");
//            File destFile = new File(audio2Dir, newFileName);

            // 새 파일명 생성 (원본 파일명 유지)
//            String newFileName = audioFile;
//            File destFile = new File(audio2Dir, newFileName);

            // 파일 이동
//            if (sourceFile.renameTo(destFile)) {
            if (sourceFile.isFile()) {
//                Log.d(TAG, "File moved to audio2 folder: " + sourceFile.getAbsolutePath());
                Log.d(TAG, "File path & name: " + sourceFile.getAbsolutePath());
                // 서버로 Base64 인코딩된 파일과 함께 전송
                sendToServer(audioFile, detectedKeyword, sourceFile);
//                sendToServer(newFileName, detectedKeyword, destFile);
            } else {
                Log.e(TAG, "Failed to move file to audio2 folder: " + audioFile);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error saving to audio2 folder: " + e.getMessage());
        }
    }
*/
    private void sendToServer(String fileName, String transcripts, String detectedText, long duration) {
        File audioFile = new File(getFilesDir(), "audio/" + fileName);
        // Log.d(TAG, "\n========= Send voice messages ===================\nFile : " + audioFile.getAbsolutePath());
        Log.d(TAG, "========= Send voice messages ===================");
        // Log.i(TAG, "Analysis delay: "+ duration + "s");
        if (!detectedText.equals("not_detected")){
            Log.w(TAG,"※ 관제 센터로 위험이 감지된 메시지를 보냅니다. ※\nAnalysis delay: "+ duration + "s\nMessage: [ "+ detectedText + " ]");
        } else{
            Log.w(TAG,"※ 관제 센터로 감지된 메시지를 보냅니다. ※\nAnalysis delay: "+ duration + "s\nMessage: [ " + transcripts + " ]" );
        }

        if (okHttpClient == null) {
            Log.e(TAG, "OkHttpClient is null, reinitializing");
            okHttpClient = new OkHttpClient();
        }
        // 파일을 Base64로 인코딩
        String base64Audio;
        try {
            base64Audio = encodeFileToBase64(audioFile);
            // Log.d(TAG, "Base64 encoded data sample: " + base64Audio.substring(0, Math.min(50, base64Audio.length())));
            // Log.d(TAG, "WAV encoding complete.");
        } catch (Exception e) {
            Log.e(TAG, "Failed to encode file to Base64: " + e.getMessage() + "\n" + LOG_SEPARATOR);
            return;
        }

        int port = Settings.System.getInt(getContentResolver(), H500.Settings.ADMIN_SERVER_PORT, 8080);
        String serverIp = Settings.System.getString(getContentResolver(), H500.Settings.SIP_SERVER);
        String lineNumber = Settings.System.getString(getContentResolver(), H500.Settings.SIP_LINE_NUMBER);

        // JSON 데이터 생성
        JSONObject jsonObject = new JSONObject();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd+HH:mm:ss");
        String callDate = sdf.format(new Date());
        try {

            jsonObject.put("src", lineNumber); // 단말 내선번호
            jsonObject.put("callDate", callDate); // 현재 시간 사용
            jsonObject.put("file", base64Audio); // 인코딩 파일 문자열
            jsonObject.put("fileName", fileName); // 파일명
            jsonObject.put("transcripts", transcripts); // 감지된 키워드
            if (detectedText.equals("not_detected")){
                jsonObject.put("label", ""); // 감지된 위험키워드가 없다면 일반 키워드 
            } else{
                jsonObject.put("label", detectedText); // 감지된 위험 키워드
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to create JSON: " + e.getMessage() + "\n" + LOG_SEPARATOR);
            return;
        }

        /* ex)
            "src":"5000",
            "callDate": "2025-04-01+16:26:41",
            "file":"base64 인코딩된 문자열",
            "fileName": "voice_20250331_14-30-45_도와줘.wav",
            "transcripts": "도와줘",
            "label": "도와"
        */

        // 서버 URL 
        //String serverUrl = "http://10.10.10.190:8080/ebm_admin/getEmergencyVoice.cors";
        String serverUrl = "http://" +serverIp +":" +port +"/ebm_admin/getEmergencyVoice.cors";
        // 요청 바디 생성
        RequestBody requestBody = RequestBody.create(
                MediaType.parse("application/json; charset=utf-8"),
                jsonObject.toString()
        );

        // 요청 생성
        Request request = new Request.Builder()
                .url(serverUrl)
                .post(requestBody)
                .build();

        // 비동기 요청 실행
        new Thread(() -> {
            Log.d(TAG, "Message info: src-" + lineNumber + ", audio-"+ fileName + ", date-" +callDate + "\nServer url: " + serverUrl);
            try (Response response = okHttpClient.newCall(request).execute()) {
                if (response.isSuccessful()) {
                    String responseBody = Objects.requireNonNull(response.body()).string();
                    Log.d(TAG, "Server response: " + responseBody + "\n" + LOG_SEPARATOR);
                } else {
                    Log.e(TAG, "Server request failed: " + response.code() + " - " + response.message() + "\n" + LOG_SEPARATOR);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error sending to server: " + e.getMessage() + "\n" + LOG_SEPARATOR);
            }
        }).start();
    }

    // 인코딩
    private String encodeFileToBase64(File file) throws Exception {
        FileInputStream fileInputStream = new FileInputStream(file);
        byte[] bytes = new byte[(int) file.length()];
        int bytesRead = fileInputStream.read(bytes);
        fileInputStream.close();

        if (bytesRead != file.length()) {
            Log.e(TAG, "Failed to read entire file: expected " + file.length() + ", read " + bytesRead);
            throw new Exception("Incomplete file read");
        }

        // NO_WRAP 옵션으로 줄바꿈 제거
        String base64String = Base64.encodeToString(bytes, Base64.NO_WRAP);
        return base64String;
    }

    public native String executePermission(String path);
    public native String executeWhisperDemo(String execPath, String encoderPath, String decoderPath,
                                            String lang, String audioPath, String libPath);

    @Override
    public void onDestroy() {
        super.onDestroy();
        isContinuousRecording = false;
        handler.removeCallbacksAndMessages(null);
        stopRecording(null); // Call stopRecording even if audioRecord is null to ensure cleanup
        if (receiver != null) {
            unregisterReceiver(receiver);
            receiver = null;
        }
        if (!analysisExecutor.isShutdown()) {
            analysisExecutor.shutdown();
        }


        // if (audioRecord != null) {
        //     stopRecording(null);
        // }
        // if (receiver != null) {
        //     unregisterReceiver(receiver);
        //     receiver = null;
        // }
        // analysisExecutor.shutdown();

        // OkHttpClient 리소스 정리
        if (okHttpClient != null) {
            okHttpClient.dispatcher().executorService().shutdown();
            okHttpClient.connectionPool().evictAll();
            Log.d(TAG, "OkHttpClient resources cleaned up");
        }
    }

    private class MyBroadcastReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(H500.Intent.ACTION_PJSIP_END_CALL)) {
                Log.i(TAG, "ACTION_PJSIP_END_CALL received");

                if (!isContinuousRecording) {
                    toggleContinuousRecording(); // 녹음 및 분석 재시작
                }
            }
        }
    }


}