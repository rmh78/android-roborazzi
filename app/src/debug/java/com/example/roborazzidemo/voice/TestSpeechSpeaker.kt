package com.example.roborazzidemo.voice

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Audible E2E user-turn cue in the app process.
 *
 * Default mode is a short [ToneGenerator] beep — near-zero CPU vs Android TTS on AVDs,
 * which was spiking load and sounding harsh at full volume.
 *
 * Full TTS remains available when the instrumentation arg `testSpeechMode=tts` is set.
 */
object TestSpeechSpeaker {
    private const val SPEAK_TIMEOUT_MS = 45_000L
    private const val BEEP_DURATION_MS = 160
    private const val BEEP_HOLD_MS = 200L
    /** ToneGenerator volume 0–100 — clearly audible without clipping. */
    private const val BEEP_VOLUME = 75
    private const val TTS_VOLUME = 0.65f
    private const val TTS_SPEECH_RATE = 0.95f
    private const val TTS_PITCH = 1.0f
    private const val MEDIA_VOLUME_FRACTION = 0.7f

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

    enum class Mode {
        /** Short system tone — default, low CPU. */
        Beep,
        /** Full TextToSpeech of the prompt text — opt-in only. */
        Tts,
    }

    fun warmUp(context: Context, mode: Mode = Mode.Beep) {
        if (mode == Mode.Tts) {
            mainHandler.post { ensureEngineOnMainThread(context.applicationContext) }
        }
    }

    fun announce(
        context: Context,
        text: String,
        mode: Mode = Mode.Beep,
        onComplete: (Boolean) -> Unit,
    ) {
        when (mode) {
            Mode.Beep -> playBeep(context.applicationContext, text, onComplete)
            Mode.Tts -> speakTts(context.applicationContext, text, onComplete)
        }
    }

    /** @deprecated Prefer [announce]; kept for call-site clarity. */
    fun speak(context: Context, text: String, onComplete: (Boolean) -> Unit) {
        announce(context, text, Mode.Beep, onComplete)
    }

    private fun playBeep(context: Context, text: String, onComplete: (Boolean) -> Unit) {
        mainHandler.post {
            ensureMediaAudible(context)
            VoiceDebugBridge.pulseMicLevel(text)
            VoiceLog.i("TestSpeech", "User-turn beep (low CPU): \"$text\"")
            var tone: ToneGenerator? = null
            try {
                tone = ToneGenerator(AudioManager.STREAM_MUSIC, BEEP_VOLUME)
                // Soft prop beep — short and clear on emulator host audio.
                tone.startTone(ToneGenerator.TONE_PROP_BEEP, BEEP_DURATION_MS)
                mainHandler.postDelayed(
                    {
                        try {
                            tone?.stopTone()
                            tone?.release()
                        } catch (_: Exception) {
                            // ignore
                        }
                        onComplete(true)
                    },
                    BEEP_HOLD_MS,
                )
            } catch (e: Exception) {
                VoiceLog.w("TestSpeech", "Beep failed: ${e.message}")
                try {
                    tone?.release()
                } catch (_: Exception) {
                    // ignore
                }
                // Do not fail the E2E turn for a missing tone — inject still runs.
                onComplete(true)
            }
        }
    }

    private fun speakTts(context: Context, text: String, onComplete: (Boolean) -> Unit) {
        mainHandler.post {
            when {
                ready -> speakTtsNow(context, text, onComplete)
                initFailed -> {
                    VoiceLog.w("TestSpeech", "TTS unavailable — falling back to beep for: $text")
                    playBeep(context, text, onComplete)
                }
                else -> {
                    pendingSpeaks += PendingSpeak(text, onComplete)
                    ensureEngineOnMainThread(context)
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
                    // Complete pending with beep fallback
                    val queued = pendingSpeaks.toList()
                    pendingSpeaks.clear()
                    queued.forEach { (text, onComplete) -> playBeep(context, text, onComplete) }
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
                engine.setSpeechRate(TTS_SPEECH_RATE)
                engine.setPitch(TTS_PITCH)

                when (engine.setLanguage(Locale.US)) {
                    TextToSpeech.LANG_MISSING_DATA,
                    TextToSpeech.LANG_NOT_SUPPORTED,
                    -> {
                        initFailed = true
                        VoiceLog.w("TestSpeech", "English TTS unavailable on device")
                        val queued = pendingSpeaks.toList()
                        pendingSpeaks.clear()
                        queued.forEach { (text, onComplete) -> playBeep(context, text, onComplete) }
                    }
                    else -> {
                        ready = true
                        VoiceLog.i("TestSpeech", "TextToSpeech ready in app process")
                        val queued = pendingSpeaks.toList()
                        pendingSpeaks.clear()
                        queued.forEach { (text, onComplete) -> speakTtsNow(context, text, onComplete) }
                    }
                }
            }
        }
    }

    private fun speakTtsNow(context: Context, text: String, onComplete: (Boolean) -> Unit) {
        val engine = tts
        if (engine == null) {
            playBeep(context, text, onComplete)
            return
        }

        ensureMediaAudible(context)
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

        VoiceLog.i("TestSpeech", "Speaking TTS (opt-in): \"$text\"")
        VoiceDebugBridge.pulseMicLevel(text)
        val params = Bundle().apply {
            putInt(TextToSpeech.Engine.KEY_PARAM_STREAM, AudioManager.STREAM_MUSIC)
            putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, TTS_VOLUME)
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

    private fun ensureMediaAudible(context: Context) {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val stream = AudioManager.STREAM_MUSIC
        val max = audioManager.getStreamMaxVolume(stream)
        if (max <= 0) return
        val target = (max * MEDIA_VOLUME_FRACTION).toInt().coerceIn(1, max)
        val current = audioManager.getStreamVolume(stream)
        if (current < target) {
            audioManager.setStreamVolume(stream, target, 0)
            VoiceLog.d("TestSpeech", "Media volume $current → $target (max=$max)")
        }
    }
}
