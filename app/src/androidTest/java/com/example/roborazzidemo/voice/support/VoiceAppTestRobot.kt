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
    fun assertAppVisible(timeoutMillis: Long = 90_000) = waitForAppShellVisible(timeoutMillis)

    private fun waitForAppShellVisible(timeoutMillis: Long) {
        // ActivityScenarioRule already started MainActivity; avoid an immediate CLEAR_TOP
        // relaunch that can race Compose rendering on cold CI emulator starts.
        if (pollForAppShellVisible(timeoutMillis)) return

        repeat(3) { attempt ->
            VoiceE2ELog.detail("overlay not visible — relaunch attempt ${attempt + 1}")
            ensureAppInForeground()
            if (pollForAppLaunched(30_000) && pollForAppShellVisible(90_000)) return
        }
        error("Voice Assistant overlay was not visible after relaunch. ${diagnostics()}")
    }

    private fun pollForAppShellVisible(timeoutMillis: Long): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMillis
        while (System.currentTimeMillis() < deadline) {
            if (isVoiceOverlayVisible()) return true
            Thread.sleep(POLL_MS)
        }
        return isVoiceOverlayVisible()
    }

    private fun pollForAppLaunched(timeoutMillis: Long): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMillis
        while (System.currentTimeMillis() < deadline) {
            if (isAppContentVisible()) return true
            Thread.sleep(POLL_MS)
        }
        return isAppContentVisible()
    }

    private fun ensureAppInForeground() {
        if (!device.isScreenOn) {
            device.wakeUp()
            device.waitForIdle(500)
        }
        device.pressHome()
        device.waitForIdle(1_000)
        val component = "${context.packageName}/.MainActivity"
        device.executeShellCommand("am start -W -n $component")
        device.wait(Until.hasObject(By.pkg(context.packageName)), 15_000)
        device.waitForIdle(5_000)
    }

    private fun isAppContentVisible(): Boolean =
        device.currentPackageName == context.packageName &&
            (
                device.findObject(By.text("Roborazzi Demo")) != null ||
                    device.findObject(By.text("Items")) != null ||
                    device.findObject(By.desc("item-list-screen")) != null
                )

    private fun isVoiceOverlayVisible(): Boolean =
        device.findObject(By.desc("voice-assistant-overlay")) != null ||
            device.findObject(By.desc("voice-connect-switch")) != null ||
            device.findObject(By.text("Voice Assistant")) != null ||
            device.findObject(By.text("VOICE INTERFACE")) != null

    private fun isAppShellVisible(): Boolean = isVoiceOverlayVisible()

    fun assertHomeScreenVisible() = waitForHomeScreen(timeoutMillis = 5_000)

    fun connect() {
        tapConnectSwitch()
        waitForConnectionStarted()
    }

    fun disconnect() = tapConnectSwitch()

    fun speak(text: String) = TestSpeechAnnouncer.speak(context, text)

    fun speakAndWaitForResponse(text: String, timeoutMillis: Long = 120_000) {
        waitForReadyToSpeak()
        VoiceE2ELog.step("speak (response): \"$text\"")
        val baseline = toolWaitBaseline()
        speak(text)
        waitForUserTurnRegistered(baseline, timeoutMillis / 4)
        waitForAssistantSpeechComplete(timeoutMillis, baseline)
        VoiceE2ELog.detail("response complete: status=[${status()}] turns=[${conversationTurnsIncludingLive().joinToString()}]")
    }

    fun speakAndWaitForTool(text: String, toolName: String, timeoutMillis: Long = 60_000) {
        waitForReadyToSpeak()
        VoiceE2ELog.step("speak (tool=$toolName): \"$text\"")
        val baseline = toolWaitBaseline()
        speak(text)
        waitForUserTurnRegistered(baseline, timeoutMillis / 4)
        waitForToolInvocation(toolName, baseline, timeoutMillis)
        if (toolName !in TOOLS_SKIP_SPEECH_COMPLETE) {
            // Tool invocation is bounded by timeoutMillis; Grok may keep streaming audio
            // well after the tool runs, especially late in a long CI session.
            waitForAssistantSpeechComplete(SPEECH_COMPLETE_TIMEOUT_MILLIS, baseline, activityAlreadySeen = true)
        }
        VoiceE2ELog.detail("tool complete: tool=$toolName status=[${status()}] turns=[${conversationTurnsIncludingLive().joinToString()}]")
    }

    fun waitForReadyToSpeak(timeoutMillis: Long = 120_000) {
        waitUntil(timeoutMillis, "Timed out waiting for voice assistant ready to accept speech. ${diagnostics()}") {
            isReadyToAcceptSpeech()
        }
        device.waitForIdle(1_000)
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

    private fun isReadyToAcceptSpeech(): Boolean {
        val current = status()
        // Trust the voice status line over turn-indicator nodes — CI UiAutomator
        // intermittently retains stale turn-active markers with an empty tree.
        return isReadyToListen(current) && !isAssistantSpeaking(current)
    }

    private fun waitForUserTurnRegistered(
        baseline: ToolWaitBaseline,
        timeoutMillis: Long,
    ) {
        waitUntil(
            timeoutMillis,
            "Timed out waiting for spoken prompt to register. ${diagnostics()}",
        ) {
            conversationTurnsIncludingLive().count { it == "you" } > baseline.youTurns ||
                device.findObject(By.desc("voice-transcript-live-you")) != null
        }
    }

    private fun waitForToolInvocation(
        toolName: String,
        baseline: ToolWaitBaseline,
        timeoutMillis: Long,
    ) {
        waitUntil(
            timeoutMillis,
            "Timed out waiting for tool '$toolName' to run. ${diagnostics()}",
        ) {
            val turns = conversationTurnsIncludingLive()
            turns.count { it == "you" } > baseline.youTurns && lastToolLabel() == toolName
        }
    }

    private fun waitForAssistantSpeechComplete(
        timeoutMillis: Long,
        baseline: ToolWaitBaseline,
        activityAlreadySeen: Boolean = false,
    ) {
        var sawActivity = activityAlreadySeen
        waitUntil(
            timeoutMillis,
            "Timed out waiting for assistant to finish speaking. ${diagnostics()}",
        ) {
            val current = status()
            val turns = conversationTurnsIncludingLive()
            val youTurns = turns.count { it == "you" }
            val grokTurns = turns.count { it == "grok" }
            if (isAssistantTurnActive()) {
                sawActivity = true
            }
            if (isAssistantSpeaking(current)) {
                sawActivity = true
            }
            if (youTurns > baseline.youTurns) {
                sawActivity = true
            }
            if (grokTurns > baseline.grokTurns) {
                sawActivity = true
            }
            if (lastToolLabel() != baseline.toolLabel && lastToolLabel() != null) {
                sawActivity = true
            }
            if (!device.findObjects(By.descContains("voice-transcript-live-grok")).isNullOrEmpty()) {
                sawActivity = true
            }
            sawActivity && isAssistantTurnFinished(current)
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

    fun waitForListItemSelected(itemIndex: Int, timeoutMillis: Long = 30_000) {
        val selectedDesc = "item-row-selected-$itemIndex"
        checkNotNull(
            device.wait(Until.findObject(By.desc(selectedDesc)), timeoutMillis)
                ?: device.wait(Until.findObject(By.descContains("item-row-selected-")), timeoutMillis),
        ) {
            "Timed out waiting for selected list row index $itemIndex. ${diagnostics()}"
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
            device.findObject(By.descContains("voice-sig-level-")) != null ||
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

    fun assertGreetingTurnIfPresent(timeoutMillis: Long = 120_000) {
        waitUntil(timeoutMillis, "Expected optional Grok greeting turn. ${diagnostics()}") {
            val turns = transcriptSummary()
            turns.isEmpty() || turns.first() == "grok"
        }
        if (!isReadyToListen(status())) {
            waitForAssistantSpeechComplete(
                timeoutMillis = timeoutMillis,
                baseline = toolWaitBaseline(),
                activityAlreadySeen = transcriptSummary().isNotEmpty(),
            )
        }
    }

    fun assertExchangeTurns(timeoutMillis: Long = 90_000) {
        val deadline = System.currentTimeMillis() + timeoutMillis
        while (System.currentTimeMillis() < deadline) {
            if (exchangeTurnsReady()) return
            Thread.sleep(POLL_MS)
        }
        if (exchangeTurnsRecorded()) {
            VoiceE2ELog.detail(
                "exchange turns recorded; proceeding without full session idle (${diagnostics()})",
            )
            waitForListenStatus(60_000)
            return
        }
        error("Expected valid user exchange turn(s). ${diagnostics()}")
    }

    private fun exchangeTurnsRecorded(): Boolean {
        val turns = conversationTurnsIncludingLive()
        if (!isValidConversationTurnOrder(turns)) return false
        if (hasEchoTurnAfterGrok(turns)) return false
        return turns.count { it == "you" } >= 1 &&
            (turns.lastOrNull() == "grok" || turns.lastOrNull() == "you")
    }

    private fun exchangeTurnsReady(): Boolean =
        exchangeTurnsRecorded() && isReadyToAcceptSpeech()

    private fun waitForListenStatus(timeoutMillis: Long) {
        waitUntil(timeoutMillis, "Timed out waiting for listen status. ${diagnostics()}") {
            val current = status()
            isReadyToListen(current) && !isAssistantSpeaking(current)
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

    fun waitUntilDisconnected(timeoutMillis: Long = 30_000) {
        waitUntil(timeoutMillis, "Timed out waiting for disconnected state. ${diagnostics()}") {
            isDisconnected()
        }
    }

    private fun isDisconnected(): Boolean {
        val current = status()
        if (current.equals("Disconnected", ignoreCase = true)) return true
        if (current.contains("Tap Connect to grant", ignoreCase = true)) return true
        val checkable = device.findObject(By.checkable(true))
        if (checkable != null && !checkable.isChecked) return true
        return !micLevelUiVisible() &&
            !current.contains("Listening", ignoreCase = true) &&
            !isResponseActivity(current)
    }

    private fun tapConnectSwitch() {
        val switch = device.wait(Until.findObject(By.desc("voice-connect-switch")), 10_000)
            ?: device.wait(Until.findObject(By.checkable(true)), 10_000)
            ?: device.wait(Until.findObject(By.text("Connect")), 10_000)
        checkNotNull(switch) { "Connect switch was not found. ${diagnostics()}" }
        val bounds = switch.visibleBounds
        device.click(bounds.centerX(), bounds.centerY())
        device.waitForIdle(3_000)
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

    private fun isAssistantTurnActive(): Boolean =
        device.findObject(By.desc("voice-assistant-turn-active")) != null

    private fun isAssistantTurnIdle(): Boolean =
        device.findObject(By.desc("voice-assistant-turn-idle")) != null

    private fun isAssistantTurnFinished(status: String): Boolean =
        when {
            isAssistantTurnIdle() -> isReadyToListen(status)
            isAssistantTurnActive() -> false
            else -> isReadyToListen(status) && !isAssistantSpeaking(status)
        }

    private fun isAssistantSpeaking(status: String): Boolean =
        status.contains("Grok is responding", ignoreCase = true) ||
            status.contains("Grok is greeting", ignoreCase = true) ||
            status.contains("Running ", ignoreCase = true) ||
            status.contains("Running tool", ignoreCase = true) ||
            status.contains("Processing", ignoreCase = true)

    private fun isResponseActivity(status: String): Boolean = isAssistantSpeaking(status)

    private fun isReadyToListen(status: String): Boolean =
        status.contains("Listening — ask a question", ignoreCase = true) ||
            status.equals("Listening", ignoreCase = true) ||
            status.contains("Preparing microphone", ignoreCase = true) ||
            (isAssistantTurnIdle() && device.findObject(By.text("AI RDY")) != null)

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
        "package=[${device.currentPackageName}] status=[${status()}] " +
            "turns=[${conversationTurnsIncludingLive().joinToString()}] " +
            "visibleText=[${visibleUiText()}]"

    private fun <T> readSafely(default: T, block: () -> T): T =
        try {
            block()
        } catch (_: StaleObjectException) {
            default
        }

    companion object {
        private const val POLL_MS = 500L
        private const val SPEECH_COMPLETE_TIMEOUT_MILLIS = 120_000L
        private val TOOLS_SKIP_SPEECH_COMPLETE = setOf("navigate_to_screen", "open_list_item")
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
            device.waitForIdle(5_000)
            val context = instrumentation.targetContext
            TestSpeechAnnouncer.warmUp(context)
            val robot = VoiceAppTestRobot(device, context)
            if (!robot.pollForAppLaunched(60_000)) {
                robot.ensureAppInForeground()
                check(robot.pollForAppLaunched(30_000)) {
                    "App did not reach foreground. ${robot.diagnostics()}"
                }
            }
            robot.waitForAppShellVisible(timeoutMillis = 120_000)
            VoiceE2ELog.step("voice assistant overlay visible")
            return robot
        }
    }
}