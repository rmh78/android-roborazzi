package com.example.roborazzidemo

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import com.example.roborazzidemo.R
import com.example.roborazzidemo.model.Item
import com.example.roborazzidemo.model.ItemIconType
import com.example.roborazzidemo.model.sampleItems
import com.example.roborazzidemo.ui.ItemDetailScreen
import org.junit.Test

class ItemDetailScreenTest : RoborazziComposeTest() {
    @Test
    fun itemDetailScreen_sampleItem() {
        val item = sampleItems().first()

        setThemedContent {
            ItemDetailScreen(
                item = item,
                onBack = {},
            )
        }

        composeTestRule.onAllNodesWithText("Item 1").assertCountEquals(2)
        composeTestRule.onNodeWithText("Short note.").assertIsDisplayed()
        captureScreenshot(GoldenImages.ITEM_DETAIL_SAMPLE)
    }

    @Test
    fun itemDetailScreen_longDescription() {
        val item = Item(
            id = 3,
            title = "Item 3",
            descriptionRes = R.string.item_desc_long,
            iconType = ItemIconType.Flight,
            iconSizeDp = 48,
        )

        setThemedContent {
            ItemDetailScreen(
                item = item,
                onBack = {},
            )
        }

        composeTestRule.onAllNodesWithText("Item 3").assertCountEquals(2)
        composeTestRule.onNodeWithText(
            "A longer description with more detail. This item includes extra context so the row grows taller and stands out when scrolling through the list alongside compact entries.",
        ).assertIsDisplayed()
        captureScreenshot(GoldenImages.ITEM_DETAIL_LONG)
    }
}