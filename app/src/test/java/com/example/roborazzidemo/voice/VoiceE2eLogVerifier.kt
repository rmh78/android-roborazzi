package com.example.roborazzidemo.voice

/**
 * Verifies captured VoiceAssistant logcat for E2E acceptance criteria.
 * Criterion 3 requires a direct chain after text inject:
 * text create → response.create → response.created → audio.delta → response.done
 * with no intervening response.done or tool call between created and first audio.
 */
object VoiceE2eLogVerifier {
    data class Result(val passed: Boolean, val failures: List<String>)

    private val connectPatterns = listOf(
        "WebSocket open",
        "conversation.created",
        "session.update",
        "session.updated",
        "Session configured",
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
        val failures = mutableListOf<String>()

        val textCreate = Regex("""conversation\.item\.create \(text:""").find(slice)
        if (textCreate == null) failures += "text turn: missing conversation.item.create (text:)"

        val responseCreate = Regex("""response\.create \(after text message\)""").find(slice)
        if (responseCreate == null) failures += "text turn: missing response.create (after text message)"

        val created = if (responseCreate != null) {
            Regex("""← response\.created""").find(slice, responseCreate.range.last + 1)
        } else {
            Regex("""← response\.created""").find(slice)
        }
        if (created == null) failures += "text turn: missing response.created after text response.create"

        if (textCreate != null && responseCreate != null &&
            textCreate.range.first >= responseCreate.range.first
        ) {
            failures += "text turn: conversation.item.create not before response.create"
        }
        if (responseCreate != null && created != null &&
            responseCreate.range.first >= created.range.first
        ) {
            failures += "text turn: response.create not before response.created"
        }

        val createdPos = created?.range?.first
        val audio = if (createdPos != null) {
            Regex("""← response\.(output_)?audio\.delta""").find(slice, createdPos)
        } else {
            null
        }
        if (createdPos != null && audio == null) {
            failures += "text turn: missing audio.delta after response.created"
        }

        val audioPos = audio?.range?.first
        if (createdPos != null && audioPos != null) {
            val between = slice.substring(createdPos, audioPos)
            if (between.contains("← response.done")) {
                failures += "text turn: response.done between response.created and first audio.delta"
            }
            if (between.contains("function_call_arguments.done")) {
                failures += "text turn: tool call between response.created and first audio.delta"
            }
        }

        val done = if (audioPos != null) {
            Regex("""← response\.done""").find(slice, audioPos)
        } else {
            null
        }
        if (audioPos != null && done == null) {
            failures += "text turn: missing response.done after first audio.delta"
        }
        if (audioPos != null && done != null && audioPos >= done.range.first) {
            failures += "text turn: response.done not after first audio.delta"
        }

        if (slice.contains("[Session] API error")) failures += "text turn: API error in inject slice"
        if (slice.contains("Response timeout")) failures += "text turn: Response timeout in inject slice"

        return Result(failures.isEmpty(), failures)
    }
}