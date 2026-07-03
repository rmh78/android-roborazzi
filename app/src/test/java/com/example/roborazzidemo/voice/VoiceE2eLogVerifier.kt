package com.example.roborazzidemo.voice

/**
 * Verifies captured VoiceAssistant logcat for E2E acceptance criteria.
 * Used by [VoiceE2eLogVerifierTest] against a real captured log fixture.
 */
object VoiceE2eLogVerifier {
    data class Result(val passed: Boolean, val failures: List<String>)

    private val connectPatterns = listOf(
        "WebSocket open",
        "conversation.created",
        "session.update",
        "session.updated",
        "Session configured",
        "Mic streaming active",
    )

    fun verifyConnectPhase(log: String): Result {
        val failures = connectPatterns.filterNot { log.contains(it) }
        return Result(failures.isEmpty(), failures.map { "connect: missing '$it'" })
    }

    fun verifyTextTurnSequence(log: String): Result {
        if (!log.contains("Injected text command")) {
            return Result(false, listOf("text turn: missing Injected text command"))
        }
        val sliceStart = listOf(
            "Forwarding text to session",
            "Injected text command",
        ).mapNotNull { marker ->
            log.indexOf(marker).takeIf { it >= 0 }
        }.minOrNull()
            ?: return Result(false, listOf("text turn: missing inject marker"))
        val slice = log.substring(sliceStart)

        val ordered = listOf(
            Regex("""conversation\.item\.create \(text:"""),
            Regex("""response\.create \(after text message\)"""),
            Regex("""← response\.created"""),
            Regex("""← response\.(output_)?audio\.delta"""),
        )

        var lastPos = -1
        val failures = mutableListOf<String>()
        for (pattern in ordered) {
            val match = pattern.find(slice)
            if (match == null) {
                failures += "text turn: missing ordered event matching ${pattern.pattern}"
                continue
            }
            if (match.range.first <= lastPos) {
                failures += "text turn: out-of-order event ${pattern.pattern}"
            }
            lastPos = match.range.first
        }

        val audioPos = Regex("""← response\.(output_)?audio\.delta""")
            .findAll(slice)
            .lastOrNull()
            ?.range
            ?.first
        val doneAfterAudio = Regex("""← response\.done""")
            .findAll(slice)
            .map { it.range.first }
            .any { pos -> audioPos != null && pos > audioPos }

        if (audioPos == null) {
            failures += "text turn: no audio delta in inject slice"
        } else if (!doneAfterAudio) {
            failures += "text turn: no response.done after audio delta"
        }

        if (slice.contains("[Session] API error")) {
            failures += "text turn: API error in inject slice"
        }
        if (slice.contains("Response timeout")) {
            failures += "text turn: Response timeout in inject slice"
        }

        return Result(failures.isEmpty(), failures)
    }
}