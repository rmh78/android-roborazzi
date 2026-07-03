package com.example.roborazzidemo.viewmodel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ItemListScrollControllerTest {
    @Test
    fun scrollToOneBasedIndex_returnsMessage() {
        val controller = ItemListScrollController()

        val result = controller.scrollToOneBasedIndex(5)

        assertEquals("Scrolled to item 5.", result)
    }

    @Test
    fun scrollToOneBasedIndex_rejectsInvalidIndex() {
        val controller = ItemListScrollController()

        val result = controller.scrollToOneBasedIndex(0)

        assertTrue(result.contains("at least 1"))
    }
}