package com.hjict.voiceai;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.provider.Settings;
import android.util.Log;

public class VoiceAIReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.i("VoiceAIReceiver", "##################################### VoiceAIReceiver - onReceive()");

        if (intent.getAction() != null && intent.getAction().equals(Intent.ACTION_BOOT_COMPLETED)) {
            int isVoiceAIon = Settings.System.getInt(context.getContentResolver(), H500.Settings.VOICE_AI_STATE, 1);
            if (isVoiceAIon == 1) {
                Intent service = new Intent(context, VoiceAIService.class);
                Log.i("VoiceAIReceiver", "VoiceAI - Starting the service.");
                context.startService(service); // 일반 서비스로 시작
            }
        } else if (intent.getAction() != null && intent.getAction().equals(H500.Intent.ACTION_VOICE_AI_STATE_CHANGED)) {
            Log.i("VoiceAIReceiver", "##################################### VoiceAIReceiver - ACTION_VOICE_AI_STATE_CHANGED");
            int voiceAIValue = intent.getIntExtra("state", 1);
            Intent service = new Intent(context, VoiceAIService.class);
            if (voiceAIValue == 1) {
                Log.i("VoiceAIReceiver", "VoiceAI is turned on. Starting the service.");
                context.startService(service); // 일반 서비스로 시작
            } else {
                context.stopService(service);
                Log.i("VoiceAIReceiver", "VoiceAI is turned off. Stopping the service.");
            }
        }
    }
}
