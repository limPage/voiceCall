package com.hjict.voiceai;

import android.annotation.SuppressLint;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaMetadataRetriever;
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


    private static final String SELECTED_VOCAB = "vocab_ko.txt";

    private BroadcastReceiver pjsipReceiver;
//    private BroadcastReceiver keywordFetchReceiver;

    // OkHttpClient를 클래스 멤버로 선언하여 단일 인스턴스 유지
    private OkHttpClient okHttpClient;

    private static final String LOG_SEPARATOR = "========= End of sending voice messages =========";

    private List<String> garbageKeywords = null; // Cache for file-based keywords

    VoiceAINanoServer voiceAiServer;

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "Service created");
        setupFiles();

        // OkHttpClient 초기화
        okHttpClient = new OkHttpClient();

        // IntentFilter 초기화
        IntentFilter intentFilter = new IntentFilter(H500.Intent.ACTION_PJSIP_END_CALL);
//        IntentFilter fetchFilter = new IntentFilter("com.hjict.voiceai.ACTION_FETCH_KEYWORDS"); //  이 부분 추가할 것

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
        pjsipReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                Log.i(TAG, "ACTION_PJSIP_END_CALL received");
                if (!isContinuousRecording) {
                    toggleContinuousRecording(); // 녹음 및 분석 재시작
                }
            }
        };
//        keywordFetchReceiver = new BroadcastReceiver() {
//            @Override
//            public void onReceive(Context context, Intent intent) {
//                Log.i(TAG, "키워드 동기화 요청(Broadcast) 수신");
//                fetchAndApplyKeywordsFromControlServer();
//            }
//        };

        registerReceiver(pjsipReceiver,intentFilter, Context.RECEIVER_EXPORTED);
//        registerReceiver(keywordFetchReceiver,fetchFilter, Context.RECEIVER_EXPORTED);

        // 키워드 자동 동기화
        fetchAndApplyKeywordsFromControlServer();

        // Load garbage keywords from file
        loadGarbageKeywords();
        voiceAiServer = new VoiceAINanoServer(getApplicationContext());
        try {
            voiceAiServer.start();
            Log.i("VoiceAiNano", "NanoHTTPD started on port "+Settings.System.getInt(getContentResolver(), H500.Settings.WHITELIST_PORT, 8040));
        } catch (IOException e) {
            Log.e("VoiceAiNano", "Failed to start NanoHTTPD", e);
        }
    }
    // Method to load keywords from /sdcard/Download/detected.txt or assets/detected.txt
    private void loadGarbageKeywords() {
        List<String> keywords = new ArrayList<>();
        File sdcardFile = new File("/sdcard/Whisper/model/detected.txt");

        // 1. Try reading from /sdcard/Whisper/model/detected.txt
        try {
            if (sdcardFile.exists()) {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(sdcardFile)))) {
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
                    Log.d(TAG, "✅ Loaded " + keywords.size() + " garbage keywords from /sdcard/Whisper/model/detected.txt");
                    return;
                } else {
                    Log.w(TAG, "⚠️ /sdcard/Whisper/model/detected.txt is empty, falling back to assets.");
                }
            } else {
                Log.w(TAG, "⚠️ /sdcard/Whisper/model/detected.txt not found, falling back to assets.");
            }
        } catch (SecurityException e) {
            Log.e(TAG, "❌ Permission denied reading /sdcard/Whisper/model/detected.txt: " + e.getMessage());
        } catch (IOException e) {
            Log.e(TAG, "❌ Error reading /sdcard/Whisper/model/detected.txt: " + e.getMessage());
        }

        // 2. Try reading from assets/model/detected.txt and copy to sdcard
        try {
            // ensure folder exists
            File parentDir = sdcardFile.getParentFile();
            if (!parentDir.exists()) parentDir.mkdirs();

            try (
                    InputStream is = getAssets().open("model/detected.txt");
                    BufferedReader reader = new BufferedReader(new InputStreamReader(is));
                    FileOutputStream fos = new FileOutputStream(sdcardFile)  // copy file to sdcard
            ) {
                String line;
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (!line.isEmpty()) {
                        keywords.add(line);
                        fos.write((line + "\n").getBytes());  // write to copied file
                    }
                }
                fos.flush();
            }

            if (!keywords.isEmpty()) {
                garbageKeywords = keywords;
                Log.d(TAG, "✅ Loaded " + keywords.size() + " garbage keywords from assets and copied to /sdcard/Whisper/model/detected.txt");
                return;
            } else {
                Log.w(TAG, "⚠️ assets/model/detected.txt is empty, using default keywords.");
            }

        } catch (IOException e) {
            Log.e(TAG, "❌ Error reading or copying assets/model/detected.txt: " + e.getMessage());
        }

        // 3. Fall back to empty default
        garbageKeywords = new ArrayList<>();
        Log.w(TAG, "⚠️ Using default (empty) garbage keywords list.");
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
        // File modelDir = new File(getFilesDir(), "model");
        // if (!modelDir.exists()) modelDir.mkdirs();
        File execDir = new File(getFilesDir(), "exec");
        if (!execDir.exists()) execDir.mkdirs();
        File modelDir = new File("/sdcard/Whisper/model");
        if (!modelDir.exists()) modelDir.mkdirs();
        File libDir = new File(getFilesDir(), "lib");
        if (!libDir.exists()) libDir.mkdirs();
        File audioDir = new File(getFilesDir(), "audio");
        if (!audioDir.exists()) audioDir.mkdirs();
        File staticDir = new File(getFilesDir(), "static");
        if (!staticDir.exists()) staticDir.mkdirs();
        File keywordsDir = new File(getFilesDir(), "keywords");
        if (!keywordsDir.exists()) keywordsDir.mkdirs();


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
            Log.d(TAG, "d :"+execDir.listFiles());
            Log.d(TAG, ""+execDir.listFiles().length);

            if (execDir.listFiles() == null || execDir.listFiles().length == 0) {
                Log.d(TAG, "exec 디렉토리에 파일이 없습니다. rknn 파일 생성.");
                // 1. 실행 파일 확인 및 복사 rknn으로 시작하는 파일이 없으면 기본 실행파일을 가져온다.
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
            } else {
                Log.d(TAG, "rknn file checked");
            }

            //  2. 모델 파일 확인 및 복사
            try {
                File[] existingFiles = modelDir.listFiles();
                if (existingFiles == null || existingFiles.length <= 1) {
                    Log.d(TAG, "모델 디렉토리에 파일이 없어 복사 시작");

                    String[] modelFiles = getAssets().list("model");
                    for (String modelFileName : modelFiles) {
                        File modelFile = new File(modelDir, modelFileName);
                        if (!modelFile.exists()) {
                            try (InputStream is = getAssets().open("model/" + modelFileName);
                                FileOutputStream fos = new FileOutputStream(modelFile)) {
                                byte[] buffer = new byte[1024];
                                int len;
                                while ((len = is.read(buffer)) != -1) {
                                    fos.write(buffer, 0, len);
                                }
                                Log.d(TAG, "복사된 모델 파일: " + modelFile.getAbsolutePath());
                            }
                        }
                    }
                } else {
                    Log.d(TAG, "model file checked");
                }
            } catch (IOException e) {
                Log.e(TAG, "모델 파일 복사 실패: " + e.getMessage());
            }

            String[] libFiles = {"librga.so", "librknnrt.so"};
            for (String libFile : libFiles) {//
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

            String[] staticFiles = getAssets().list("static");
            for (String staticFile : staticFiles) {
                File destStaticFile = new File(staticDir, staticFile);
                if (!destStaticFile.exists()) {
                    Log.d(TAG, destStaticFile.getAbsolutePath());
                    try (InputStream is = getAssets().open("static/" + staticFile);
                        FileOutputStream fos = new FileOutputStream(destStaticFile)) {
                        byte[] buffer = new byte[1024];
                        int len;
                        while ((len = is.read(buffer)) != -1) {
                            fos.write(buffer, 0, len);
                        }
                    }
                }
            }


            File keywordMapFile = new File(keywordsDir, "keyword_map.json");
            if (!keywordMapFile.exists()) {
                try (InputStream is = getAssets().open("keywords/keyword_map.json");
                     FileOutputStream fos = new FileOutputStream(keywordMapFile)) {
                    byte[] buffer = new byte[1024];
                    int len;
                    while ((len = is.read(buffer)) != -1) {
                        fos.write(buffer, 0, len);
                    }
                    Log.d(TAG, "keyword_map.json 파일 복사 완료");
                } catch (IOException e) {
                    Log.e(TAG, "백업 파일 복사 실패: " + e.getMessage());
                }
                KeywordManager.load(getApplicationContext()); // 화이트 리스트 적용
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
        SimpleDateFormat sdf = new SimpleDateFormat("yyMMdd_HH-mm-ss");
        String timestamp = sdf.format(new Date());
        String lineNumber = Settings.System.getString(getContentResolver(), H500.Settings.SIP_LINE_NUMBER);
        if (lineNumber.isEmpty()){
            lineNumber = "unknown";
        }
        return "voice_" + lineNumber + "_" + timestamp + ".wav";
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
            Log.d(TAG, "Continuous recording stopped at " + System.currentTimeMillis());
            handler.post(() -> {
                stopRecording(null);
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
            scheduleNextRecordingIfNeeded();
            return;
        }

        String audioFile = audioFileQueue.poll();
        if (audioFile == null) {
            scheduleNextRecordingIfNeeded();
            return;
        }
        // === 파일 크기 검사 추가 ===
        File audioPathFile = new File(getFilesDir(), "audio/" + audioFile);

        // === 파일 크기 검사 추가 ===
        long fileSize = audioPathFile.length();
        if (fileSize == 0) {
            Log.e(TAG, "분석할 음성 파일이 비어있음: " + audioFile);
            scheduleNextRecordingIfNeeded();
            return;
        }
        else if (fileSize < 100) {
            Log.e(TAG, "분석할 음성 파일 크기가 작음. pass (" + fileSize + " bytes): " + audioFile);
            scheduleNextRecordingIfNeeded();
            return;
        }

        // 모델 크기 출력 0618
        MediaMetadataRetriever mmr = new MediaMetadataRetriever();
        try {
            mmr.setDataSource(audioPathFile.getPath());
            String durationStr = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
            long durationMs = Long.parseLong(durationStr);
            long durationSec = durationMs / 1000;
            Log.d(TAG, "음성 길이: " + durationSec + "초 (" + durationMs + "ms)");

            // 예시: 너무 짧으면 분석 생략
            if (durationSec < 1) {
                Log.e(TAG, "음성 파일 길이가 너무 짧음: " + durationSec + "초");
                scheduleNextRecordingIfNeeded();
                return;
            }

        } catch (Exception e) {
            Log.e(TAG, "오디오 길이 측정 실패: " + e.getMessage());
        } finally {
            try {
                mmr.release();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        // 모델 크기 출력0618

//        long lastModified = audioPathFile.lastModified();
//        long now = System.currentTimeMillis();
//        if ((now - lastModified) < 1000) {
//            Log.w(TAG, "작은 파일 분석 생략: " + audioFile);
//            scheduleNextRecordingIfNeeded();
//            return;
//        }

//        Log.d(TAG, "Submitting analysis for: " + audioFile + " at " + System.currentTimeMillis());
        analysisExecutor.submit(() -> {
            // File modelDir = new File(getFilesDir(), "model");
            // String encoderPath = new File(modelDir, SELECTED_ENCODER).getAbsolutePath();
            // String decoderPath = new File(modelDir, SELECTED_DECODER).getAbsolutePath();
            File execFile = new File(getFilesDir(), "exec/rknn_whisper_demo_base_10s_static");
            String execPath = execFile.getAbsolutePath();
            File modelDir = new File("/sdcard/Whisper/model");
            File[] modelFiles = modelDir.listFiles();
            String encoderPath = null;
            String decoderPath = null;

            if (modelFiles != null) {
                for (File file : modelFiles) {
                    String name = file.getName().toLowerCase();

                    if (encoderPath == null && name.contains("encoder")) {
                        encoderPath = file.getAbsolutePath();
                    } else if (decoderPath == null && name.contains("decoder")) {
                        decoderPath = file.getAbsolutePath();
                    }

                    if (encoderPath != null && decoderPath != null) {
                        break; // 둘 다 찾았으면 종료
                    }
                }
            }
            if (encoderPath == null || decoderPath == null) {
                Log.e(TAG, "모델 파일 중 encoder 또는 decoder가 없습니다.");
                return;
            }
            
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
                String cleanedResult = result.replace(" ", "").replace(".", "").replace("?", "").replace("!", "").replace("~", "");
                long duration = (System.currentTimeMillis() - startTime) / 1000;
                //2초가 넘는 유의미한 텍스트인지 확인
                if (duration != 1 && !result.startsWith("No Whisper") && !result.startsWith("Error:") && cleanedResult.length() >= 2){
                    // 필요없는 텍스트 감지인지 확인하고 서버 전송
                    if (!getDetectedGarbageKeyword(cleanedResult)){
                        String detectedKeyword = KeywordManager.matchEmergencyKeyword(cleanedResult);
                        if (detectedKeyword.equals("not_detected")){
                            Log.d(TAG, "Emergency keyword not detected:" + detectedKeyword);
                            sendToServer(audioFile, result, "not_detected", duration, encoderPath); //화이트리스트만 서버에 전송하기
                            Log.i(TAG, audioFile.replace(".wav", "") + " - " + result + " [Invalid message] [ Analysis delay: "  + duration + "s]");
                        } else if (!detectedKeyword.isEmpty()){
                            Log.d(TAG, "Emergency keyword detected:" + detectedKeyword);
                            Log.i(TAG, audioFile.replace(".wav", "") + " - " + result + " [ message] [ Analysis delay: "  + duration + "s]");
//                        toggleContinuousRecording(); // 통화를 한다면 녹음 및 분석 중지
                            sendToServer(audioFile, result, detectedKeyword, duration, encoderPath);
//                        sendBroadcast(new Intent(H500.Intent.ACTION_PJSIP_MAKE_CALL), H500.Permission.H500_BROADCAST_PERMISSION);
//                        Log.w(TAG,"관제센터로 연결합니다.");
                        }
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

            // 위험 상황이 감지되어 녹음이 중단되었으면 다음 분석을 예약하지 않음
            if (!isContinuousRecording || !isRecording) {
                return;
            }

            // 분석 완료 후 최소 3초 보장 후 녹음 종료 및 재시작
            handler.postDelayed(() -> {
                if (isContinuousRecording && isRecording) {
                    stopRecording(currentRecordingFile);
                    startRecordingAndAnalysis();
                }
            }, delay);
        });
    }

    private void sendToServer(String fileName, String transcripts, String detectedText, long duration, String encoderPath) { // 2025-05-21 인코더 파일명 추출해서 버전명 서버로 보내는 기능 추가
        File audioFile = new File(getFilesDir(), "audio/" + fileName);
        // Log.d(TAG, "\n========= Send voice messages ===================\nFile : " + audioFile.getAbsolutePath());
        Log.d(TAG, "========= Send voice messages ===================");
        // Log.i(TAG, "Analysis delay: "+ duration + "s");
        if (!detectedText.equals("not_detected")){
            Log.w(TAG,"※ 관제 센터로 위험이 감지된 메시지를 보냅니다. ※\nAnalysis delay: "+ duration + "s\nMessage: [ "+ detectedText + " ]");
        } else {
            Log.w(TAG,"※ 관제 센터로 감지된 메시지를 보냅니다. ※\nAnalysis delay: "+ duration + "s\nMessage: [ " + transcripts + " ]" );
        }

        if (okHttpClient == null) {
            Log.e(TAG, "OkHttpClient is null, reinitializing");
            okHttpClient = new OkHttpClient();
        }
        // 파일을 Base64로 인코딩
        String base64Audio;
        try {
            base64Audio = VoiceAIUtil.encodeFileToBase64(audioFile);
            // Log.d(TAG, "Base64 encoded data sample: " + base64Audio.substring(0, Math.min(50, base64Audio.length())));
            // Log.d(TAG, "WAV encoding complete.");
//             Log.d(TAG, base64Audio);

        } catch (Exception e) {
            Log.e(TAG, "Failed to encode file to Base64: " + e.getMessage() + "\n" + LOG_SEPARATOR);
            return;
        }

        int port = Settings.System.getInt(getContentResolver(), H500.Settings.ADMIN_SERVER_PORT, 8080);
        String serverIp = Settings.System.getString(getContentResolver(), H500.Settings.SIP_SERVER);
        String lineNumber = Settings.System.getString(getContentResolver(), H500.Settings.SIP_LINE_NUMBER);

        // mk 빌드 방식에서 주석 해제할것
//        String sipRegistStatus = SystemProperties.get("tcc.selfCheck.sipRegistStatus", "0");
//        if (!sipRegistStatus.equals("1")){
//            Log.e(TAG, "SIP가 연결 되어 있지 않습니다. 전송을 중단합니다.");
//            stopRecording(null);
//            Settings.System.putInt(getContentResolver(), H500.Settings.VOICE_AI_STATE, 0);
//            new Handler(Looper.getMainLooper()).postDelayed(() -> {
//                Log.w(TAG, "SIP NULL - VoiceAIService 종료");
//                stopSelf();
//            }, 1000);
//            return;
//        }

        if (serverIp == null || serverIp.isEmpty()) {
            Log.e(TAG, "서버 IP가 설정되어 있지 않습니다. 전송을 중단합니다.");
            stopRecording(null);
            Settings.System.putInt(getContentResolver(), H500.Settings.VOICE_AI_STATE, 0);
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                Log.e(TAG, "SERVER IP NULL - VoiceAIService 종료");
                stopSelf();
            }, 1000);
            return;
        }

        if (lineNumber == null || lineNumber.isEmpty()) {
            Log.e(TAG, "내선 번호가 설정되어 있지 않습니다. 전송을 중단합니다.");
            stopRecording(null);
            Settings.System.putInt(getContentResolver(), H500.Settings.VOICE_AI_STATE, 0);
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                Log.e(TAG, "SIP NULL - VoiceAIService 종료");
                stopSelf();
            }, 1000);
            return;
        }

        // lch std 2025-05-21 서버에 버전명 전송 추가
        // ex) String path = "/sdcard/Whisper/model/0.2_encoder.rknn";
        String[] partsBySlash = encoderPath.split("/");
        // ex) [ "", "sdcard", "Whisper", "model", "0.2_encoder.rknn" ] 
        String modelName = partsBySlash[partsBySlash.length - 1]; // 마지막 요소 = "0.2_encoder.rknn"
        String[] partsByUnderscore = modelName.split("_");
        // ex [ "0.2", "encoder.rknn" ]
        String versionName = partsByUnderscore[0];



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
            } else {
                jsonObject.put("label", detectedText); // 감지된 위험 키워드
            }
            jsonObject.put("version", versionName); // 모델 버전
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
            "version": "1.7"
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
            Log.d(TAG, "Message info: src-" + lineNumber + ", audio-"+ fileName + ", version-"+ versionName + ", date-" +callDate + "\nServer url: " + serverUrl);
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


    public native String executePermission(String path);
    public native String executeWhisperDemo(String execPath, String encoderPath, String decoderPath,
                                            String lang, String audioPath, String libPath);

    @Override
    public void onDestroy() {
        super.onDestroy();
        isContinuousRecording = false;
        handler.removeCallbacksAndMessages(null);
        stopRecording(null); // Call stopRecording even if audioRecord is null to ensure cleanup
        if (pjsipReceiver != null) {
            unregisterReceiver(pjsipReceiver);
            pjsipReceiver = null;
        }
//        if (keywordFetchReceiver != null) {
//            unregisterReceiver(keywordFetchReceiver);
//            keywordFetchReceiver = null;
//        }
        if (!analysisExecutor.isShutdown()) {
            analysisExecutor.shutdown();
        }


        // if (audioRecord != null) {
        //     stopRecording(null);
        // }

        // analysisExecutor.shutdown();

        // OkHttpClient 리소스 정리
        if (okHttpClient != null) {
            okHttpClient.dispatcher().executorService().shutdown();
            okHttpClient.connectionPool().evictAll();
            Log.d(TAG, "OkHttpClient resources cleaned up");
        }

        // NanoServer도 종료
        if (voiceAiServer != null) {
            voiceAiServer.stop();
            Log.d(TAG, "NanoServer stopped");
        }

        // 관제 서버로 종료 알림 전송
//        new Thread(() -> {
//            String url = "http://10.10.10.51:8080/ebm_admin/getVoiceAiOnOff.cors?src=5000&voiceActive=false";
//            OkHttpClient client = new OkHttpClient();
//            Request request = new Request.Builder().url(url).build();
//            try (Response response = client.newCall(request).execute()) {
//                if (response.isSuccessful()) {
//                    Log.i(TAG, "관제 서버에 종료 상태 전송 완료");
//                } else {
//                    Log.w(TAG, "관제 서버 응답 오류: " + response.code());
//                }
//            } catch (Exception e) {
//                Log.e(TAG, "관제 서버 전송 실패: " + e.getMessage());
//            }
//        }).start(); // 네트워크는 반드시 별도 스레드에서 수행
    }


    private void scheduleNextRecordingIfNeeded() {

        handler.postDelayed(() -> {
            if (isContinuousRecording && isRecording) {
                stopRecording(currentRecordingFile);
                startRecordingAndAnalysis();
            }
        }, 3000);
    }

    private void fetchAndApplyKeywordsFromControlServer() {
        String serverIp = Settings.System.getString(getContentResolver(), H500.Settings.SIP_SERVER);
        String lineNumber = Settings.System.getString(getContentResolver(), H500.Settings.SIP_LINE_NUMBER);
        int port = Settings.System.getInt(getContentResolver(), H500.Settings.ADMIN_SERVER_PORT, 8080);
        Log.w(TAG, "관제 서버로 부터 whitelist 동기화를 진행합니다.");

        // mk 빌드시 주석 해제할 것
//        String sipRegistStatus = SystemProperties.get("tcc.selfCheck.sipRegistStatus", "0");
//        if (!sipRegistStatus.equals("1")){
//            Log.e(TAG, "SIP가 연결 되어 있지 않습니다. 전송을 중단합니다.");
//            stopRecording(null);
//              Settings.System.putInt(getContentResolver(), H500.Settings.VOICE_AI_STATE, 0);
//            new Handler(Looper.getMainLooper()).postDelayed(() -> {
//                Log.w(TAG, "SIP NULL - VoiceAIService 종료");
//                stopSelf();
//            }, 1000);
//            return;
//        }

        if (serverIp == null || serverIp.isEmpty()) {
            Log.e(TAG, "서버 IP가 설정되어 있지 않습니다. 동기화를 중단합니다.");
            stopRecording(null);
            Settings.System.putInt(getContentResolver(), H500.Settings.VOICE_AI_STATE, 0);
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                Log.w(TAG, "SERVER IP NULL - VoiceAIService 종료");
                stopSelf();
            }, 1000);
            return;
        }

        if (lineNumber == null || lineNumber.isEmpty()) {
            Log.e(TAG, "내선 번호가 설정되어 있지 않습니다. 동기화를 중단합니다.");
            stopRecording(null);
            Settings.System.putInt(getContentResolver(), H500.Settings.VOICE_AI_STATE, 0);
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                Log.w(TAG, "SIP NULL - VoiceAIService 종료");
                stopSelf();
            }, 1000);
            return;
        }

        String controlServerUrl = "http://"+ serverIp + ":" + port + "/ebm_admin/getVoiceAiKeyword.cors";

        new Thread(() -> {
            try {
                Request request = new Request.Builder()
                        .url(controlServerUrl)
                        .get()
                        .build();

                Response response = okHttpClient.newCall(request).execute();
                if (response.isSuccessful()) {
                    String responseBody = Objects.requireNonNull(response.body()).string();

                    JSONObject keywordJson = new JSONObject(responseBody);
                    JSONObject keywordMap = KeywordManager.convertKeywordListFormat(keywordJson);

                    KeywordManager.sync(getApplicationContext(), keywordMap);
                    Log.i(TAG, "✅ 키워드 동기화 완료 " + keywordMap);
                } else {
                    Log.e(TAG, "❌ 키워드 동기화 실패: HTTP " + response.code());
                }
            } catch (Exception e) {
                Log.e(TAG, "❌ 키워드 동기화 중 오류: " + e.getMessage());
            }
        }).start();
    }

}
