package com.example.roborazzidemo.ui.futuristic

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

/** Chamfered sci-fi panel — cuts the top-right and bottom-left corners. */
class ChamferedPanelShape(private val cut: Dp = 14.dp) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        val cutPx = with(density) { cut.toPx() }
        val path = Path().apply {
            moveTo(0f, 0f)
            lineTo(size.width - cutPx, 0f)
            lineTo(size.width, cutPx)
            lineTo(size.width, size.height)
            lineTo(cutPx, size.height)
            lineTo(0f, size.height - cutPx)
            close()
        }
        return Outline.Generic(path)
    }
}