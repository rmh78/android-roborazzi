package com.example.roborazzidemo.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun LazyListScrollbar(
    listState: LazyListState,
    modifier: Modifier = Modifier,
    thickness: Dp = 4.dp,
) {
    val indicatorState = listState.scrollIndicatorState ?: return

    val thumbMetrics by remember(indicatorState) {
        derivedStateOf {
            val contentSize = indicatorState.contentSize
            val viewportSize = indicatorState.viewportSize
            val scrollOffset = indicatorState.scrollOffset

            if (
                contentSize == Int.MAX_VALUE ||
                viewportSize == Int.MAX_VALUE ||
                contentSize <= viewportSize
            ) {
                return@derivedStateOf null
            }

            val scrollRange = (contentSize - viewportSize).coerceAtLeast(1)
            val thumbFraction = viewportSize.toFloat() / contentSize.toFloat()
            ThumbMetrics(
                thumbFraction = thumbFraction,
                thumbOffsetFraction = scrollOffset.toFloat() / scrollRange.toFloat(),
            )
        }
    }

    val metrics = thumbMetrics ?: return

    BoxWithConstraints(
        modifier = modifier
            .fillMaxHeight()
            .width(thickness + 8.dp)
            .padding(end = 2.dp),
    ) {
        val trackHeight = maxHeight
        val thumbHeight = (trackHeight * metrics.thumbFraction).coerceAtLeast(thickness * 4)
        val maxThumbTravel = trackHeight - thumbHeight
        val thumbOffset = maxThumbTravel * metrics.thumbOffsetFraction

        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .width(thickness)
                .fillMaxHeight()
                .clip(RoundedCornerShape(thickness / 2))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
        )

        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(y = thumbOffset)
                .width(thickness)
                .height(thumbHeight)
                .clip(RoundedCornerShape(thickness / 2))
                .background(
                    MaterialTheme.colorScheme.primary,
                ),
        )
    }
}

private data class ThumbMetrics(
    val thumbFraction: Float,
    val thumbOffsetFraction: Float,
)