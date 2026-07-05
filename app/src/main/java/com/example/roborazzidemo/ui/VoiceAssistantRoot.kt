package com.example.roborazzidemo.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import com.example.roborazzidemo.ui.futuristic.FuturisticBackground
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.rememberNavController
import com.example.roborazzidemo.AppNavHost
import com.example.roborazzidemo.BuildConfig
import com.example.roborazzidemo.navigation.VoiceNavigationHandler
import com.example.roborazzidemo.theme.RoborazziDemoTheme
import com.example.roborazzidemo.viewmodel.ItemListScrollController
import com.example.roborazzidemo.viewmodel.VoiceAssistantViewModel
import com.example.roborazzidemo.voice.VoiceToolExecutor
@Composable
fun VoiceAssistantRoot(
    hasRecordAudioPermission: Boolean,
    onRequestRecordAudioPermission: () -> Unit,
) {
    RoborazziDemoTheme {
        val navController = rememberNavController()
        val scrollController = remember { ItemListScrollController() }
        val navigationHandler = remember(navController) { VoiceNavigationHandler(navController) }
        val toolExecutor = remember(navigationHandler, scrollController) {
            VoiceToolExecutor(navigationHandler, scrollController)
        }
        val applicationContext = LocalContext.current.applicationContext
        val viewModel: VoiceAssistantViewModel = viewModel(
            factory = VoiceAssistantViewModel.Factory(
                apiKey = BuildConfig.XAI_API_KEY,
                toolExecutor = toolExecutor,
                applicationContext = applicationContext,
            ),
        )

        val uiState by viewModel.uiState.collectAsState()
        DisposableEffect(hasRecordAudioPermission) {
            viewModel.setMicrophonePermissionGranted(hasRecordAudioPermission)
            onDispose { }
        }

        Box(modifier = Modifier.fillMaxSize()) {
            FuturisticBackground(modifier = Modifier.fillMaxSize())
            AppNavHost(
                navController = navController,
                scrollController = scrollController,
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("app_content"),
            )

            VoiceTranscriptOverlay(
                state = uiState,
                onConnectChange = { connect ->
                    if (!hasRecordAudioPermission) {
                        onRequestRecordAudioPermission()
                        return@VoiceTranscriptOverlay
                    }
                    if (connect) {
                        viewModel.connect()
                    } else {
                        viewModel.disconnect()
                    }
                },
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}