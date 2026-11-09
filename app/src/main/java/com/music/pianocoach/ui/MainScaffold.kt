package com.music.pianocoach.ui

import android.app.Activity
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Piano
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
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
import com.music.pianocoach.ui.theme.MuseColors

enum class MainTab(val route: String, val label: String) {
    Practice("practice", "Studio"),
    Coach("coach", "Muse"),
    Drill("drill", "Play"),
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
        containerColor = MuseColors.InkBackground,
        bottomBar = {
            if (!drillFullscreen) {
                NavigationBar(
                    containerColor = MuseColors.InkSurface.copy(alpha = 0.92f),
                    tonalElevation = 0.dp,
                    windowInsets = NavigationBarDefaults.windowInsets,
                ) {
                    MainTab.entries.forEach { tab ->
                        val selected = current == tab.route
                        val scale by animateFloatAsState(
                            targetValue = if (selected) 1.08f else 1f,
                            animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                            label = "navScale",
                        )
                        val iconTint by animateColorAsState(
                            targetValue = if (selected) MuseColors.Amber else MuseColors.IvoryDim,
                            label = "navTint",
                        )
                        NavigationBarItem(
                            selected = selected,
                            onClick = { navController.navigate(tab.route) { launchSingleTop = true } },
                            icon = {
                                Icon(
                                    when (tab) {
                                        MainTab.Practice -> Icons.Default.Piano
                                        MainTab.Coach -> Icons.Default.AutoAwesome
                                        MainTab.Drill -> Icons.Default.MusicNote
                                    },
                                    contentDescription = tab.label,
                                    tint = iconTint,
                                    modifier = Modifier.graphicsLayer {
                                        scaleX = scale
                                        scaleY = scale
                                    },
                                )
                            },
                            label = { Text(tab.label, style = MaterialTheme.typography.labelLarge) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MuseColors.Amber,
                                selectedTextColor = MuseColors.Amber,
                                unselectedIconColor = MuseColors.IvoryDim,
                                unselectedTextColor = MuseColors.IvoryDim,
                                indicatorColor = MuseColors.Amber.copy(alpha = 0.18f),
                            ),
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
        val bar = MuseColors.WaterfallBg.toArgb()
        if (enabled && window != null && controller != null) {
            WindowCompat.setDecorFitsSystemWindows(window, false)
            window.statusBarColor = bar
            window.navigationBarColor = bar
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            WindowInsetsControllerCompat(window, view).isAppearanceLightStatusBars = false
        } else if (window != null && controller != null) {
            WindowCompat.setDecorFitsSystemWindows(window, true)
            window.statusBarColor = MuseColors.InkBackground.toArgb()
            window.navigationBarColor = MuseColors.InkBackground.toArgb()
            controller.show(WindowInsetsCompat.Type.systemBars())
            WindowInsetsControllerCompat(window, view).isAppearanceLightStatusBars = false
        }
        onDispose {
            if (window != null && controller != null) {
                WindowCompat.setDecorFitsSystemWindows(window, true)
                controller.show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }
}
