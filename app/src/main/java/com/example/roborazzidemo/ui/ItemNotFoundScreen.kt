package com.example.roborazzidemo.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.roborazzidemo.R
import com.example.roborazzidemo.theme.LcarsRed
import com.example.roborazzidemo.ui.futuristic.HoloPanel
import com.example.roborazzidemo.ui.futuristic.NexusSectionHeader

@Composable
fun ItemNotFoundScreen(
    onBack: () -> Unit,
) {
    Scaffold(
        containerColor = Color.Transparent,
        contentWindowInsets = AppScaffoldInsets,
        topBar = {
            AppTopBar(
                title = stringResource(R.string.item_not_found_title),
                onBack = onBack,
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            HoloPanel(accent = LcarsRed) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    NexusSectionHeader(
                        title = "No Match",
                        subtitle = "Query returned zero records",
                        accent = LcarsRed,
                    )
                    Text(
                        text = "⚠",
                        style = MaterialTheme.typography.displaySmall,
                        color = LcarsRed,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = stringResource(R.string.item_not_found_message),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}