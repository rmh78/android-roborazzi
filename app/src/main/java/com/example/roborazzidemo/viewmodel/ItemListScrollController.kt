package com.example.roborazzidemo.viewmodel

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

class ItemListScrollController {
    private val _scrollToIndex = MutableSharedFlow<Int>(extraBufferCapacity = 1)
    val scrollToIndex: SharedFlow<Int> = _scrollToIndex.asSharedFlow()

    private val _highlightedIndex = MutableStateFlow<Int?>(null)
    val highlightedIndex: StateFlow<Int?> = _highlightedIndex.asStateFlow()

    fun scrollToOneBasedIndex(oneBasedIndex: Int): String {
        if (oneBasedIndex < 1) {
            return "Item index must be at least 1."
        }
        val zeroBased = oneBasedIndex - 1
        _highlightedIndex.value = zeroBased
        _scrollToIndex.tryEmit(zeroBased)
        return "Scrolled to and highlighted item $oneBasedIndex."
    }

    fun clearHighlight() {
        _highlightedIndex.value = null
    }
}