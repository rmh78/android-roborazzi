package com.example.roborazzidemo.voice.support

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.StaleObjectException
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until

class VoiceAppTestRobot private constructor(
    private val device: UiDevice,
    private val context: Context,
) {
    fun assertAppVisible() {
        checkNotNull(device.wait(Until.findObject(By.text("Voice Assistant")), 10_000)) {
            "Voice Assistant overlay was not visible on screen."
        }
    }

    fun assertHomeScreenVisible() = waitForHomeScreen(timeoutMillis = 5_000)

    fun connect() {
        tapConnectSwitch()
        waitForConnectionStarted()
    }

    fun disconnect() = tapConnectSwitch()

    fun speak(text: String) = TestSpeechAnnouncer.speak(context, text)

    fun speakAndWaitForResponse(text: String, timeoutMillis: Long = 90_000) {
        speak(text)
        waitForVoiceResponseComplete(timeoutMillis)
    }

    fun speakAndWaitForTool(text: String, toolName: String, timeoutMillis: Long = 120_000) {
        val baseline = toolWaitBaseline()
        speak(text)
        try {
            waitForVoiceResponseWithTool(toolName, baseline, timeoutMillis)
        } catch (_: IllegalStateException) {
            speak(text)
            waitForVoiceResponseWithTool(toolName, toolWaitBaseline(), timeoutMillis)
        }
    }

    private data class ToolWaitBaseline(
        val youTurns: Int,
        val grokTurns: Int,
        val toolLabel: String?,
    )

    private fun toolWaitBaseline(): ToolWaitBaseline {
        val turns = conversationTurnsIncludingLive()
        return ToolWaitBaseline(
            youTurns = turns.count { it == "you" },
            grokTurns = turns.count { it == "grok" },
            toolLabel = lastToolLabel(),
        )
    }

    fun waitForConnectionStarted(timeoutMillis: Long = 15_000) {
        waitUntil(timeoutMillis, "Timed out waiting for voice connection to start. ${diagnostics()}") {
            val current = status()
            !current.equals("Disconnected", ignoreCase = true) &&
                !current.contains("Tap Connect to grant", ignoreCase = true)
        }
    }

    fun waitForVoiceReady(timeoutMillis: Long = 120_000) {
        waitUntil(timeoutMillis, "Timed out waiting for voice session ready. ${diagnostics()}") {
            when (val current = status()) {
                "Disconnected" ->
                    error("Voice session disconnected before ready. ${diagnostics()}")
                "Waiting for conversation…",
                "Configuring session…",
                -> false
                else -> isReadyToListen(current)
            }
        }
    }

    fun waitForVoiceResponseComplete(timeoutMillis: Long = 90_000) {
        var sawActivity = false
        waitUntil(timeoutMillis, "Timed out waiting for voice response audio. ${diagnostics()}") {
            val current = status()
            if (isResponseActivity(current)) {
                sawActivity = true
            }
            sawActivity && isReadyToListen(current)
        }
    }

    private fun waitForVoiceResponseWithTool(
        toolName: String,
        baseline: ToolWaitBaseline,
        timeoutMillis: Long,
    ) {
        waitUntil(
            timeoutMillis,
            "Timed out waiting for voice response with tool '$toolName'. ${diagnostics()}",
        ) {
            val turns = conversationTurnsIncludingLive()
            if (turns.count { it == "you" } <= baseline.youTurns) return@waitUntil false
            val current = status()
            if (!isReadyToListen(current)) return@waitUntil false
            if (lastToolLabel() != toolName) return@waitUntil false
            current.contains("Running $toolName", ignoreCase = true) ||
                turns.count { it == "grok" } > baseline.grokTurns ||
                baseline.toolLabel != toolName
        }
    }

    fun waitForItemsListScreen(timeoutMillis: Long = 90_000) {
        if (device.wait(Until.findObject(By.desc("item-list-screen")), timeoutMillis) != null) return
        checkNotNull(device.wait(Until.findObject(By.text("Items")), 5_000)) {
            "Timed out waiting for items list. ${diagnostics()}"
        }
    }

    fun waitForItemDetailScreen(itemTitle: String, timeoutMillis: Long = 90_000) {
        checkNotNull(device.wait(Until.findObject(By.text(itemTitle)), timeoutMillis)) {
            "Timed out waiting for item detail '$itemTitle'. ${diagnostics()}"
        }
    }

    fun waitForListItemVisible(itemTitle: String, timeoutMillis: Long = 30_000) {
        checkNotNull(device.wait(Until.findObject(By.text(itemTitle)), timeoutMillis)) {
            "Timed out waiting for list row '$itemTitle'. ${diagnostics()}"
        }
    }

    fun waitForItemNotFoundScreen(timeoutMillis: Long = 90_000) {
        checkNotNull(device.wait(Until.findObject(By.text("Item not found")), timeoutMillis)) {
            "Timed out waiting for item-not-found screen. ${diagnostics()}"
        }
    }

    fun waitForHomeScreen(timeoutMillis: Long = 90_000) {
        checkNotNull(device.wait(Until.findObject(By.text("Roborazzi Demo")), timeoutMillis)) {
            "Timed out waiting for home screen. ${diagnostics()}"
        }
    }

    fun assertConnectedVoiceChromeVisible() {
        waitUntil(10_000, "Expected mic level UI while connected. ${diagnostics()}") {
            micLevelUiVisible()
        }
    }

    private fun micLevelUiVisible(): Boolean {
        val visible = visibleUiText()
        return device.findObject(By.desc("voice-mic-level")) != null ||
            device.findObject(By.text("Mic level")) != null ||
            device.findObject(By.text("SIG")) != null ||
            device.findObject(By.descContains("audio-level-")) != null ||
            visible.contains("Mic level", ignoreCase = true) ||
            visible.contains("SIG")
    }

    fun assertLastToolWas(toolName: String) {
        waitUntil(5_000, "Expected last tool '$toolName'. ${diagnostics()}") {
            lastToolLabel() == toolName
        }
    }

    fun conversationTurns(): List<String> = conversationTurnsIncludingLive()

    fun assertGreetingTurnIfPresent(timeoutMillis: Long = 10_000) {
        waitUntil(timeoutMillis, "Expected optional Grok greeting turn. ${diagnostics()}") {
            val turns = transcriptSummary()
            turns.isEmpty() || turns.first() == "grok"
        }
    }

    fun assertExchangeTurns(timeoutMillis: Long = 20_000) {
        waitUntil(timeoutMillis, "Expected valid user exchange turn(s). ${diagnostics()}") {
            val turns = conversationTurnsIncludingLive()
            if (!isValidConversationTurnOrder(turns)) return@waitUntil false
            if (hasLiveGrokTurn()) return@waitUntil false
            if (hasEchoTurnAfterGrok(turns)) return@waitUntil false
            when (turns.lastOrNull()) {
                "grok" -> turns.count { it == "you" } >= 1
                "you" -> isReadyToListen(status())
                else -> false
            }
        }
    }

    fun assertConversationTurnCounts(
        minYou: Int,
        minGrok: Int,
        timeoutMillis: Long = 5_000,
    ) {
        waitUntil(timeoutMillis, "Expected turn counts you>=$minYou grok>=$minGrok. ${diagnostics()}") {
            val turns = conversationTurnsIncludingLive()
            turns.count { it == "you" } >= minYou && turns.count { it == "grok" } >= minGrok
        }
    }

    fun assertValidConversationTurns(timeoutMillis: Long = 5_000) {
        waitUntil(timeoutMillis, "Conversation turns were not in a valid order. ${diagnostics()}") {
            isValidConversationTurnOrder(conversationTurnsIncludingLive())
        }
    }

    fun waitUntilDisconnected(timeoutMillis: Long = 15_000) {
        waitUntil(timeoutMillis, "Timed out waiting for disconnected state. ${diagnostics()}") {
            status().equals("Disconnected", ignoreCase = true)
        }
    }

    private fun tapConnectSwitch() {
        val switch = device.wait(Until.findObject(By.desc("voice-connect-switch")), 10_000)
            ?: device.wait(Until.findObject(By.text("Connect")), 10_000)
        checkNotNull(switch) { "Connect switch was not found. ${diagnostics()}" }
        switch.click()
    }

    private fun waitUntil(
        timeoutMillis: Long,
        onTimeout: String,
        predicate: () -> Boolean,
    ) {
        val deadline = System.currentTimeMillis() + timeoutMillis
        while (System.currentTimeMillis() < deadline) {
            if (predicate()) return
            Thread.sleep(POLL_MS)
        }
        error(onTimeout)
    }

    private fun status(): String {
        val fromSemantics = readSafely("") {
            device.findObjects(By.descContains("voice-status-"))
                .mapNotNull { node ->
                    node.contentDescription
                        ?.removePrefix("voice-status-")
                        ?.takeIf { it.isNotBlank() }
                }
                .firstOrNull()
                .orEmpty()
        }
        if (fromSemantics.isNotEmpty()) return fromSemantics
        val visible = visibleUiText()
        return STATUS_MARKERS.firstOrNull { marker -> visible.contains(marker, ignoreCase = true) }
            .orEmpty()
    }

    private fun isResponseActivity(status: String): Boolean =
        status.contains("Grok is responding", ignoreCase = true) ||
            status.contains("Running ", ignoreCase = true) ||
            status.contains("Processing", ignoreCase = true)

    private fun isReadyToListen(status: String): Boolean =
        status.contains("Listening — ask a question", ignoreCase = true) ||
            status.equals("Listening", ignoreCase = true) ||
            status.contains("Preparing microphone", ignoreCase = true)

    private fun conversationTurnsIncludingLive(): List<String> = transcriptSummary()

    private fun transcriptSummary(): List<String> = readSafely(emptyList()) {
        val fromSummary = device.findObjects(By.descContains("voice-transcript-summary-"))
            .mapNotNull { node ->
                node.contentDescription
                    ?.removePrefix("voice-transcript-summary-")
                    ?.takeIf { it.isNotBlank() }
            }
            .firstOrNull()
        if (!fromSummary.isNullOrEmpty()) {
            return@readSafely fromSummary.split(',').filter { it == "you" || it == "grok" }
        }
        indexedTranscriptTurns()
    }

    private fun indexedTranscriptTurns(): List<String> =
        device.findObjects(By.descContains("voice-transcript-"))
            .mapNotNull { node -> parseIndexedTranscriptTurn(node.contentDescription) }
            .sortedBy { it.first }
            .map { it.second }

    private fun hasLiveGrokTurn(): Boolean = isResponseActivity(status())

    private fun isValidConversationTurnOrder(turns: List<String>): Boolean {
        if (turns.isEmpty()) return false
        var index = 0
        while (index < turns.size && turns[index] == "grok") {
            index++
        }
        while (index < turns.size) {
            if (turns[index] != "you") return false
            index++
            if (index < turns.size && turns[index] == "grok") {
                index++
            }
        }
        return true
    }

    /** Echo shows up as a stray user turn immediately after Grok with no new exchange completing. */
    private fun parseIndexedTranscriptTurn(description: String?): Pair<Int, String>? {
        if (description == null) return null
        val match = INDEXED_TRANSCRIPT_REGEX.matchEntire(description) ?: return null
        return match.groupValues[1].toInt() to match.groupValues[2]
    }

    private fun hasEchoTurnAfterGrok(turns: List<String>): Boolean {
        if (turns.size < 2) return false
        if (turns.last() != "you") return false
        return turns[turns.size - 2] == "grok" && isReadyToListen(status())
    }

    private fun lastToolLabel(): String? = readSafely(null) {
        device.findObjects(By.descContains("voice-last-tool-"))
            .mapNotNull { node ->
                node.contentDescription
                    ?.removePrefix("voice-last-tool-")
                    ?.takeIf { it.isNotBlank() }
            }
            .firstOrNull()
    }

    private fun visibleUiText(): String = readSafely("") {
        val pkg = context.packageName
        buildList<String> {
            device.findObjects(By.pkg(pkg)).forEach { node ->
                node.text?.takeIf { it.isNotBlank() }?.let(::add)
                node.contentDescription
                    ?.removePrefix("voice-status-")
                    ?.removePrefix("voice-last-tool-")
                    ?.takeIf { it.isNotBlank() }
                    ?.let(::add)
            }
        }.distinct().joinToString(separator = " | ")
    }

    private fun diagnostics(): String =
        "status=[${status()}] turns=[${conversationTurnsIncludingLive().joinToString()}] " +
            "visibleText=[${visibleUiText()}]"

    private fun <T> readSafely(default: T, block: () -> T): T =
        try {
            block()
        } catch (_: StaleObjectException) {
            default
        }

    companion object {
        private const val POLL_MS = 500L
        private val INDEXED_TRANSCRIPT_REGEX = Regex("^voice-transcript-(\\d+)-(you|grok)$")

        private val STATUS_MARKERS = listOf(
            "Listening — ask a question",
            "Preparing microphone…",
            "Grok is responding…",
            "Grok is greeting you…",
            "Running navigate_to_screen…",
            "Running navigate_back…",
            "Running open_list_item…",
            "Running describe_screen…",
            "Running web_search…",
            "Processing…",
            "You are speaking",
            "Waiting for conversation…",
            "Configuring session…",
            "Disconnected",
            "Listening",
            "Error",
        )

        fun create(): VoiceAppTestRobot {
            val instrumentation = InstrumentationRegistry.getInstrumentation()
            val device = UiDevice.getInstance(instrumentation)
            device.waitForIdle(3_000)
            val context = instrumentation.targetContext
            TestSpeechAnnouncer.warmUp(context)
            return VoiceAppTestRobot(device, context)
        }
    }
}