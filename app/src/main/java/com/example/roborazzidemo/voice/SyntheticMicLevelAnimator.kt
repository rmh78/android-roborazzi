package com.example.roborazzidemo.voice

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.sin

/**
 * Drives [level] while user speech is injected (E2E TTS / VOICE_SPOKEN) so the SIG meter
 * animates even when live [PcmAudioCapture] is paused or the emulator mic is silent.
 */
class SyntheticMicLevelAnimator(
    private val scope: CoroutineScope,
) {
    private val _level = MutableStateFlow(0f)
    val level: StateFlow<Float> = _level.asStateFlow()

    private var animationJob: Job? = null

    fun pulseForSpeech(
        text: String,
        durationMs: Long = estimateSpeechDuration(text),
    ) {
        pulseForDuration(durationMs)
    }

    fun pulseForDuration(durationMs: Long) {
        animationJob?.cancel()
        if (durationMs <= 0L) {
            _level.value = 0f
            return
        }
        animationJob = scope.launch {
            VoiceLog.d("Mic", "Synthetic SIG pulse started (${durationMs}ms)")
            val deadline = System.currentTimeMillis() + durationMs
            var tick = 0
            while (isActive && System.currentTimeMillis() < deadline) {
                val wave = (sin(tick * 0.42) + 1.0) / 2.0
                val level = (0.18f + 0.62f * wave.toFloat()).coerceIn(0.12f, 0.9f)
                _level.value = level
                tick++
                delay(PULSE_INTERVAL_MS)
            }
            _level.value = 0f
            VoiceLog.d("Mic", "Synthetic SIG pulse finished")
        }
    }

    fun cancel() {
        animationJob?.cancel()
        animationJob = null
        _level.value = 0f
    }

    companion object {
        /** Coarser ticks — SIG meter does not need 12 Hz updates. */
        private const val PULSE_INTERVAL_MS = 120L

        fun estimateSpeechDuration(text: String): Long {
            val trimmed = text.trim()
            val byLength = 1_400L + trimmed.length * 55L
            return byLength.coerceIn(1_800L, 12_000L)
        }
    }
}