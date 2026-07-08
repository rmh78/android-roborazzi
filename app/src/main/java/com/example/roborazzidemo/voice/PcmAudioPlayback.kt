package com.example.roborazzidemo.voice

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.util.Base64
import java.util.LinkedList

/**
 * PCM16 playback aligned with xAI's
 * [PcmAudioSink](https://github.com/xai-org/xai-cookbook/tree/main/Android/VoiceApiAndroidExample).
 */
class PcmAudioPlayback(
    private val context: Context,
) {
    private var audioTrack: AudioTrack? = null
    private val playbackQueue = LinkedList<ByteArray>()
    private val lock = Any()
    private var isPlaying = false
    private var chunksPlayed = 0

    @Volatile
    private var totalFramesWritten: Long = 0

    @Volatile
    private var drainingPlayback = false

    @Volatile
    var onIdleChanged: ((Boolean) -> Unit)? = null

    fun playBase64Chunk(base64Audio: String) {
        if (base64Audio.isEmpty()) return
        val pcm16 = Base64.decode(base64Audio, Base64.NO_WRAP)
        synchronized(lock) {
            playbackQueue.add(pcm16)
            if (!isPlaying) {
                isPlaying = true
                notifyIdleChanged()
                Thread { playNextChunk() }.start()
            }
        }
    }

    fun flush() {
        synchronized(lock) {
            playbackQueue.clear()
            totalFramesWritten = 0
            val track = audioTrack ?: return
            try {
                if (track.playState == AudioTrack.PLAYSTATE_PLAYING) {
                    track.pause()
                    track.flush()
                    track.play()
                }
            } catch (e: IllegalStateException) {
                VoiceLog.w("Playback", "AudioTrack.flush skipped: ${e.message}")
            }
            isPlaying = false
            drainingPlayback = false
        }
        notifyIdleChanged()
        VoiceLog.d("Playback", "Playback flushed (barge-in)")
    }

    fun stop() {
        synchronized(lock) {
            playbackQueue.clear()
            isPlaying = false
            drainingPlayback = false
            totalFramesWritten = 0
            releaseTrackLocked()
        }
        if (chunksPlayed > 0) {
            VoiceLog.i("Playback", "Stopped (chunks_played=$chunksPlayed)")
        }
        chunksPlayed = 0
        notifyIdleChanged()
    }

    fun isIdle(): Boolean = synchronized(lock) {
        !isPlaying && playbackQueue.isEmpty() && !drainingPlayback
    }

    fun whenIdle(onIdle: () -> Unit, postDrainDelayMs: Long = 0L) {
        Thread(
            {
                while (true) {
                    synchronized(lock) {
                        if (!isPlaying && playbackQueue.isEmpty()) {
                            break
                        }
                    }
                    Thread.sleep(PLAYBACK_IDLE_POLL_MS)
                }
                drainingPlayback = true
                notifyIdleChanged()
                waitForPlaybackDrain(postDrainDelayMs)
                synchronized(lock) {
                    releaseTrackLocked()
                    totalFramesWritten = 0
                }
                drainingPlayback = false
                VoiceLog.d("Playback", "Playback idle — ready for microphone capture")
                notifyIdleChanged()
                onIdle()
            },
            "voice-playback-idle",
        ).start()
    }

    private fun notifyIdleChanged() {
        onIdleChanged?.invoke(isIdle())
    }

    private fun waitForPlaybackDrain(postDrainDelayMs: Long) {
        val track = synchronized(lock) { audioTrack }
        if (track == null) {
            if (postDrainDelayMs > 0) Thread.sleep(postDrainDelayMs)
            return
        }
        val targetFrames = totalFramesWritten
        if (targetFrames > 0) {
            val audioDurationMs =
                (targetFrames * 1_000L) / VoiceConstants.SAMPLE_RATE_HZ
            val slackFrames = playbackDrainSlackFrames()
            val deadline = System.currentTimeMillis() +
                audioDurationMs +
                postDrainDelayMs +
                PLAYBACK_DRAIN_MARGIN_MS
            while (System.currentTimeMillis() < deadline) {
                try {
                    val head = track.playbackHeadPosition.toLong()
                    if (head + slackFrames >= targetFrames) break
                } catch (e: IllegalStateException) {
                    VoiceLog.w("Playback", "Playback drain poll skipped: ${e.message}")
                    break
                }
                Thread.sleep(PLAYBACK_HEAD_POLL_MS)
            }
        }
        if (postDrainDelayMs > 0) {
            Thread.sleep(postDrainDelayMs)
        }
    }

    private fun playbackDrainSlackFrames(): Long =
        if (VoiceDeviceHints.isLikelyEmulator()) {
            EMULATOR_PLAYBACK_HEAD_SLACK_FRAMES
        } else {
            PLAYBACK_HEAD_SLACK_FRAMES
        }

    private fun playNextChunk() {
        while (true) {
            val chunk: ByteArray?
            synchronized(lock) {
                if (playbackQueue.isEmpty()) {
                    isPlaying = false
                    if (totalFramesWritten > 0) {
                        drainingPlayback = true
                    }
                    notifyIdleChanged()
                    return
                }
                chunk = playbackQueue.poll()
            }

            val pcm16 = chunk ?: continue
            val track = synchronized(lock) {
                ensureTrackLocked()
                audioTrack
            } ?: return

            val written = track.write(pcm16, 0, pcm16.size, AudioTrack.WRITE_BLOCKING)
            if (written < 0) {
                VoiceLog.e("Playback", "AudioTrack.write failed with code $written")
                synchronized(lock) { isPlaying = false }
                return
            }
            totalFramesWritten += pcm16.size / BYTES_PER_FRAME
            chunksPlayed++
            if (chunksPlayed == 1 || chunksPlayed % PLAYBACK_LOG_INTERVAL == 0) {
                VoiceLog.d(
                    "Playback",
                    "Playing assistant audio (chunks=$chunksPlayed, bytes=${pcm16.size}, written=$written)",
                )
            }
        }
    }

    private fun ensureTrackLocked() {
        if (audioTrack != null) return
        boostPlaybackVolume()
        val minBuffer = AudioTrack.getMinBufferSize(
            VoiceConstants.SAMPLE_RATE_HZ,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        audioTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(VoiceConstants.SAMPLE_RATE_HZ)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build(),
            )
            .setBufferSizeInBytes(minBuffer)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
            .also { track ->
                track.play()
                VoiceLog.i(
                    "Playback",
                    "AudioTrack created (${VoiceConstants.SAMPLE_RATE_HZ}Hz mono PCM16, media usage)",
                )
            }
    }

    private fun boostPlaybackVolume() {
        val audioManager =
            context.applicationContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val stream = AudioManager.STREAM_MUSIC
        val maxVolume = audioManager.getStreamMaxVolume(stream)
        val currentVolume = audioManager.getStreamVolume(stream)
        if (currentVolume < maxVolume) {
            audioManager.setStreamVolume(stream, maxVolume, 0)
            VoiceLog.i(
                "Playback",
                "Boosted STREAM_MUSIC volume ($currentVolume → $maxVolume) for assistant audio",
            )
        }
    }

    private fun releaseTrackLocked() {
        val track = audioTrack ?: return
        audioTrack = null
        try {
            if (track.playState == AudioTrack.PLAYSTATE_PLAYING) {
                track.stop()
            }
        } catch (e: IllegalStateException) {
            VoiceLog.w("Playback", "AudioTrack.stop skipped: ${e.message}")
        }
        try {
            track.release()
        } catch (e: IllegalStateException) {
            VoiceLog.w("Playback", "AudioTrack.release skipped: ${e.message}")
        }
    }

    companion object {
        private const val BYTES_PER_FRAME = 2
        private const val PLAYBACK_LOG_INTERVAL = 25
        private const val PLAYBACK_IDLE_POLL_MS = 50L
        private const val PLAYBACK_HEAD_POLL_MS = 20L
        private const val PLAYBACK_DRAIN_MARGIN_MS = 1_500L
        private const val PLAYBACK_HEAD_SLACK_FRAMES = 2_400L
        private const val EMULATOR_PLAYBACK_HEAD_SLACK_FRAMES = 7_200L
    }
}