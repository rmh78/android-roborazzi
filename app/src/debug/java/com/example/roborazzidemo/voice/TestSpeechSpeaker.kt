package com.example.roborazzidemo.voice

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Speaks E2E test utterances in the app process via TTS (emulator/device speaker).
 */
object TestSpeechSpeaker {
    private const val SPEAK_TIMEOUT_MS = 45_000L

    private val mainHandler = Handler(Looper.getMainLooper())
    private val pendingSpeaks = mutableListOf<PendingSpeak>()

    @Volatile
    private var tts: TextToSpeech? = null

    @Volatile
    private var ready = false

    @Volatile
    private var initFailed = false

    @Volatile
    private var initializing = false

    private val initLock = Any()

    private data class PendingSpeak(
        val text: String,
        val onComplete: (Boolean) -> Unit,
    )

    fun warmUp(context: Context) {
        mainHandler.post { ensureEngineOnMainThread(context.applicationContext) }
    }

    fun speak(context: Context, text: String, onComplete: (Boolean) -> Unit) {
        mainHandler.post {
            when {
                ready -> speakNow(context.applicationContext, text, onComplete)
                initFailed -> {
                    VoiceLog.w("TestSpeech", "TTS unavailable — skipping playback for: $text")
                    onComplete(false)
                }
                else -> {
                    pendingSpeaks += PendingSpeak(text, onComplete)
                    ensureEngineOnMainThread(context.applicationContext)
                }
            }
        }
    }

    private fun ensureEngineOnMainThread(context: Context) {
        if (ready || initFailed || initializing) return

        synchronized(initLock) {
            if (ready || initFailed || initializing) return
            initializing = true
            VoiceLog.i("TestSpeech", "Initializing TextToSpeech on main thread…")
            tts = TextToSpeech(context) { status ->
                val engine = tts
                initializing = false
                if (status != TextToSpeech.SUCCESS || engine == null) {
                    initFailed = true
                    VoiceLog.w("TestSpeech", "TextToSpeech init failed (status=$status)")
                    failPendingSpeaks()
                    return@TextToSpeech
                }

                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                    engine.setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build(),
                    )
                }

                when (engine.setLanguage(Locale.US)) {
                    TextToSpeech.LANG_MISSING_DATA,
                    TextToSpeech.LANG_NOT_SUPPORTED,
                    -> {
                        initFailed = true
                        VoiceLog.w("TestSpeech", "English TTS unavailable on device")
                        failPendingSpeaks()
                    }
                    else -> {
                        ready = true
                        VoiceLog.i("TestSpeech", "TextToSpeech ready in app process")
                        flushPendingSpeaks(context)
                    }
                }
            }
        }
    }

    private fun flushPendingSpeaks(context: Context) {
        val queued = pendingSpeaks.toList()
        pendingSpeaks.clear()
        queued.forEach { (text, onComplete) ->
            speakNow(context, text, onComplete)
        }
    }

    private fun failPendingSpeaks() {
        val queued = pendingSpeaks.toList()
        pendingSpeaks.clear()
        queued.forEach { (_, onComplete) -> onComplete(false) }
    }

    private fun speakNow(context: Context, text: String, onComplete: (Boolean) -> Unit) {
        val engine = tts
        if (engine == null) {
            onComplete(false)
            return
        }

        boostPlaybackVolume(context)
        val finished = AtomicBoolean(false)
        val utteranceId = "voice-test-${System.nanoTime()}"

        engine.setOnUtteranceProgressListener(
            object : UtteranceProgressListener() {
                private fun complete(success: Boolean) {
                    if (finished.compareAndSet(false, true)) {
                        onComplete(success)
                    }
                }

                override fun onStart(utteranceId: String?) = Unit

                override fun onDone(utteranceId: String?) {
                    complete(true)
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    complete(false)
                }

                override fun onError(utteranceId: String?, errorCode: Int) {
                    complete(false)
                }
            },
        )

        VoiceLog.i("TestSpeech", "Speaking through emulator audio: \"$text\"")
        VoiceDebugBridge.pulseMicLevel(text)
        val params = Bundle().apply {
            putInt(TextToSpeech.Engine.KEY_PARAM_STREAM, AudioManager.STREAM_MUSIC)
            putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1f)
        }
        val queued = engine.speak(text, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
        if (queued == TextToSpeech.ERROR) {
            VoiceLog.w("TestSpeech", "TTS speak() returned ERROR for: \"$text\"")
            onComplete(false)
            return
        }

        mainHandler.postDelayed(
            {
                if (finished.compareAndSet(false, true)) {
                    VoiceLog.w("TestSpeech", "Timed out waiting for TTS to finish: \"$text\"")
                    onComplete(false)
                }
            },
            SPEAK_TIMEOUT_MS,
        )
    }

    private fun boostPlaybackVolume(context: Context) {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val stream = AudioManager.STREAM_MUSIC
        audioManager.setStreamVolume(stream, audioManager.getStreamMaxVolume(stream), 0)
    }
}