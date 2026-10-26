package com.dk.kuiver.sample

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.dk.kuiver.sample.theme.AppTheme

enum class Screen {
    SHOWCASE,
    PLAYGROUND,
    STRESS_TEST,
}

// Layout algorithm selection for the sample app UI
enum class LayoutAlgorithm {
    HIERARCHICAL,
    FORCE_DIRECTED
}

@Composable
@Preview
fun App() {
    val systemInDarkTheme = isSystemInDarkTheme()
    var isDarkTheme by rememberSaveable { mutableStateOf(systemInDarkTheme) }
    var currentScreen by rememberSaveable { mutableStateOf(Screen.SHOWCASE) }

    // Scrolling pans on desktop and web, so the zoom gesture needs pointing out once
    val zoomHintHostState = remember { SnackbarHostState() }
    LaunchedEffect(Unit) {
        if (usesCtrlScrollZoom) {
            zoomHintHostState.showSnackbar("Hold Ctrl and scroll to zoom in and out")
        }
    }

    AppTheme(darkTheme = isDarkTheme) {
        Box(modifier = Modifier.fillMaxSize()) {
            when (currentScreen) {
                Screen.SHOWCASE -> ShowcaseDemo(
                    isDarkTheme = isDarkTheme,
                    onThemeToggle = { isDarkTheme = !isDarkTheme },
                    onOpenPlayground = { currentScreen = Screen.PLAYGROUND },
                    onOpenStressTest = { currentScreen = Screen.STRESS_TEST }
                )

                Screen.PLAYGROUND -> PlaygroundScreen(
                    onNavigateBack = { currentScreen = Screen.SHOWCASE }
                )

                Screen.STRESS_TEST -> StressTestScreen(
                    onNavigateBack = { currentScreen = Screen.SHOWCASE }
                )
            }

            SnackbarHost(
                hostState = zoomHintHostState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .systemBarsPadding()
                    .padding(start = 16.dp, end = 16.dp, bottom = 96.dp)
            )
        }
    }
}
