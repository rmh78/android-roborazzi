package com.example.roborazzidemo

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

        captureScreenshot(GoldenImages.ITEM_DETAIL_LONG)
    }
}