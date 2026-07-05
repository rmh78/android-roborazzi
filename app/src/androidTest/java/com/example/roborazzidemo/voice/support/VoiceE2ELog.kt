package com.example.roborazzidemo.voice.support

import android.util.Log

object VoiceE2ELog {
    const val TAG = "VoiceE2E"

    fun step(message: String) {
        Log.i(TAG, "STEP: $message")
    }

    fun detail(message: String) {
        Log.d(TAG, message)
    }
}