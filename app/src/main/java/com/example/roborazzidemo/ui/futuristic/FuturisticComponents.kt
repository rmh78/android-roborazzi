package com.example.roborazzidemo.ui.futuristic

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.roborazzidemo.theme.LcarsBlue
import com.example.roborazzidemo.theme.LcarsBlueDeep
import com.example.roborazzidemo.theme.LcarsOrange

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