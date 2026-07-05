package com.example.roborazzidemo.ui.futuristic

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import com.example.roborazzidemo.theme.LcarsBlue
import com.example.roborazzidemo.theme.LcarsBlueDeep
import com.example.roborazzidemo.theme.LcarsOrange
import androidx.compose.ui.unit.dp

@Composable
fun FuturisticBackground(modifier: Modifier = Modifier) {
    val dark = isSystemInDarkTheme()
    val bg = MaterialTheme.colorScheme.background

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(bg),
    ) {
        Row(
            modifier = Modifier.fillMaxHeight(),
        ) {
            Box(
                modifier = Modifier
                    .width(8.dp)
                    .fillMaxHeight()
                    .background(LcarsOrange),
            )
            Box(
                modifier = Modifier
                    .width(6.dp)
                    .fillMaxHeight()
                    .background(LcarsBlue.copy(alpha = if (dark) 0.85f else 0.7f)),
            )
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .background(LcarsBlueDeep.copy(alpha = 0.8f)),
            )
        }
        if (dark) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawCircle(
                    color = LcarsBlue.copy(alpha = 0.06f),
                    radius = size.width * 0.4f,
                    center = Offset(size.width * 0.9f, size.height * 0.12f),
                )
                drawCircle(
                    color = LcarsBlueDeep.copy(alpha = 0.05f),
                    radius = size.width * 0.3f,
                    center = Offset(size.width * 0.15f, size.height * 0.88f),
                )
            }
        }
    }
}