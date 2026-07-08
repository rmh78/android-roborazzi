package com.example.roborazzidemo.voice

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack

/**
 * Plays E2E user PCM on the device speaker in sync with [GrokVoiceSession] WebSocket streaming.
 */
object TestPcmMirrorPlayback {
    fun create(context: Context): PcmChunkMirror =
        MirrorSession(context.applicationContext)

    private class MirrorSession(
        private val context: Context,
    ) : PcmChunkMirror {
        private val lock = Any()
        private var audioTrack: AudioTrack? = null
        private var framesWritten: Long = 0

        override fun writeChunk(pcm16: ByteArray) {
            synchronized(lock) {
                val track = ensureTrackLocked()
                val written = track.write(pcm16, 0, pcm16.size, AudioTrack.WRITE_BLOCKING)
                if (written < 0) {
                    VoiceLog.e("TestPcmMirror", "AudioTrack.write failed with code $written")
                    return
                }
                framesWritten += pcm16.size / BYTES_PER_FRAME
            }
        }

        override fun awaitDrain() {
            val track = synchronized(lock) { audioTrack } ?: return
            val targetFrames = synchronized(lock) { framesWritten }
            if (targetFrames <= 0) return
            val audioDurationMs = (targetFrames * 1_000L) / VoiceConstants.SAMPLE_RATE_HZ
            val deadline = System.currentTimeMillis() + audioDurationMs + DRAIN_MARGIN_MS
            while (System.currentTimeMillis() < deadline) {
                try {
                    val head = track.playbackHeadPosition.toLong()
                    if (head + DRAIN_SLACK_FRAMES >= targetFrames) break
                } catch (e: IllegalStateException) {
                    VoiceLog.w("TestPcmMirror", "Playback drain poll skipped: ${e.message}")
                    break
                }
                Thread.sleep(DRAIN_POLL_MS)
            }
        }

        override fun release() {
            synchronized(lock) {
                releaseTrackLocked()
                framesWritten = 0
            }
        }

        private fun ensureTrackLocked(): AudioTrack {
            audioTrack?.let { return it }
            boostPlaybackVolume()
            val minBuffer = AudioTrack.getMinBufferSize(
                VoiceConstants.SAMPLE_RATE_HZ,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
            )
            return AudioTrack.Builder()
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
                        "TestPcmMirror",
                        "Mirroring user PCM on speaker (${VoiceConstants.SAMPLE_RATE_HZ}Hz mono)",
                    )
                }
                .also { audioTrack = it }
        }

        private fun boostPlaybackVolume() {
            val audioManager =
                context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val stream = AudioManager.STREAM_MUSIC
            val maxVolume = audioManager.getStreamMaxVolume(stream)
            val currentVolume = audioManager.getStreamVolume(stream)
            if (currentVolume < maxVolume) {
                audioManager.setStreamVolume(stream, maxVolume, 0)
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
                VoiceLog.w("TestPcmMirror", "AudioTrack.stop skipped: ${e.message}")
            }
            try {
                track.release()
            } catch (e: IllegalStateException) {
                VoiceLog.w("TestPcmMirror", "AudioTrack.release skipped: ${e.message}")
            }
        }
    }

    private const val BYTES_PER_FRAME = 2
    private const val DRAIN_POLL_MS = 20L
    private const val DRAIN_MARGIN_MS = 800L
    private const val DRAIN_SLACK_FRAMES = 2_400L
}