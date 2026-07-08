package com.example.roborazzidemo.voice

/**
 * Debug E2E hook: mirror each PCM frame to the device speaker while it is streamed to xAI.
 */
interface PcmChunkMirror {
    fun writeChunk(pcm16: ByteArray)
    fun awaitDrain()
    fun release()
}