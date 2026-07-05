package com.example.roborazzidemo.viewmodel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ItemListScrollControllerTest {
    @Test
    fun scrollToOneBasedIndex_returnsMessage() {
        val controller = ItemListScrollController()

        val result = controller.scrollToOneBasedIndex(5)

        assertEquals("Scrolled to and highlighted item 5.", result)
    }

    @Test
    fun scrollToOneBasedIndex_highlightsZeroBasedIndex() {
        val controller = ItemListScrollController()

        controller.scrollToOneBasedIndex(10)

        assertEquals(9, controller.highlightedIndex.value)
    }

    @Test
    fun clearHighlight_clearsSelection() {
        val controller = ItemListScrollController()
        controller.scrollToOneBasedIndex(3)

        controller.clearHighlight()

        assertNull(controller.highlightedIndex.value)
    }

    @Test
    fun scrollToOneBasedIndex_rejectsInvalidIndex() {
        val controller = ItemListScrollController()

        val result = controller.scrollToOneBasedIndex(0)

        assertTrue(result.contains("at least 1"))
    }
}