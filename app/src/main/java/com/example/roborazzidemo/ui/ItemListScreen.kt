package com.example.roborazzidemo.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.roborazzidemo.R
import com.example.roborazzidemo.model.Item
import com.example.roborazzidemo.model.sampleItems
import com.example.roborazzidemo.theme.LcarsBlue
import com.example.roborazzidemo.theme.LcarsBlueDeep
import com.example.roborazzidemo.theme.LcarsOrange
import com.example.roborazzidemo.theme.RoborazziDemoTheme
import com.example.roborazzidemo.ui.futuristic.LcarsBarShape
import com.example.roborazzidemo.viewmodel.ItemListScrollController

@Composable
fun ItemListScreen(
    items: List<Item>,
    onItemClick: (Item) -> Unit,
    onBack: () -> Unit,
    scrollController: ItemListScrollController? = null,
    reserveVoiceOverlayInset: Boolean = true,
) {
    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            AppTopBar(
                title = stringResource(R.string.items_title),
                onBack = onBack,
            )
        },
    ) { innerPadding ->
        val listState = rememberLazyListState()
        val bottomInset = if (reserveVoiceOverlayInset) {
            VoiceOverlayMetrics.DisconnectedListBottomInset
        } else {
            0.dp
        }

        LaunchedEffect(scrollController) {
            scrollController?.scrollToIndex?.collect { index ->
                listState.animateScrollToItem(index)
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .semantics { contentDescription = "item-list-screen" }
                    .testTag("item_list"),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(
                    start = 12.dp,
                    end = 12.dp,
                    top = 8.dp,
                    bottom = bottomInset + 8.dp,
                ),
            ) {
                itemsIndexed(items, key = { _, item -> item.id }) { index, item ->
                    ItemRow(
                        item = item,
                        nodeIndex = index + 1,
                        onClick = { onItemClick(item) },
                    )
                }
            }

            ListEdgeFadeOverlay()

            LazyListScrollbar(
                listState = listState,
                modifier = Modifier.align(Alignment.CenterEnd),
            )
        }
    }
}

@Composable
private fun ItemRow(
    item: Item,
    nodeIndex: Int,
    onClick: () -> Unit,
) {
    val indexAccent = when (nodeIndex % 4) {
        1 -> LcarsOrange
        2 -> LcarsBlue
        3 -> LcarsBlueDeep
        else -> Color(0xFF77BBFF)
    }
    val indexBackground = indexAccent.copy(alpha = 0.28f)
    val shape = LcarsBarShape()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(end = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            modifier = Modifier
                .width(56.dp)
                .height(72.dp)
                .background(indexBackground),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "%02d".format(nodeIndex),
                style = MaterialTheme.typography.labelMedium,
                color = indexAccent,
                fontWeight = FontWeight.Bold,
            )
        }
        Icon(
            imageVector = item.iconType.asImageVector(),
            contentDescription = stringResource(item.iconType.contentDescriptionRes),
            modifier = Modifier.size(item.iconSizeDp.dp),
            tint = indexAccent,
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = stringResource(item.descriptionRes),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
            )
        }
    }
}

@Preview(showBackground = true, heightDp = 640)
@Composable
private fun ItemListScreenPreview() {
    RoborazziDemoTheme(darkTheme = true) {
        ItemListScreen(
            items = sampleItems(),
            onItemClick = {},
            onBack = {},
            reserveVoiceOverlayInset = false,
        )
    }
}