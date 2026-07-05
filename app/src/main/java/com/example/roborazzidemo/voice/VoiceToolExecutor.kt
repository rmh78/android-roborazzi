package com.example.roborazzidemo.voice

import com.example.roborazzidemo.navigation.VoiceNavigationHandler
import com.example.roborazzidemo.semantics.ScreenContentProvider
import com.example.roborazzidemo.viewmodel.ItemListScrollController
import org.json.JSONObject

class VoiceToolExecutor(
    private val navigationHandler: VoiceNavigationHandler,
    private val scrollController: ItemListScrollController,
) {
    fun execute(name: String, arguments: JSONObject): String {
        VoiceLog.i("Tool", "Executing $name with $arguments")
        val output = when (name) {
            "navigate_to_screen" -> {
                val destination = arguments.getString("destination")
                val itemId = if (arguments.has("item_id") && !arguments.isNull("item_id")) {
                    arguments.getInt("item_id")
                } else {
                    null
                }
                navigationHandler.navigateToScreen(destination, itemId).asToolOutput()
            }
            "navigate_back" -> navigationHandler.navigateBack().asToolOutput()
            "open_list_item" -> {
                val index = arguments.getInt("index")
                scrollController.scrollToOneBasedIndex(index)
            }
            "describe_screen" -> ScreenContentProvider.dumpScreenJson()
            "web_search", "x_search", "file_search" -> {
                VoiceLog.w("Tool", "Unexpected client dispatch for server-executed tool: $name")
                """{"status":"completed"}"""
            }
            else -> "Unknown tool: $name"
        }
        VoiceLog.i("Tool", "$name → ${output.take(200)}")
        return output
    }
}