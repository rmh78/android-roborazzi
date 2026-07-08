package com.example.roborazzidemo.voice

import android.content.Context

class VoicePreferences(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getSelectedVoiceId(): String? = prefs.getString(KEY_SELECTED_VOICE, null)

    fun setSelectedVoiceId(voiceId: String) {
        prefs.edit().putString(KEY_SELECTED_VOICE, voiceId).apply()
    }

    companion object {
        private const val PREFS_NAME = "voice_assistant_prefs"
        private const val KEY_SELECTED_VOICE = "selected_voice_id"
    }
}