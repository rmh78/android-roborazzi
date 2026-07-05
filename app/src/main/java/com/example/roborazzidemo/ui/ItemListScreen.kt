package com.example.roborazzidemo.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect

import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.example.roborazzidemo.viewmodel.ItemListScrollController

import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.roborazzidemo.R
import com.example.roborazzidemo.model.Item
import com.example.roborazzidemo.model.sampleItems
import com.example.roborazzidemo.theme.RoborazziDemoTheme

@Composable
fun ItemListScreen(
    items: List<Item>,
    onItemClick: (Item) -> Unit,
    onBack: () -> Unit,
    scrollController: ItemListScrollController? = null,
) {
    Scaffold(
        topBar = {
            AppTopBar(
                title = stringResource(R.string.items_title),
                onBack = onBack,
            )
        },
    ) { innerPadding ->
        val listState = rememberLazyListState()

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
            ) {
                items(items, key = { it.id }) { item ->
                    ItemRow(
                        item = item,
                        onClick = { onItemClick(item) },
                    )
                    HorizontalDivider()
                }
            }

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
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            imageVector = item.iconType.asImageVector(),
            contentDescription = stringResource(item.iconType.contentDescriptionRes),
            modifier = Modifier.size(item.iconSizeDp.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = stringResource(item.descriptionRes),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Preview(showBackground = true, heightDp = 640)
@Composable
private fun ItemListScreenPreview() {
    RoborazziDemoTheme {
        ItemListScreen(
            items = sampleItems(),
            onItemClick = {},
            onBack = {},
        )
    }
}