package com.example.roborazzidemo.viewmodel

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

class ItemListScrollController {
    private val _scrollToIndex = MutableSharedFlow<Int>(extraBufferCapacity = 1)
    val scrollToIndex: SharedFlow<Int> = _scrollToIndex.asSharedFlow()

    fun scrollToOneBasedIndex(oneBasedIndex: Int): String {
        if (oneBasedIndex < 1) {
            return "Item index must be at least 1."
        }
        val zeroBased = oneBasedIndex - 1
        _scrollToIndex.tryEmit(zeroBased)
        return "Scrolled to item $oneBasedIndex."
    }
}