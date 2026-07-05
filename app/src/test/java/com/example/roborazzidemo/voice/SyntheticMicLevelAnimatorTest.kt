package com.example.roborazzidemo.voice

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test

class SyntheticMicLevelAnimatorTest {
    @Test
    fun estimateSpeechDuration_scalesWithTextLength() {
        val short = SyntheticMicLevelAnimator.estimateSpeechDuration("hi")
        val long = SyntheticMicLevelAnimator.estimateSpeechDuration(
            "describe the screen for me in detail please",
        )

        assertTrue(long > short)
        assertTrue(short >= 1_800L)
    }
}