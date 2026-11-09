package com.music.pianocoach.ui

import android.app.Activity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Piano
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.music.pianocoach.ui.tabs.CoachTab
import com.music.pianocoach.ui.tabs.DrillTab
import com.music.pianocoach.ui.tabs.PracticeTab
import com.music.pianocoach.ui.tabs.SettingsSheet

enum class MainTab(val route: String, val label: String) {
    Practice("practice", "练习"),
    Coach("coach", "教练"),
    Drill("drill", "跟练"),
}

@Composable
fun MainScaffold(viewModel: MainViewModel = viewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val current = backStack?.destination?.route ?: MainTab.Practice.route
    val drillFullscreen = current == MainTab.Drill.route

    ImmersiveEffect(enabled = drillFullscreen)

    if (uiState.showSettings) {
        SettingsSheet(uiState, viewModel, onDismiss = { viewModel.showSettings(false) })
    }

    Scaffold(
        containerColor = if (drillFullscreen) Color(0xFF12151A) else Color.Unspecified,
        bottomBar = {
            if (!drillFullscreen) {
                NavigationBar {
                    MainTab.entries.forEach { tab ->
                        NavigationBarItem(
                            selected = current == tab.route,
                            onClick = { navController.navigate(tab.route) { launchSingleTop = true } },
                            icon = {
                                Icon(
                                    when (tab) {
                                        MainTab.Practice -> Icons.Default.Piano
                                        MainTab.Coach -> Icons.Default.School
                                        MainTab.Drill -> Icons.Default.MusicNote
                                    },
                                    contentDescription = tab.label,
                                )
                            },
                            label = { Text(tab.label) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = MainTab.Practice.route,
            modifier = if (drillFullscreen) {
                Modifier.fillMaxSize()
            } else {
                Modifier.padding(padding)
            },
        ) {
            composable(MainTab.Practice.route) { PracticeTab(uiState, viewModel) }
            composable(MainTab.Coach.route) { CoachTab(uiState, viewModel) }
            composable(MainTab.Drill.route) {
                DrillTab(
                    state = uiState,
                    viewModel = viewModel,
                    fullscreen = true,
                    onExitFullscreen = {
                        navController.navigate(MainTab.Practice.route) { launchSingleTop = true }
                    },
                )
            }
        }
    }
}

@Composable
private fun ImmersiveEffect(enabled: Boolean) {
    val view = LocalView.current
    DisposableEffect(enabled) {
        val activity = view.context as? Activity
        val window = activity?.window
        val controller = window?.let { WindowInsetsControllerCompat(it, view) }
        if (enabled && window != null && controller != null) {
            WindowCompat.setDecorFitsSystemWindows(window, false)
            window.statusBarColor = Color(0xFF12151A).toArgb()
            window.navigationBarColor = Color(0xFF12151A).toArgb()
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        } else if (window != null && controller != null) {
            WindowCompat.setDecorFitsSystemWindows(window, true)
            controller.show(WindowInsetsCompat.Type.systemBars())
        }
        onDispose {
            if (window != null && controller != null) {
                WindowCompat.setDecorFitsSystemWindows(window, true)
                controller.show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }
}
