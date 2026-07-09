package com.example.roborazzidemo.voice

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Base64
import java.util.LinkedList

/**
 * PCM16 playback aligned with xAI's
 * [PcmAudioSink](https://github.com/xai-org/xai-cookbook/tree/main/Android/VoiceApiAndroidExample).
 */
class PcmAudioPlayback {
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

    /**
     * Invokes [onIdle] after queued PCM has been written and the AudioTrack has drained.
     *
     * @param postDrainDelayMs extra silence after hardware drain (emulator echo tail)
     * @param graceForStartMs wait this long for late audio deltas after response.done
     * before treating "empty queue" as idle (avoids killing Hello mid-arrival)
     */
    fun whenIdle(
        onIdle: () -> Unit,
        postDrainDelayMs: Long = 0L,
        graceForStartMs: Long = PLAYBACK_START_GRACE_MS,
    ) {
        Thread(
            {
                // response.done can race the last audio deltas; wait briefly for work.
                val graceDeadline = System.currentTimeMillis() + graceForStartMs
                while (System.currentTimeMillis() < graceDeadline) {
                    val hasWork = synchronized(lock) {
                        isPlaying || playbackQueue.isNotEmpty() || totalFramesWritten > 0
                    }
                    if (hasWork) break
                    Thread.sleep(PLAYBACK_IDLE_POLL_MS)
                }

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
                // Do not release the track immediately after drain — keep it warm for the
                // next assistant turn. Only stop() releases on disconnect.
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

    private fun playbackDrainSlackFrames(): Long = PLAYBACK_HEAD_SLACK_FRAMES

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
        val minBuffer = AudioTrack.getMinBufferSize(
            VoiceConstants.SAMPLE_RATE_HZ,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        // 4× min buffer reduces underruns on emulator host-audio scheduling.
        val bufferSize = (minBuffer * 4).coerceAtLeast(minBuffer)
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
            .setBufferSizeInBytes(bufferSize)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
            .also { track ->
                // Ensure stream is audible (some AVDs start media tracks at 0 gain).
                try {
                    track.setVolume(1.0f)
                } catch (e: IllegalStateException) {
                    VoiceLog.w("Playback", "AudioTrack.setVolume skipped: ${e.message}")
                }
                track.play()
                VoiceLog.i(
                    "Playback",
                    "AudioTrack created (${VoiceConstants.SAMPLE_RATE_HZ}Hz mono PCM16, " +
                        "buffer=$bufferSize, media usage, volume=1.0)",
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
        /** Allow late response.output_audio.delta after response.done before idling. */
        private const val PLAYBACK_START_GRACE_MS = 400L
    }
}