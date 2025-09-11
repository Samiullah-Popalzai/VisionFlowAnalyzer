package com.apro.ProMaherQuickBetAnalyzer;

import android.util.Log;

public class SamiLogger {
    private static boolean isEnabled=true;
    public static void log(String key, String msg){
        if(isEnabled){
            Log.d(key, msg);
        }
    }
}
