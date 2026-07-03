package com.example.roborazzidemo.semantics

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect

@Composable
fun TrackScreenContent(
    route: String,
    elements: List<ScreenElement>,
) {
    DisposableEffect(route, elements) {
        ScreenContentRegistry.update(route, elements)
        onDispose { }
    }
}