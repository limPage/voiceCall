//package com.hjict.voicecallai;
//
//import android.app.Service;
//import android.content.BroadcastReceiver;
//import android.content.Context;
//import android.content.Intent;
//import android.content.IntentFilter;
//import android.media.AudioFormat;
//import android.media.AudioRecord;
//import android.media.MediaRecorder;
//import android.os.Handler;
//import android.os.IBinder;
//import android.os.Looper;
//import android.util.Log;
//
//import java.io.File;
//import java.io.FileOutputStream;
//import java.io.InputStream;
//import java.io.RandomAccessFile;
//import java.nio.ByteBuffer;
//import java.nio.ByteOrder;
//import java.text.SimpleDateFormat;
//import java.util.Arrays;
//import java.util.Comparator;
//import java.util.Date;
//import java.util.LinkedList;
//import java.util.Queue;
//
//public class VoiceCallAIService extends Service {
//    static {
//        System.loadLibrary("native-lib");
//    }
//
//    private static final String TAG = "VoiceCallAIService";
//
//    private AudioRecord audioRecord;
//    private boolean isRecording = false;
//    private static final int SAMPLE_RATE = 16000;
//    private static final int CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO;
//    private static final int AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT;
//    private Thread recordingThread;
//    private Thread analysisThread;
//    private Handler handler = new Handler(Looper.getMainLooper());
//
//    private boolean isContinuousRecording = false;
//    private Queue<String> audioFileQueue = new LinkedList<>();
//    private static final int MAX_AUDIO_FILES = 100;
//    private boolean isRecordingThreadRunning = false;
//    private boolean isAnalysisThreadRunning = false;
//    private final Object queueLock = new Object();
//
////    private static final String SELECTED_ENCODER = "whisper_encoder_base_10s.rknn";
//    private static final String SELECTED_ENCODER = "static_whisper_base_ko_10s_encoder.rknn";
////    private static final String SELECTED_DECODER = "whisper_decoder_base_10s.rknn";
//    private static final String SELECTED_DECODER = "static_whisper_base_ko_10s_decoder.rknn";
//    private static final String SELECTED_VOCAB = "vocab_ko.txt";
//    public static MyBroadcastReceiver receiver = null;
//    public static IntentFilter intentFilter = null;
//
//
//    @Override
//    public void onCreate() {
//        super.onCreate();
//        Log.d(TAG, "Service created");
//        setupFiles();
//
//        // IntentFilter 초기화
//        if (intentFilter == null) {
//            intentFilter = new IntentFilter();
//            intentFilter.addAction(H500.Intent.ACTION_PJSIP_END_CALL);
//
//            try{
//                Process process = Runtime.getRuntime().exec("chmod -R 777 " + getFilesDir());
//                process.waitFor();
//                if (process.exitValue() != 0) {
//                    Log.e(TAG, "Failed to set execute permission");
//                    return;
//                }
//            }catch (Exception e){
//                Log.d(TAG, "onCreate: permission error - "+e.getMessage());
//            }
//
//        }
//    }
//
//    @Override
//    public int onStartCommand(Intent intent, int flags, int startId) {
//        Log.d(TAG, "Service started");
//
//        if (receiver == null) {
//            receiver = new MyBroadcastReceiver();
//            registerReceiver(receiver, intentFilter, Context.RECEIVER_EXPORTED);
//        }
//
//        if (!isContinuousRecording) {
//            startContinuousRecording();
////            toggleContinuousRecording();
//        }
//        return START_STICKY; // 시스템에 의해 종료되면 재시작
//    }
//
//    @Override
//    public IBinder onBind(Intent intent) {
//        return null;
//    }
//
//    private void setupFiles() {
//        // 파일 설정 로직 (변경 없음)
//        File execDir = new File(getFilesDir(), "exec");
//        if (!execDir.exists()) execDir.mkdirs();
//        File modelDir = new File(getFilesDir(), "model");
//        if (!modelDir.exists()) modelDir.mkdirs();
//        File libDir = new File(getFilesDir(), "lib");
//        if (!libDir.exists()) libDir.mkdirs();
//        File audioDir = new File(getFilesDir(), "audio");
//        if (!audioDir.exists()) audioDir.mkdirs();
//
//        try {
//            File execFile = new File(execDir, "rknn_whisper_demo_base_10s_static");
//            if (!execFile.exists()) {
//                try (InputStream is = getAssets().open("rknn_whisper_demo_base_10s_static");
//                     FileOutputStream fos = new FileOutputStream(execFile)) {
//                    byte[] buffer = new byte[1024];
//                    int len;
//                    while ((len = is.read(buffer)) != -1) {
//                        fos.write(buffer, 0, len);
//                    }
//                }
//            }
//
//            String[] modelFiles = getAssets().list("model");
//            if (modelFiles != null) {
//                for (String modelFile : modelFiles) {
//                    File destModelFile = new File(modelDir, modelFile);
//                    if (!destModelFile.exists()) {
//                        try (InputStream is = getAssets().open("model/" + modelFile);
//                             FileOutputStream fos = new FileOutputStream(destModelFile)) {
//                            byte[] buffer = new byte[1024];
//                            int len;
//                            while ((len = is.read(buffer)) != -1) {
//                                fos.write(buffer, 0, len);
//                            }
//                            Log.d(TAG, "Copied model file: " + modelFile);
//                        }
//                    }
//                }
//            }
//
//            String[] libFiles = {"librga.so", "librknnrt.so"};
//            for (String libFile : libFiles) {
//                File destLibFile = new File(libDir, libFile);
//                if (!destLibFile.exists()) {
//                    try (InputStream is = getAssets().open("lib/" + libFile);
//                         FileOutputStream fos = new FileOutputStream(destLibFile)) {
//                        byte[] buffer = new byte[1024];
//                        int len;
//                        while ((len = is.read(buffer)) != -1) {
//                            fos.write(buffer, 0, len);
//                        }
//                    }
//                }
//            }
//            Process process = Runtime.getRuntime().exec("chmod -R 777 " + getFilesDir());
//            process.waitFor();
//            if (process.exitValue() != 0) {
//                Log.e(TAG, "Failed to set execute permission");
//            }
//        } catch (Exception e) {
//            Log.e(TAG, "Setup failed: " + e.getMessage());
//        }
//    }
//
//    private String generateAudioFileName() {
//        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd_HH:mm:ss");
//        String timestamp = sdf.format(new Date());
//        return timestamp + ".wav";
//    }
//
//    private void manageAudioFiles() {
//        File audioDir = new File(getFilesDir(), "audio");
//        File[] files = audioDir.listFiles((d, name) -> name.endsWith(".wav"));
//        if (files != null && files.length > MAX_AUDIO_FILES) {
//            Arrays.sort(files, Comparator.comparingLong(File::lastModified));
//            for (int i = 0; i < files.length - MAX_AUDIO_FILES; i++) {
//                files[i].delete();
//            }
//        }
//    }
//
//    private void startRecording(String fileName) {
//        int bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT) * 4;
//        audioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC, SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT, bufferSize);
//        if (audioRecord.getState() != AudioRecord.STATE_INITIALIZED) {
//            Log.e(TAG, "AudioRecord initialization failed");
//            return;
//        }
//        audioRecord.startRecording();
//        isRecording = true;
//
//        File audioDir = new File(getFilesDir(), "audio");
//        File outputFile = new File(audioDir, fileName);
//        recordingThread = new Thread(() -> recordAudio(outputFile));
//        recordingThread.start();
//        Log.d(TAG, "Recording started for " + fileName);
//    }
//
//    private void stopRecording(String fileName) {
//        if (audioRecord != null) {
//            isRecording = false;
//            audioRecord.stop();
//            audioRecord.release();
//            audioRecord = null;
//            try {
//                recordingThread.join(1000);
//            } catch (InterruptedException e) {
//                Log.e(TAG, "Recording thread interrupted: " + e.getMessage());
//            }
//            Log.d(TAG, "Recording stopped for " + (fileName != null ? fileName : "unknown"));
//        }
//    }
//
//    private void recordAudio(File outputFile) {
//        try (FileOutputStream fos = new FileOutputStream(outputFile)) {
//            byte[] header = new byte[44];
//            fos.write(header);
//
//            int bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT) * 4;
//            short[] audioData = new short[bufferSize];
//            long totalDataLen = 0;
//            long startTime = System.currentTimeMillis();
//
//            while (isRecording && (System.currentTimeMillis() - startTime) < 3000) {
//                int read = audioRecord.read(audioData, 0, bufferSize);
//                if (read > 0) {
//                    ByteBuffer buffer = ByteBuffer.allocate(read * 2);
//                    buffer.order(ByteOrder.LITTLE_ENDIAN);
//                    for (int i = 0; i < read; i++) {
//                        buffer.putShort(audioData[i]);
//                    }
//                    fos.write(buffer.array());
//                    totalDataLen += read * 2;
//                }
//            }
//
//            writeWavHeader(outputFile, totalDataLen);
//            Log.d(TAG, "Recording completed for " + outputFile.getName());
//        } catch (Exception e) {
//            Log.e(TAG, "Recording failed: " + e.getMessage());
//        }
//    }
//
//    private void writeWavHeader(File file, long totalAudioLen) throws Exception {
//        long totalDataLen = totalAudioLen + 36;
//        long byteRate = SAMPLE_RATE * 2;
//        try (RandomAccessFile raf = new RandomAccessFile(file, "rw")) {
//            raf.seek(0);
//            raf.writeBytes("RIFF");
//            raf.writeInt(Integer.reverseBytes((int) totalDataLen));
//            raf.writeBytes("WAVE");
//            raf.writeBytes("fmt ");
//            raf.writeInt(Integer.reverseBytes(16));
//            raf.writeShort(Short.reverseBytes((short) 1));
//            raf.writeShort(Short.reverseBytes((short) 1));
//            raf.writeInt(Integer.reverseBytes(SAMPLE_RATE));
//            raf.writeInt(Integer.reverseBytes((int) byteRate));
//            raf.writeShort(Short.reverseBytes((short) 2));
//            raf.writeShort(Short.reverseBytes((short) 16));
//            raf.writeBytes("data");
//            raf.writeInt(Integer.reverseBytes((int) totalAudioLen));
//        }
//    }
//
//    private void startContinuousRecording() {
//        isContinuousRecording = true;
//        startRecordingThread();
//        startAnalysisThread();
//    }
//
//
//
//    private void stopContinuousRecording() {
//        isContinuousRecording = false;
//        isRecordingThreadRunning = false;
//        isAnalysisThreadRunning = false;
//
//        stopRecording(null);
//        try {
//            if (recordingThread != null) recordingThread.join(1000);
//            if (analysisThread != null) analysisThread.join(1000);
//        } catch (InterruptedException e) {
//            Log.e(TAG, "Thread join interrupted: " + e.getMessage());
//        }
//        synchronized (queueLock) {
//            audioFileQueue.clear();
//        }
//        Log.d(TAG, "Continuous recording stopped");
//    }
//
//    private void startRecordingThread() {
//        isRecordingThreadRunning = true;
//        recordingThread = new Thread(() -> {
//            Log.d(TAG, "Recording thread started");
//            while (isRecordingThreadRunning && isContinuousRecording) {
//                try {
//                    String audioFileName = generateAudioFileName();
//                    startRecording(audioFileName);
//                    Thread.sleep(3000);
//                    stopRecording(audioFileName);
//
//                    synchronized (queueLock) {
//                        audioFileQueue.offer(audioFileName);
//                    }
//                    manageAudioFiles();
//                } catch (InterruptedException e) {
//                    Log.e(TAG, "Recording thread interrupted: " + e.getMessage());
//                }
//            }
//            Log.d(TAG, "Recording thread terminated");
//        });
//        recordingThread.start();
//    }
//
//    private void startAnalysisThread() {
//        isAnalysisThreadRunning = true;
//        analysisThread = new Thread(() -> {
//            Log.d(TAG, "Analysis thread started");
//            while (isAnalysisThreadRunning && isContinuousRecording) {
//                String audioFile;
//                synchronized (queueLock) {
//                    audioFile = audioFileQueue.poll();
//                }
//                if (audioFile != null) {
//                    analyzeAudioFile(audioFile);
//                } else {
//                    try {
//                        Thread.sleep(100);
//                    } catch (InterruptedException e) {
//                        Log.e(TAG, "Analysis thread interrupted: " + e.getMessage());
//                    }
//                }
//            }
//            Log.d(TAG, "Analysis thread terminated");
//        });
//        analysisThread.start();
//    }
//
//    private void analyzeAudioFile(String audioFile) {
//        File execFile = new File(getFilesDir(), "exec/rknn_whisper_demo_base_10s_static");
//        String execPath = execFile.getAbsolutePath();
//        File modelDir = new File(getFilesDir(), "model");
//        String encoderPath = new File(modelDir, SELECTED_ENCODER).getAbsolutePath();
//        String decoderPath = new File(modelDir, SELECTED_DECODER).getAbsolutePath();
//        String lang = SELECTED_VOCAB.replace("vocab_", "").replace(".txt", "");
//        String audioPath = new File(new File(getFilesDir(), "audio"), audioFile).getAbsolutePath();
//        String libPath = new File(getFilesDir(), "lib").getAbsolutePath();
//
//        if (!execFile.exists()) {
//            Log.e(TAG, "Executable not found: " + execPath);
//            return;
//        }
//
//        long startTime = System.currentTimeMillis();
//        try {
//            String result = executeWhisperDemo(execPath, encoderPath, decoderPath, lang, audioPath, libPath);
//            long duration = (System.currentTimeMillis() - startTime) / 1000;
//            Log.d(TAG, audioFile.replace(".wav", "") + " - " + (result.isEmpty() ? "No output" : result) + " [" + duration + "s]");
//
//            // 키워드 검사
//            if (containsEmergencyKeyword(result)) {
//                Log.d(TAG, "Emergency keyword detected:" + result);
//                stopContinuousRecording(); // 녹음 및 분석 중지
//                sendBroadcast(new Intent(H500.Intent.ACTION_PJSIP_MAKE_CALL), H500.Permission.H500_BROADCAST_PERMISSION);
//                Log.w(TAG,"관제센터로 연결합니다.");
//            }
//        } catch (Exception e) {
//            Log.e(TAG, "Analysis failed for " + audioFile + ": " + e.getMessage());
//        }
//    }
//
//    private boolean containsEmergencyKeyword(String result) {
//        String[] keywords = {"살려", "살려줘요", "살려주", "살려줘", "살려주세요", "사람살려", "도와", "도와주","도와줘", "도와줘요", "도와주세요"};
//        for (String keyword : keywords) {
//            if (result.trim().contains(keyword)) {
//                return true;
//            }
//        }
//        return false;
//    }
//
//    public native String executePermission(String path);
//    public native String executeWhisperDemo(String execPath, String encoderPath, String decoderPath,
//                                            String lang, String audioPath, String libPath);
//
//    @Override
//    public void onDestroy() {
//        super.onDestroy();
//        stopContinuousRecording();
//        if (receiver != null) {
//            unregisterReceiver(receiver);
//            receiver = null;
//        }
//        Log.d(TAG, "Service destroyed");
//    }
//
//    private class MyBroadcastReceiver extends BroadcastReceiver {
//        @Override
//        public void onReceive(Context context, Intent intent) {
//            if (intent.getAction().equals(H500.Intent.ACTION_PJSIP_END_CALL)) {
//                Log.i(TAG, "ACTION_PJSIP_END_CALL received");
//                if (!isContinuousRecording) {
//                    startContinuousRecording(); // 녹음 및 분석 재시작
//                }
//            }
//        }
//    }
//}