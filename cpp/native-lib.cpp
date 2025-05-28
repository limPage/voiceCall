#include <jni.h>
#include <string>
#include <unistd.h>
#include <sys/wait.h>
#include <stdio.h>
#include <sys/stat.h>
#include <android/log.h>
#define LOG_TAG "WhisperDemo"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
void logRawBytes(const std::string& str) {
    std::string hex;
    for (unsigned char c : str) {
        char buf[8];
        snprintf(buf, sizeof(buf), "%02X ", c);
        hex += buf;
    }
    LOGD("Raw bytes of output: %s", hex.c_str());
}
bool isValidUTF8(const std::string& str) {
    const unsigned char* bytes = reinterpret_cast<const unsigned char*>(str.c_str());
    size_t i = 0;
    while (i < str.length()) {
        int bytesToRead = 0;
        if (bytes[i] <= 0x7F) bytesToRead = 1;         // 1-byte (ASCII)
        else if ((bytes[i] & 0xE0) == 0xC0) bytesToRead = 2; // 2-byte
        else if ((bytes[i] & 0xF0) == 0xE0) bytesToRead = 3; // 3-byte
        else if ((bytes[i] & 0xF8) == 0xF0) bytesToRead = 4; // 4-byte
        else return false; // Invalid start byte

        if (i + bytesToRead > str.length()) return false; // Truncated

        for (int j = 1; j < bytesToRead; ++j) {
            if ((bytes[i + j] & 0xC0) != 0x80) return false; // Invalid continuation byte
        }
        i += bytesToRead;
    }
    return true;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_hjict_voiceai_VoiceAIService_executePermission(JNIEnv* env, jobject /* this */, jstring path) {
    const char *pathStr = env->GetStringUTFChars(path, nullptr);
    if (pathStr == nullptr) {
        LOGE("Failed to convert jstring to const char*");
        return env->NewStringUTF("Error: Failed to get path");
    }

    LOGD("Setting execute permission for: %s", pathStr);

    // 명령어 생성: "chmod +x <path>"
    std::string command = "chmod -R 777 ";
    command += pathStr;

    // 명령어 실행 및 결과 캡처
    FILE *pipe = popen(command.c_str(), "r");
    if (!pipe) {
        env->ReleaseStringUTFChars(path, pathStr);
        LOGE("Failed to execute command: %s", command.c_str());
        return env->NewStringUTF("Error: Failed to execute command");
    }

    // 명령어 출력 읽기
    char buffer[128];
    std::string result = "";
    while (fgets(buffer, sizeof(buffer), pipe) != nullptr) {
        result += buffer;
    }

    // 파이프 닫기 및 상태 확인
    int status = pclose(pipe);
    env->ReleaseStringUTFChars(path, pathStr);

    // 결과 반환
    if (status == 0) {
        LOGD("Permission set successfully for: %s", pathStr);
        return env->NewStringUTF("Success: Permission Check: OK");
    } else {
        std::string errorMsg = "Error: Permission Command failed with status " + std::to_string(status);
        if (!result.empty()) {
            errorMsg += "\nOutput: " + result;
        }
        LOGE("%s", errorMsg.c_str());
        return env->NewStringUTF(errorMsg.c_str());
    }
}


extern "C" JNIEXPORT jstring JNICALL
Java_com_hjict_voiceai_VoiceAIService_executeWhisperDemo(
        JNIEnv* env, jobject /* this */, jstring execPath, jstring encoderPath, jstring decoderPath,
        jstring lang, jstring audioPath, jstring libPath) {

    const char* exec_path = env->GetStringUTFChars(execPath, 0);
    const char* encoder_path = env->GetStringUTFChars(encoderPath, 0);
    const char* decoder_path = env->GetStringUTFChars(decoderPath, 0);
    const char* language = env->GetStringUTFChars(lang, 0);
    const char* audio_path = env->GetStringUTFChars(audioPath, 0);
    const char* lib_path = env->GetStringUTFChars(libPath, 0);

    setenv("LD_LIBRARY_PATH", lib_path, 1);
    LOGD("LD_LIBRARY_PATH set to: %s", lib_path);

    char command[512];
    snprintf(command, sizeof(command), "%s %s %s %s %s 2>/sdcard/whisper_stderr.txt",
             exec_path, encoder_path, decoder_path, language, audio_path);
    LOGD("Executing command: %s", command);

    FILE* pipe = popen(command, "r");
    if (!pipe) {
        LOGD("Failed to open pipe");
        env->ReleaseStringUTFChars(execPath, exec_path);
        env->ReleaseStringUTFChars(encoderPath, encoder_path);
        env->ReleaseStringUTFChars(decoderPath, decoder_path);
        env->ReleaseStringUTFChars(lang, language);
        env->ReleaseStringUTFChars(audioPath, audio_path);
        env->ReleaseStringUTFChars(libPath, lib_path);
        return env->NewStringUTF("Failed to open pipe");
    }

    char buffer[128];
    std::string result = "";
    std::string whisper_output = "";
    const std::string prefix = "Whisper output: ";

    while (fgets(buffer, sizeof(buffer), pipe) != NULL) {
        std::string line(buffer);
        result += line;
        LOGD("Captured output: %s", line.c_str());

        size_t pos = line.find(prefix);
        if (pos != std::string::npos) {
            whisper_output = line.substr(pos + prefix.length());
            size_t newline_pos = whisper_output.find('\n');
            if (newline_pos != std::string::npos) {
                whisper_output = whisper_output.substr(0, newline_pos);
            }
            break;
        }
    }
    int exit_code = pclose(pipe);
    LOGD("Exit code: %d, Full Result: %s", exit_code, result.c_str());
    LOGD("Extracted Whisper output: %s", whisper_output.c_str());
    logRawBytes(whisper_output); // Debug raw bytes

    env->ReleaseStringUTFChars(execPath, exec_path);
    env->ReleaseStringUTFChars(encoderPath, encoder_path);
    env->ReleaseStringUTFChars(decoderPath, decoder_path);
    env->ReleaseStringUTFChars(lang, language);
    env->ReleaseStringUTFChars(audioPath, audio_path);
    env->ReleaseStringUTFChars(libPath, lib_path);

    if (whisper_output.empty()) {
        char error_msg[256];
        snprintf(error_msg, sizeof(error_msg), "No Whisper output found (exit code: %d)", exit_code);
        return env->NewStringUTF(error_msg);
    }

    if (!isValidUTF8(whisper_output)) {
        LOGE("Invalid UTF-8 detected in Whisper output: %s", whisper_output.c_str());
        return env->NewStringUTF("Error: Invalid UTF-8 output from Whisper");
    }

    return env->NewStringUTF(whisper_output.c_str());
}
