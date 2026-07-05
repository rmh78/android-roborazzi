package com.example.roborazzidemo.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.roborazzidemo.R
import com.example.roborazzidemo.theme.LcarsBlue
import com.example.roborazzidemo.theme.LcarsBlueDeep
import com.example.roborazzidemo.theme.LcarsOrange
import com.example.roborazzidemo.theme.RoborazziDemoTheme
import com.example.roborazzidemo.ui.futuristic.HoloPanel
import com.example.roborazzidemo.ui.futuristic.LcarsSideAccent
import com.example.roborazzidemo.ui.futuristic.NeonPrimaryButton
import com.example.roborazzidemo.ui.futuristic.NexusStatusChip

@Composable
fun HomeScreen(
    onBrowseItems: () -> Unit,
    onViewSampleDetail: () -> Unit,
) {
    Scaffold(
        containerColor = Color.Transparent,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            LcarsSideAccent(modifier = Modifier.fillMaxWidth())

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                NexusStatusChip(label = "SYS.ONLINE")
                NexusStatusChip(
                    label = "NEXUS 2.4",
                    accent = LcarsBlue,
                )
            }

            HoloPanel(
                modifier = Modifier.fillMaxWidth(),
                accent = LcarsBlue,
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    Text(
                        text = "BRIDGE CONSOLE",
                        style = MaterialTheme.typography.labelMedium,
                        color = LcarsOrange,
                        fontWeight = FontWeight.Bold,
                    )
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.home_title),
                            style = MaterialTheme.typography.displaySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Light,
                        )
                        Text(
                            text = stringResource(R.string.home_subtitle),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    Column(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        NeonPrimaryButton(
                            text = stringResource(R.string.home_browse_items),
                            onClick = onBrowseItems,
                        )
                        NeonPrimaryButton(
                            text = stringResource(R.string.home_view_sample_detail),
                            onClick = onViewSampleDetail,
                            accent = LcarsBlueDeep,
                        )
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun HomeScreenPreview() {
    RoborazziDemoTheme(darkTheme = true) {
        HomeScreen(
            onBrowseItems = {},
            onViewSampleDetail = {},
        )
    }
}