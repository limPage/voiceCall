package com.hjict.voiceai;

public class H500 extends Thread{

    class Settings {
        public static final String VOICE_AI_STATE = "voice_ai_state";
        public static final String ADMIN_SERVER_PORT = "admin_server_port";
        public static final String SIP_SERVER       = "sip_server";
        public static final String SIP_LINE_NUMBER  = "sip_line_number";
    }

    class Intent{
        public static final String ACTION_VOICE_AI_STATE_CHANGED = "android.intent.action.VOICE_AI_STATE_CHANGED";
        public static final String ACTION_PJSIP_MAKE_CALL = "android.intent.action.PJSIP_MAKE_CALL";
        public static final String ACTION_PJSIP_END_CALL = "android.intent.action.PJSIP_END_CALL";

    }
    class Permission {
        public static final String H500_BROADCAST_PERMISSION = "android.permission.H500_BROADCAST_PERMISSION";
    }

}
