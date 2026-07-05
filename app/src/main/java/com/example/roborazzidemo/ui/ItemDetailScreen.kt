package com.example.roborazzidemo.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.roborazzidemo.R
import com.example.roborazzidemo.model.Item
import com.example.roborazzidemo.model.ItemIconType
import com.example.roborazzidemo.model.sampleItems
import com.example.roborazzidemo.theme.RoborazziDemoTheme
import com.example.roborazzidemo.ui.futuristic.HoloPanel
import com.example.roborazzidemo.ui.futuristic.NexusSectionHeader

@Composable
fun ItemDetailScreen(
    item: Item,
    onBack: () -> Unit,
) {
    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            AppTopBar(
                title = item.title,
                onBack = onBack,
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            HoloPanel(
                accent = MaterialTheme.colorScheme.tertiary,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    NexusSectionHeader(
                        title = "Data Node",
                        subtitle = "Encrypted payload · read-only",
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = item.iconType.asImageVector(),
                            contentDescription = stringResource(item.iconType.contentDescriptionRes),
                            modifier = Modifier.size(item.iconSizeDp.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text = item.title,
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    Text(
                        text = stringResource(item.descriptionRes),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ItemDetailScreenPreview() {
    RoborazziDemoTheme(darkTheme = true) {
        ItemDetailScreen(
            item = sampleItems().first(),
            onBack = {},
        )
    }
}

@Preview(showBackground = true, name = "Long description")
@Composable
private fun ItemDetailScreenLongPreview() {
    RoborazziDemoTheme(darkTheme = true) {
        ItemDetailScreen(
            item = Item(
                id = 3,
                title = "Item 3",
                descriptionRes = R.string.item_desc_long,
                iconType = ItemIconType.Flight,
                iconSizeDp = 48,
            ),
            onBack = {},
        )
    }
}