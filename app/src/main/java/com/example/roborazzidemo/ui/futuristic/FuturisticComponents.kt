package com.example.roborazzidemo.ui.futuristic

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.roborazzidemo.theme.LcarsBlue
import com.example.roborazzidemo.theme.LcarsBlueDeep
import com.example.roborazzidemo.theme.LcarsOrange
import com.example.roborazzidemo.theme.NexusGlowCyan
import com.example.roborazzidemo.theme.NexusOverlayInset
import com.example.roborazzidemo.theme.NexusOverlayTextDim

@Composable
fun NexusStatusChip(
    label: String,
    modifier: Modifier = Modifier,
    accent: Color = LcarsOrange,
) {
    val shape = RoundedCornerShape(topStart = 14.dp, bottomStart = 14.dp, topEnd = 3.dp, bottomEnd = 3.dp)
    Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        color = Color.Black,
        fontWeight = FontWeight.Bold,
        modifier = modifier
            .clip(shape)
            .background(accent)
            .padding(horizontal = 14.dp, vertical = 6.dp),
    )
}

@Composable
fun HoloPanel(
    modifier: Modifier = Modifier,
    accent: Color = LcarsBlue,
    content: @Composable () -> Unit,
) {
    val shape = LcarsPanelShape()
    Column(modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.55f)
                .height(10.dp)
                .clip(LcarsBarShape())
                .background(accent),
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(start = 20.dp, top = 16.dp, end = 20.dp, bottom = 24.dp),
        ) {
            content()
        }
    }
}

@Composable
fun NeonPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accent: Color = LcarsOrange,
) {
    val shape = LcarsBarShape()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(shape)
            .background(accent)
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = Color.Black,
            fontWeight = FontWeight.Bold,
        )
        Box(
            modifier = Modifier
                .width(32.dp)
                .height(6.dp)
                .clip(LcarsPillShape)
                .background(Color.Black.copy(alpha = 0.25f)),
        )
    }
}

@Composable
fun NexusSectionHeader(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    accent: Color = LcarsBlue,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                modifier = Modifier
                    .width(48.dp)
                    .height(8.dp)
                    .clip(LcarsBarShape())
                    .background(accent),
            )
            Text(
                text = title.uppercase(),
                style = MaterialTheme.typography.labelMedium,
                color = accent,
                fontWeight = FontWeight.Bold,
            )
        }
        if (subtitle != null) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 58.dp),
            )
        }
    }
}

@Composable
fun SigLevelMeter(
    level: Float,
    modifier: Modifier = Modifier,
) {
    val animatedLevel by animateFloatAsState(
        targetValue = level.coerceIn(0f, 1f),
        label = "sig_level",
    )
    val segmentCount = 14
    val litSegments = (animatedLevel * segmentCount).toInt().coerceIn(0, segmentCount)
    val levelLabel = "%.4f".format(animatedLevel)
    val trackShape = LcarsBarShape()
    val trackWidth = 132.dp
    val trackHeight = 16.dp

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(1.dp),
        ) {
            Text(
                text = "SIG",
                style = MaterialTheme.typography.labelSmall,
                color = LcarsBlue,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "IN //",
                style = MaterialTheme.typography.labelSmall,
                color = NexusOverlayTextDim,
                fontFamily = FontFamily.Monospace,
            )
        }

        Box(
            modifier = Modifier
                .width(trackWidth)
                .height(trackHeight + 6.dp),
        ) {
            if (litSegments > 0) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(vertical = 1.dp)
                        .width(trackWidth * litSegments / segmentCount + 6.dp)
                        .height(trackHeight + 4.dp)
                        .clip(trackShape)
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(
                                    NexusGlowCyan,
                                    Color.Transparent,
                                ),
                            ),
                        ),
                )
            }

            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .width(trackWidth)
                    .height(trackHeight)
                    .clip(trackShape)
                    .background(NexusOverlayInset)
                    .border(1.dp, LcarsBlueDeep.copy(alpha = 0.7f), trackShape)
                    .padding(horizontal = 4.dp, vertical = 3.dp)
                    .semantics {
                        contentDescription = "voice-sig-level-$levelLabel"
                        stateDescription = "audio-level-$levelLabel"
                    }
                    .testTag("voice_mic_level_bar"),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    repeat(segmentCount) { index ->
                        val isLit = index < litSegments
                        val isPeak = isLit && index == litSegments - 1
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clip(LcarsPillShape)
                                .background(
                                    if (isLit) {
                                        Brush.verticalGradient(
                                            colors = if (isPeak) {
                                                listOf(
                                                    Color.White.copy(alpha = 0.95f),
                                                    LcarsOrange,
                                                    LcarsOrange.copy(alpha = 0.75f),
                                                )
                                            } else {
                                                listOf(
                                                    LcarsOrange.copy(alpha = 0.95f),
                                                    LcarsOrange.copy(alpha = 0.55f),
                                                )
                                            },
                                        )
                                    } else {
                                        Brush.verticalGradient(
                                            colors = listOf(
                                                Color(0xFF0C1624),
                                                Color(0xFF060C14),
                                            ),
                                        )
                                    },
                                )
                                .then(
                                    if (!isLit) {
                                        Modifier.border(
                                            width = 0.5.dp,
                                            color = LcarsBlueDeep.copy(alpha = 0.35f),
                                            shape = LcarsPillShape,
                                        )
                                    } else {
                                        Modifier
                                    },
                                ),
                        )
                    }
                }
            }

            Row(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .width(trackWidth)
                    .padding(horizontal = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                repeat(5) {
                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(2.dp)
                            .background(LcarsBlue.copy(alpha = 0.45f)),
                    )
                }
            }
        }

        Text(
            text = "%02d".format((animatedLevel * 100f).toInt()),
            style = MaterialTheme.typography.labelSmall,
            color = if (litSegments > 0) LcarsOrange else NexusOverlayTextDim,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
fun LcarsSideAccent(
    modifier: Modifier = Modifier,
    colors: List<Color> = listOf(LcarsOrange, LcarsBlue, LcarsBlueDeep),
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(12.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        colors.forEach { color ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .height(12.dp)
                    .clip(LcarsBarShape())
                    .background(color),
            )
        }
    }
}