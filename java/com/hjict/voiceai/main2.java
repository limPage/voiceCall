//package com.example.speechtotextapp;
//
//import android.Manifest;
//import android.content.pm.PackageManager;
//import android.media.AudioFormat;
//import android.media.AudioRecord;
//import android.media.MediaRecorder;
//import android.os.Bundle;
//import android.os.Handler;
//import android.os.Looper;
//import android.text.method.ScrollingMovementMethod;
//import android.util.Log;
//import android.view.View;
//import android.widget.AdapterView;
//import android.widget.ArrayAdapter;
//import android.widget.Button;
//import android.widget.Spinner;
//import android.widget.TextView;
//import androidx.appcompat.app.AppCompatActivity;
//import androidx.core.app.ActivityCompat;
//import androidx.core.content.ContextCompat;
//
//import java.io.File;
//import java.io.FileOutputStream;
//import java.io.InputStream;
//import java.io.RandomAccessFile;
//import java.nio.ByteBuffer;
//import java.nio.ByteOrder;
//import java.text.SimpleDateFormat;
//import java.util.ArrayList;
//import java.util.Arrays;
//import java.util.Comparator;
//import java.util.Date;
//import java.util.LinkedList;
//import java.util.List;
//import java.util.Queue;
//import java.util.concurrent.ExecutorService;
//import java.util.concurrent.Executors;
//
//public class MainActivity extends AppCompatActivity {
//    static {
//        System.loadLibrary("native-lib");
//    }
//
//    private TextView resultText;
//    private Button continuousRecordButton;
//    private static final int REQUEST_CODE_PERMISSIONS = 2;
//    private AudioRecord audioRecord;
//    private boolean isRecording = false;
//    private static final int SAMPLE_RATE = 16000;
//    private static final int CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO;
//    private static final int AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT;
//    private Thread recordingThread;
//    private Handler handler = new Handler(Looper.getMainLooper());
//    private ExecutorService analysisExecutor = Executors.newSingleThreadExecutor();
//
//    private boolean isContinuousRecording = false;
//    private Queue<String> audioFileQueue = new LinkedList<>();
//    private static final int MAX_AUDIO_FILES = 100;
//    private static final int MAX_TEXT_LINES = 50;
//    private String currentRecordingFile = null;
//    private boolean isFirstRecording = true; // 첫 녹음 여부 추적
//
//    private Spinner encoderSpinner, decoderSpinner, vocabSpinner;
//    private String selectedEncoder = "whisper_encoder_base.rknn";
//    private String selectedDecoder = "whisper_decoder_base.rknn";
//    private String selectedVocab = "vocab_ko.txt";
//
//    @Override
//    protected void onCreate(Bundle savedInstanceState) {
//        super.onCreate(savedInstanceState);
//        setContentView(R.layout.activity_main);
//
//        resultText = findViewById(R.id.result_text);
//        resultText.setMovementMethod(new ScrollingMovementMethod());
//        resultText.setMaxLines(MAX_TEXT_LINES);
//        resultText.setText("Results will appear below:\n\n");
//
//        continuousRecordButton = findViewById(R.id.continuous_record_button);
//        continuousRecordButton.setOnClickListener(v -> toggleContinuousRecording());
//
//        encoderSpinner = findViewById(R.id.encoder_spinner);
//        decoderSpinner = findViewById(R.id.decoder_spinner);
//        vocabSpinner = findViewById(R.id.vocab_spinner);
//
//        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
//            ActivityCompat.requestPermissions(this,
//                    new String[]{Manifest.permission.RECORD_AUDIO},
//                    REQUEST_CODE_PERMISSIONS);
//        }
//
//        setupFiles();
//        setupSpinners();
//    }
//
//    private void setupFiles() {
//        // 기존 setupFiles() 그대로 유지
//        File execDir = new File(getFilesDir(), "exec");
//        if (!execDir.exists()) execDir.mkdirs();
//
//        File modelDir = new File(getFilesDir(), "model");
//        if (!modelDir.exists()) modelDir.mkdirs();
//
//        File libDir = new File(getFilesDir(), "lib");
//        if (!libDir.exists()) libDir.mkdirs();
//
//        File audioDir = new File(getFilesDir(), "audio");
//        if (!audioDir.exists()) audioDir.mkdirs();
//
//        try {
//            File execFile = new File(execDir, "rknn_whisper_demo");
//            if (!execFile.exists()) {
//                try (InputStream is = getAssets().open("rknn_whisper_demo");
//                     FileOutputStream fos = new FileOutputStream(execFile)) {
//                    byte[] buffer = new byte[1024];
//                    int len;
//                    while ((len = is.read(buffer)) != -1) {
//                        fos.write(buffer, 0, len);
//                    }
//                }
//                Process process = Runtime.getRuntime().exec("chmod +x " + execFile.getAbsolutePath());
//                process.waitFor();
//                if (process.exitValue() != 0) {
//                    Log.e("MainActivity", "Failed to set execute permission for rknn_whisper_demo");
//                    return;
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
//                            Log.d("MainActivity", "Copied model file: " + modelFile);
//                        }
//                    }
//                }
//            } else {
//                Log.e("MainActivity", "No files found in assets/model/");
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
//        } catch (Exception e) {
//            Log.e("MainActivity", "Setup failed: " + e.getMessage());
//        }
//    }
//
//    private void setupSpinners() {
//        // 기존 setupSpinners() 그대로 유지
//        List<String> encoderFiles = new ArrayList<>();
//        try {
//            String[] assets = getAssets().list("model");
//            if (assets != null) {
//                for (String asset : assets) {
//                    if (asset.startsWith("whisper_encoder_") && asset.endsWith(".rknn")) {
//                        encoderFiles.add(asset);
//                    }
//                }
//            }
//        } catch (Exception e) {
//            Log.e("MainActivity", "Failed to load encoder files: " + e.getMessage());
//        }
//        ArrayAdapter<String> encoderAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, encoderFiles);
//        encoderAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
//        encoderSpinner.setAdapter(encoderAdapter);
//        encoderSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
//            @Override
//            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
//                selectedEncoder = encoderFiles.get(position);
//            }
//
//            @Override
//            public void onNothingSelected(AdapterView<?> parent) {}
//        });
//        if (!encoderFiles.isEmpty()) encoderSpinner.setSelection(0);
//
//        List<String> decoderFiles = new ArrayList<>();
//        try {
//            String[] assets = getAssets().list("model");
//            if (assets != null) {
//                for (String asset : assets) {
//                    if (asset.startsWith("whisper_decoder_") && asset.endsWith(".rknn")) {
//                        decoderFiles.add(asset);
//                    }
//                }
//            }
//        } catch (Exception e) {
//            Log.e("MainActivity", "Failed to load decoder files: " + e.getMessage());
//        }
//        ArrayAdapter<String> decoderAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, decoderFiles);
//        decoderAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
//        decoderSpinner.setAdapter(decoderAdapter);
//        decoderSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
//            @Override
//            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
//                selectedDecoder = decoderFiles.get(position);
//            }
//
//            @Override
//            public void onNothingSelected(AdapterView<?> parent) {}
//        });
//        if (!decoderFiles.isEmpty()) decoderSpinner.setSelection(0);
//
//        List<String> vocabFiles = new ArrayList<>();
//        try {
//            String[] assets = getAssets().list("model");
//            if (assets != null) {
//                for (String asset : assets) {
//                    if (asset.startsWith("vocab_") && asset.endsWith(".txt")) {
//                        vocabFiles.add(asset);
//                    }
//                }
//            }
//        } catch (Exception e) {
//            Log.e("MainActivity", "Failed to load vocab files: " + e.getMessage());
//        }
//        ArrayAdapter<String> vocabAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, vocabFiles);
//        vocabAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
//        vocabSpinner.setAdapter(vocabAdapter);
//        vocabSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
//            @Override
//            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
//                selectedVocab = vocabFiles.get(position);
//            }
//
//            @Override
//            public void onNothingSelected(AdapterView<?> parent) {}
//        });
//        if (!vocabFiles.isEmpty()) vocabSpinner.setSelection(0);
//    }
//
//    private void appendResult(String fileName, String result, float duration) {
//        // 기존 appendResult() 그대로 유지
//        String formattedResult = String.format("◉ %s\n=> %s (소요 시간: %.2f초)\n\n", fileName, result, duration);
//        Log.d("MainActivity", "Appending result: " + formattedResult.trim());
//
//        handler.post(() -> {
//            String currentText = resultText.getText().toString();
//            String[] lines = currentText.split("\n");
//
//            if (lines.length >= MAX_TEXT_LINES) {
//                StringBuilder trimmedText = new StringBuilder();
//                int startIndex = Math.max(0, lines.length - (MAX_TEXT_LINES - 5));
//                for (int i = startIndex; i < lines.length; i++) {
//                    trimmedText.append(lines[i]).append("\n");
//                }
//                if (!lines[lines.length - 1].isEmpty()) {
//                    trimmedText.append("\n");
//                }
//                currentText = trimmedText.toString();
//                resultText.setText(currentText);
//            }
//
//            resultText.append(formattedResult);
//
//            if (resultText.getLayout() != null) {
//                int scrollAmount = resultText.getLayout().getLineTop(resultText.getLineCount()) - resultText.getHeight();
//                if (scrollAmount > 0) {
//                    resultText.scrollTo(0, scrollAmount);
//                } else {
//                    resultText.scrollTo(0, 0);
//                }
//            }
//            resultText.invalidate();
//        });
//    }
//
//    private String generateAudioFileName() {
//        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd_HHmmss");
//        String timestamp = sdf.format(new Date());
//        return "voice_" + timestamp + ".wav";
//    }
//
//    private void manageAudioFiles() {
//        // 기존 manageAudioFiles() 그대로 유지
//        File audioDir = new File(getFilesDir(), "audio");
//        File[] files = audioDir.listFiles((d, name) -> name.startsWith("voice_") && name.endsWith(".wav"));
//        if (files != null && files.length > MAX_AUDIO_FILES) {
//            Arrays.sort(files, Comparator.comparingLong(File::lastModified));
//            for (int i = 0; i < files.length - MAX_AUDIO_FILES; i++) {
//                files[i].delete();
//            }
//        }
//    }
//
//    private void toggleContinuousRecording() {
//        if (!isContinuousRecording) {
//            if (isRecording) {
//                return;
//            }
//            isContinuousRecording = true;
//            isFirstRecording = true; // 첫 녹음 플래그 설정
//            Log.d("MainActivity", "Continuous recording started at " + System.currentTimeMillis());
//            handler.post(() -> {
//                continuousRecordButton.setText("실시간 음성 분석 중...");
//                continuousRecordButton.setBackgroundResource(R.drawable.button_background_active);
//                startRecordingAndAnalysis(); // 초기 녹음 및 분석 시작
//            });
//        } else {
//            isContinuousRecording = false;
//            handler.removeCallbacksAndMessages(null);
//            stopRecording(null);
//            Log.d("MainActivity", "Continuous recording stopped at " + System.currentTimeMillis());
//            handler.post(() -> {
//                continuousRecordButton.setText("실시간 음성 분석");
//                continuousRecordButton.setBackgroundResource(R.drawable.button_background);
//            });
//        }
//    }
//
//    private void startRecordingAndAnalysis() {
//        if (!isContinuousRecording) return;
//
//        // 이전 녹음 파일을 큐에 추가 (첫 녹음 제외)
//        if (currentRecordingFile != null && !isFirstRecording) {
//            audioFileQueue.offer(currentRecordingFile);
//            manageAudioFiles();
//            analyzeNextAudio(); // 분석 시작
//        }
//
//        // 새 녹음 시작
//        String audioFileName = generateAudioFileName();
//        currentRecordingFile = audioFileName;
//        Log.d("MainActivity", "Starting recording: " + audioFileName);
//        startRecording(audioFileName);
//
//        // 첫 녹음 시 3초 보장
//        if (isFirstRecording) {
//            handler.postDelayed(() -> {
//                if (isContinuousRecording && isRecording) {
//                    stopRecording(currentRecordingFile);
//                    isFirstRecording = false; // 첫 녹음 완료
//                    startRecordingAndAnalysis(); // 다음 녹음 및 분석
//                }
//            }, 3000); // 3초 후 종료
//        }
//    }
//
//    private void startRecording(String fileName) {
//        // 기존 startRecording() 그대로 유지
//        int bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT) * 4;
//        audioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC, SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT, bufferSize);
//        if (audioRecord.getState() != AudioRecord.STATE_INITIALIZED) {
//            Log.e("MainActivity", "AudioRecord initialization failed at " + System.currentTimeMillis());
//            return;
//        }
//        audioRecord.startRecording();
//        isRecording = true;
//
//        File audioDir = new File(getFilesDir(), "audio");
//        File outputFile = new File(audioDir, fileName);
//        recordingThread = new Thread(() -> recordAudio(outputFile));
//        recordingThread.start();
//        Log.d("MainActivity", "Recording started for " + fileName + " at " + System.currentTimeMillis() + " with buffer size: " + bufferSize);
//    }
//
//    private void stopRecording(String fileName) {
//        // 기존 stopRecording() 그대로 유지
//        if (audioRecord != null) {
//            isRecording = false;
//            audioRecord.stop();
//            audioRecord.release();
//            audioRecord = null;
//            try {
//                recordingThread.join(1000);
//            } catch (InterruptedException e) {
//                Log.e("MainActivity", "Recording thread interrupted: " + e.getMessage());
//            }
//            Log.d("MainActivity", "Recording stopped for " + (fileName != null ? fileName : "unknown") + " at " + System.currentTimeMillis());
//        }
//    }
//
//    private void recordAudio(File outputFile) {
//        // 기존 recordAudio()에서 첫 녹음 3초 제한 제거
//        try (FileOutputStream fos = new FileOutputStream(outputFile)) {
//            byte[] header = new byte[44];
//            fos.write(header);
//
//            int bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT) * 4;
//            short[] audioData = new short[bufferSize];
//            long totalDataLen = 0;
//            long startTime = System.currentTimeMillis();
//            int totalSamples = 0;
//
//            while (isRecording) { // 분석 완료 시 isRecording=false로 종료
//                int read = audioRecord.read(audioData, 0, bufferSize);
//                if (read > 0) {
//                    ByteBuffer buffer = ByteBuffer.allocate(read * 2);
//                    buffer.order(ByteOrder.LITTLE_ENDIAN);
//                    for (int i = 0; i < read; i++) {
//                        buffer.putShort(audioData[i]);
//                    }
//                    fos.write(buffer.array());
//                    totalDataLen += read * 2;
//                    totalSamples += read;
//                    Log.d("MainActivity", "Wrote " + read + " samples to " + outputFile.getName() + ", total samples: " + totalSamples + " at " + (System.currentTimeMillis() - startTime) + "ms");
//                } else if (read < 0) {
//                    Log.w("MainActivity", "Audio read error for " + outputFile.getName() + " at " + (System.currentTimeMillis() - startTime) + "ms, read: " + read);
//                }
//            }
//            Log.d("MainActivity", "Recording loop exited after " + (System.currentTimeMillis() - startTime) + "ms with total samples: " + totalSamples + " for " + outputFile.getName());
//
//            writeWavHeader(outputFile, totalDataLen);
//            Log.d("MainActivity", "File writing completed for " + outputFile.getName() + " with total length: " + totalDataLen + " bytes");
//        } catch (Exception e) {
//            Log.e("MainActivity", "Recording failed: " + e.getMessage() + " at " + System.currentTimeMillis());
//            appendResult(outputFile.getName(), "Recording failed: " + e.getMessage(), 0.0f);
//        }
//    }
//
//    private void writeWavHeader(File file, long totalAudioLen) throws Exception {
//        // 기존 writeWavHeader() 그대로 유지
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
//            Log.d("MainActivity", "WAV header written for " + file.getName() + " with totalDataLen: " + totalDataLen + ", byteRate: " + byteRate + ", sampleRate: " + SAMPLE_RATE);
//        }
//    }
//
//    private void analyzeNextAudio() {
//        if (audioFileQueue.isEmpty()) {
//            return;
//        }
//
//        String audioFile = audioFileQueue.poll();
//        if (audioFile == null) {
//            return;
//        }
//
//        Log.d("MainActivity", "Submitting analysis for: " + audioFile + " at " + System.currentTimeMillis());
//        analysisExecutor.submit(() -> {
//            File execFile = new File(getFilesDir(), "exec/rknn_whisper_demo");
//            String execPath = execFile.getAbsolutePath();
//            File modelDir = new File(getFilesDir(), "model");
//            String encoderPath = new File(modelDir, selectedEncoder).getAbsolutePath();
//            String decoderPath = new File(modelDir, selectedDecoder).getAbsolutePath();
//            String lang = selectedVocab.replace("vocab_", "").replace(".txt", "");
//            String audioPath = new File(new File(getFilesDir(), "audio"), audioFile).getAbsolutePath();
//            String libPath = new File(getFilesDir(), "lib").getAbsolutePath();
//
//            if (!execFile.exists()) {
//                Log.e("MainActivity", "Executable not found: " + execPath);
//                appendResult(audioFile, "Executable not found: " + execPath, 0.0f);
//                return;
//            }
//
//            long startTime = System.currentTimeMillis();
//            try {
//                String result = executeWhisperDemo(execPath, encoderPath, decoderPath, lang, audioPath, libPath);
//                long endTime = System.currentTimeMillis();
//                float duration = (endTime - startTime) / 1000.0f;
//                Log.d("MainActivity", "Analysis completed for " + audioFile + ": " + result + " (duration: " + duration + "s)");
//                appendResult(audioFile, result.isEmpty() ? "No Whisper output" : result, duration);
//            } catch (Exception e) {
//                Log.e("MainActivity", "Analysis failed for " + audioFile + ": " + e.getMessage());
//                appendResult(audioFile, "Analysis failed: " + e.getMessage(), 0.0f);
//            }
//
//            // 분석 완료 후 현재 녹음 종료 및 재시작
//            handler.post(() -> {
//                if (isContinuousRecording && isRecording) {
//                    stopRecording(currentRecordingFile);
//                    startRecordingAndAnalysis(); // 분석 끝난 후 즉시 재녹음 및 분석
//                }
//            });
//        });
//    }
//
//    public native String executeWhisperDemo(String execPath, String encoderPath, String decoderPath,
//                                            String lang, String audioPath, String libPath);
//
//    @Override
//    protected void onDestroy() {
//        super.onDestroy();
//        isContinuousRecording = false;
//        handler.removeCallbacksAndMessages(null);
//        if (audioRecord != null) {
//            stopRecording(null);
//        }
//        analysisExecutor.shutdown();
//    }
//}