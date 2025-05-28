package com.hjict.voiceai;

import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;

import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {
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
//
//    private boolean isContinuousRecording = false;
//    private boolean isAnalyzing = false;
//    private Runnable continuousRecordingRunnable;
//    private Queue<String> audioFileQueue = new LinkedList<>();
//    private static final int MAX_AUDIO_FILES = 100;
//    private static final int MAX_TEXT_LINES = 50;
//    private int resultCounter = 1;
//
//    private Spinner encoderSpinner, decoderSpinner, vocabSpinner;
//    private String selectedEncoder = "whisper_encoder_base_10s.rknn";
//    private String selectedDecoder = "whisper_decoder_base_10s.rknn";
//    private String selectedVocab = "vocab_ko.txt";
//
//    private Thread analysisThread;
//    private boolean isRecordingThreadRunning = false;
//    private boolean isAnalysisThreadRunning = false;
//    private final Object queueLock = new Object(); // 큐 접근 동기화를 위한 락

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

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
//        setupFiles();
//        setupSpinners();
        Log.d("Sdf@", "onCreat@@@@@@@@@@@@@@@@@e: "+getFilesDir().getAbsolutePath());
        int isVoiceAIon = Settings.System.getInt(getContentResolver(), H500.Settings.VOICE_AI_STATE, 1);
        if (isVoiceAIon == 1) {
            Intent serviceIntent = new Intent(this, VoiceAIService.class); // 나중에 지울것
            startService(serviceIntent);
        }
//        executePermission("adfddd");
//        executePermission(getFilesDir().getAbsolutePath());
    }
//
//    private void setupFiles() {
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
//            File execFile = new File(execDir, "rknn_whisper_demo_base_10s");
//            if (!execFile.exists()) {
//                try (InputStream is = getAssets().open("rknn_whisper_demo_base_10s");
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
//        // "whisper_encoder_base_10s.rknn"을 기본 선택으로 설정
//        int encoderIndex = encoderFiles.indexOf("whisper_encoder_base_10s.rknn");
//        if (encoderIndex != -1) {
//            encoderSpinner.setSelection(encoderIndex);
//        } else if (!encoderFiles.isEmpty()) {
//            encoderSpinner.setSelection(0); // 기본값으로 첫 번째 항목 선택
//        }
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
//        // "whisper_decoder_base_10s.rknn"을 기본 선택으로 설정
//        int decoderIndex = decoderFiles.indexOf("whisper_decoder_base_10s.rknn");
//        if (decoderIndex != -1) {
//            decoderSpinner.setSelection(decoderIndex);
//        } else if (!decoderFiles.isEmpty()) {
//            decoderSpinner.setSelection(0); // 기본값으로 첫 번째 항목 선택
//        }
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
//        // "vocab_ko.txt"를 기본 선택으로 설정
//        int vocabIndex = vocabFiles.indexOf("vocab_ko.txt");
//        if (vocabIndex != -1) {
//            vocabSpinner.setSelection(vocabIndex);
//        } else if (!vocabFiles.isEmpty()) {
//            vocabSpinner.setSelection(0); // 기본값으로 첫 번째 항목 선택
//        }
//    }
//
//    private void appendResult(String fileName, String result, float duration) {
//        String formattedResult = String.format("◉ %s - %s [%.2f초]\n", fileName.replace("2025","").replace(".wav",""), result, duration);
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
////                if (!lines[lines.length - 1].isEmpty()) {
////                    trimmedText.append("\n");
////                }
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
//        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd_HH:mm:ss");
//        String timestamp = sdf.format(new Date());
////        return "voice_" + timestamp + ".wav";
//        return timestamp + ".wav";
//    }
//
//    private void manageAudioFiles() {
//        File audioDir = new File(getFilesDir(), "audio");
////        File[] files = audioDir.listFiles((d, name) -> name.startsWith("voice_") && name.endsWith(".wav"));
//        File[] files = audioDir.listFiles((d, name) -> name.endsWith(".wav"));
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
//            // 시작
//            isContinuousRecording = true;
//            Log.d("MainActivity", "Continuous recording started at " + System.currentTimeMillis());
//
//            handler.post(() -> {
//                continuousRecordButton.setText("실시간 음성 분석 중...");
//                continuousRecordButton.setBackgroundResource(R.drawable.button_background_active);
//            });
//
//            // 녹음 스레드 시작
//            startRecordingThread();
//
//            // 분석 스레드 시작
//            startAnalysisThread();
//        } else {
//            // 종료
//            isContinuousRecording = false;
//            Log.d("MainActivity", "Continuous recording stopped at " + System.currentTimeMillis());
//
//            handler.post(() -> {
//                continuousRecordButton.setText("실시간 음성 분석");
//                continuousRecordButton.setBackgroundResource(R.drawable.button_background);
//            });
//
//            // 현재 진행 중인 녹음 중지
//            if (audioRecord != null && isRecording) {
//                stopRecording(null);
//            }
//
//            // 스레드 중지 플래그 설정
//            isRecordingThreadRunning = false;
//            isAnalysisThreadRunning = false;
//        }
//    }
//
//    private void startRecording(String fileName) {
//        int bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT) * 4; // 버퍼 크기 증가
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
//    // 매개변수 없는 오버로드 메서드 추가
//    private void stopRecording() {
//        stopRecording(null);
//    }
//
//    private void stopRecording(String fileName) {
//        if (audioRecord != null) {
//            isRecording = false;
//            audioRecord.stop();
//            audioRecord.release();
//            audioRecord = null;
//            try {
//                recordingThread.join(1000); // 1초 대기
//            } catch (InterruptedException e) {
//                Log.e("MainActivity", "Recording thread interrupted: " + e.getMessage());
//            }
//            Log.d("MainActivity", "Recording stopped for " + (fileName != null ? fileName : "unknown") + " at " + System.currentTimeMillis());
//        }
//    }
//
//    private void recordAudio(File outputFile) {
//        try (FileOutputStream fos = new FileOutputStream(outputFile)) {
//            byte[] header = new byte[44]; // WAV 헤더 초기화
//            fos.write(header);
//
//            int bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT) * 4;
//            short[] audioData = new short[bufferSize];
//            long totalDataLen = 0;
//            long startTime = System.currentTimeMillis();
//            int totalSamples = 0;
//
//            while (isRecording && (System.currentTimeMillis() - startTime) < 5000) { // 3초 동안 녹음
//                int read = audioRecord.read(audioData, 0, bufferSize);
//                if (read > 0) {
//                    ByteBuffer buffer = ByteBuffer.allocate(read * 2);
//                    buffer.order(ByteOrder.LITTLE_ENDIAN);
//                    for (int i = 0; i < read; i++) {
//                        buffer.putShort(audioData[i]);
//                    }
//                    fos.write(buffer.array());
//                    totalDataLen += read * 2; // 바이트 단위로 누적
//                    totalSamples += read;
////                    Log.d("MainActivity", "Wrote " + read + " samples to " + outputFile.getName() + ", total samples: " + totalSamples + " at " + (System.currentTimeMillis() - startTime) + "ms");
//                } else if (read < 0) {
//                    Log.w("MainActivity", "Audio read error for " + outputFile.getName() + " at " + (System.currentTimeMillis() - startTime) + "ms, read: " + read);
//                }
//            }
//            Log.d("MainActivity", "Recording loop exited after " + (System.currentTimeMillis() - startTime) + "ms with total samples: " + totalSamples + " for " + outputFile.getName());
//
//            // WAV 헤더 작성
//            writeWavHeader(outputFile, totalDataLen);
//            Log.d("MainActivity", "File writing completed for " + outputFile.getName() + " with total length: " + totalDataLen + " bytes");
//        } catch (Exception e) {
//            Log.e("MainActivity", "Recording failed: " + e.getMessage() + " at " + System.currentTimeMillis());
//            appendResult(outputFile.getName(), "Recording failed: " + e.getMessage(), 0.0f);
//        }
//    }
//
//    private void writeWavHeader(File file, long totalAudioLen) throws Exception {
//        long totalDataLen = totalAudioLen + 36; // 전체 데이터 길이 (헤더 포함)
//        long byteRate = SAMPLE_RATE * 2; // 샘플 레이트 * 바이트/샘플 (16비트 = 2바이트)
//        try (RandomAccessFile raf = new RandomAccessFile(file, "rw")) {
//            raf.seek(0);
//            // RIFF chunk
//            raf.writeBytes("RIFF");
//            raf.writeInt(Integer.reverseBytes((int) totalDataLen));
//            raf.writeBytes("WAVE");
//            // fmt subchunk
//            raf.writeBytes("fmt ");
//            raf.writeInt(Integer.reverseBytes(16)); // fmt chunk size (16 for PCM)
//            raf.writeShort(Short.reverseBytes((short) 1)); // Audio format (1 = PCM)
//            raf.writeShort(Short.reverseBytes((short) 1)); // Number of channels (1 = mono)
//            raf.writeInt(Integer.reverseBytes(SAMPLE_RATE)); // Sample rate
//            raf.writeInt(Integer.reverseBytes((int) byteRate)); // Byte rate
//            raf.writeShort(Short.reverseBytes((short) 2)); // Block align (channels * bits/sample / 8)
//            raf.writeShort(Short.reverseBytes((short) 16)); // Bits per sample
//            // data subchunk
//            raf.writeBytes("data");
//            raf.writeInt(Integer.reverseBytes((int) totalAudioLen)); // Data size
//            Log.d("MainActivity", "WAV header written for " + file.getName() + " with totalDataLen: " + totalDataLen + ", byteRate: " + byteRate + ", sampleRate: " + SAMPLE_RATE);
//        }
//    }
//
//    private void startRecordingThread() {
//        isRecordingThreadRunning = true;
//        recordingThread = new Thread(() -> {
//            Log.d("MainActivity", "Recording thread started");
//            while (isRecordingThreadRunning && isContinuousRecording) {
//                try {
//                    String audioFileName = generateAudioFileName();
//                    Log.d("MainActivity", "Starting recording: " + audioFileName);
//                    startRecording(audioFileName);
//
//                    // 5초 동안 녹음
//                    Thread.sleep(5000);
//
//                    stopRecording(audioFileName);
//                    Log.d("MainActivity", "Recording stopped, adding to queue: " + audioFileName);
//
//                    // 큐에 안전하게 파일 추가
//                    synchronized (queueLock) {
//                        audioFileQueue.offer(audioFileName);
//                    }
//
//                    // 파일 관리
//                    manageAudioFiles();
//
//                } catch (InterruptedException e) {
//                    Log.e("MainActivity", "Recording thread interrupted: " + e.getMessage());
//                }
//            }
//            Log.d("MainActivity", "Recording thread terminated");
//        });
//        recordingThread.start();
//    }
//
//    private void startAnalysisThread() {
//        isAnalysisThreadRunning = true;
//        analysisThread = new Thread(() -> {
//            Log.d("MainActivity", "Analysis thread started");
//            while (isAnalysisThreadRunning && isContinuousRecording) {
//                boolean hasFile = false;
//                String audioFile = null;
//
//                // 큐에서 안전하게 파일 가져오기
//                synchronized (queueLock) {
//                    if (!audioFileQueue.isEmpty()) {
//                        audioFile = audioFileQueue.poll();
//                        hasFile = (audioFile != null);
//                    }
//                }
//
//                if (hasFile) {
//                    // 분석 코드
//                    analyzeAudioFile(audioFile);
//                } else {
//                    // 파일이 없으면 1초 대기 후 다시 확인
//                    try {
//                        Thread.sleep(100);
//                    } catch (InterruptedException e) {
//                        Log.e("MainActivity", "Analysis thread interrupted during wait: " + e.getMessage());
//                    }
//                }
//            }
//            Log.d("MainActivity", "Analysis thread terminated");
//        });
//        analysisThread.start();
//    }
//
//    private void analyzeAudioFile(String audioFile) {
//        if (audioFile == null) return;
//
//        Log.d("MainActivity", "Analyzing audio: " + audioFile + " at " + System.currentTimeMillis());
//
//        File execFile = new File(getFilesDir(), "exec/rknn_whisper_demo_base_10s");
//        String execPath = execFile.getAbsolutePath();
//        File modelDir = new File(getFilesDir(), "model");
//        String encoderPath = new File(modelDir, selectedEncoder).getAbsolutePath();
//        String decoderPath = new File(modelDir, selectedDecoder).getAbsolutePath();
//        String lang = selectedVocab.replace("vocab_", "").replace(".txt", "");
//        String audioPath = new File(new File(getFilesDir(), "audio"), audioFile).getAbsolutePath();
//        String libPath = new File(getFilesDir(), "lib").getAbsolutePath();
//
//        if (!execFile.exists()) {
//            Log.e("MainActivity", "Executable not found: " + execPath + " at " + System.currentTimeMillis());
//            appendResult(audioFile, "Executable not found: " + execPath, 0.0f);
//            return;
//        }
//
//        long startTime = System.currentTimeMillis();
//        try {
//            String result = executeWhisperDemo(execPath, encoderPath, decoderPath, lang, audioPath, libPath);
//            long endTime = System.currentTimeMillis();
//            float duration = (endTime - startTime) / 1000.0f;
//
//            Log.d("MainActivity", "Analysis completed for " + audioFile + ": " + result + " (duration: " + duration + "s) at " + endTime);
//            appendResult(audioFile, result.isEmpty() ? "No Whisper output" : result, duration);
//        } catch (Exception e) {
//            Log.e("MainActivity", "Analysis failed for " + audioFile + ": " + e.getMessage() + " at " + System.currentTimeMillis());
//            appendResult(audioFile, "Analysis failed: " + e.getMessage(), 0.0f);
//        }
//    }
//
//    public native String executeWhisperDemo(String execPath, String encoderPath, String decoderPath,
//                                            String lang, String audioPath, String libPath);
//    public native String executePermission(String modelPath);

    @Override
    protected void onDestroy() {
        super.onDestroy();
//        isContinuousRecording = false;
//        isRecordingThreadRunning = false;
//        isAnalysisThreadRunning = false;

//        if (audioRecord != null) {
//            stopRecording();
//        }

//        // 스레드가 안전하게 종료될 때까지 대기 (선택사항)
//        try {
//            if (recordingThread != null) {
//                recordingThread.join(1000);
//            }
//            if (analysisThread != null) {
//                analysisThread.join(1000);
//            }
//        } catch (InterruptedException e) {
//            Log.e("MainActivity", "Thread join interrupted: " + e.getMessage());
//        }
    }



}