package com.example.roborazzidemo

import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToIndex
import com.example.roborazzidemo.model.sampleItems
import com.example.roborazzidemo.ui.ItemListScreen
import org.junit.Test

class ItemListScreenTest : RoborazziComposeTest() {
    @Test
    fun itemListScreen_scrollPositions() {
        val items = sampleItems()

        setThemedContent {
            ItemListScreen(
                items = items,
                onItemClick = {},
                onBack = {},
            )
        }

        captureScreenshot(GoldenImages.ITEM_LIST_SCROLL_TOP)

        composeTestRule.onNodeWithTag("item_list").performScrollToIndex(10)
        captureScreenshot(GoldenImages.ITEM_LIST_SCROLL_MIDDLE)

        composeTestRule.onNodeWithTag("item_list").performScrollToIndex(items.lastIndex)
        captureScreenshot(GoldenImages.ITEM_LIST_SCROLL_BOTTOM)
    }
}