package com.example.ui

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.R
import com.example.modules.automate.AutomateModule
import com.example.modules.automate.ui.AutomateScreen
import com.example.modules.caffeine.CaffeineModule
import com.example.modules.caffeine.ui.CaffeineScreen
import com.example.modules.clipboard.ClipboardModule
import com.example.modules.clipboard.ui.ClipboardScreen
import com.example.modules.onehand.OneHandModule
import com.example.modules.onehand.ui.OneHandScreen
import com.example.ui.hub.HubScreen
import com.example.ui.settings.SettingsScreen
import com.example.ui.theme.ModuYouTheme

object AppRoutes {
    const val ROUTE_HUB = "hub"
    const val ROUTE_SETTINGS = "settings"
}

private data class TopLevelDestination(
    val route: String,
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
)

private val topLevelDestinations = listOf(
    TopLevelDestination(
        route = AppRoutes.ROUTE_HUB,
        labelRes = R.string.nav_modules,
        selectedIcon = Icons.Rounded.Dashboard,
        unselectedIcon = Icons.Outlined.Dashboard,
        testTag = "nav_tab_modules"
    ),
    TopLevelDestination(
        route = AppRoutes.ROUTE_SETTINGS,
        labelRes = R.string.nav_settings,
        selectedIcon = Icons.Rounded.Palette,
        unselectedIcon = Icons.Outlined.Palette,
        testTag = "nav_tab_studio"
    )
)

@Composable
fun ModuYouApp(viewModel: MainViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val isTopLevel = topLevelDestinations.any { dest ->
        currentDestination?.hierarchy?.any { it.route == dest.route } == true
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshSystemStatus()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    ModuYouTheme(themeSettings = state.themeSettings) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val useNavRail = maxWidth >= 600.dp

            val navigateTopLevel: (String) -> Unit = { route ->
                navController.navigate(route) {
                    popUpTo(navController.graph.findStartDestination().id) {
                        saveState = true
                    }
                    launchSingleTop = true
                    restoreState = true
                }
            }

            Scaffold(
                containerColor = MaterialTheme.colorScheme.background,
                bottomBar = {
                    if (isTopLevel && !useNavRail) {
                        NavigationBar {
                            topLevelDestinations.forEach { dest ->
                                val selected = currentDestination?.hierarchy?.any {
                                    it.route == dest.route
                                } == true
                                NavigationBarItem(
                                    selected = selected,
                                    onClick = { navigateTopLevel(dest.route) },
                                    modifier = Modifier.testTag(dest.testTag),
                                    icon = {
                                        Icon(
                                            imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                            contentDescription = stringResource(dest.labelRes)
                                        )
                                    },
                                    label = { Text(stringResource(dest.labelRes)) }
                                )
                            }
                        }
                    }
                }
            ) { innerPadding ->
                Row(modifier = Modifier.fillMaxSize()) {
                    if (isTopLevel && useNavRail) {
                        NavigationRail(
                            modifier = Modifier
                                .fillMaxHeight()
                                .statusBarsPadding()
                                .padding(top = 12.dp)
                        ) {
                            Spacer(modifier = Modifier.weight(0.1f))
                            topLevelDestinations.forEach { dest ->
                                val selected = currentDestination?.hierarchy?.any {
                                    it.route == dest.route
                                } == true
                                NavigationRailItem(
                                    selected = selected,
                                    onClick = { navigateTopLevel(dest.route) },
                                    modifier = Modifier.testTag(dest.testTag),
                                    icon = {
                                        Icon(
                                            imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                            contentDescription = stringResource(dest.labelRes)
                                        )
                                    },
                                    label = { Text(stringResource(dest.labelRes)) }
                                )
                            }
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(if (isTopLevel) innerPadding else androidx.compose.foundation.layout.PaddingValues(0.dp))
                    ) {
                        NavHost(
                            navController = navController,
                            startDestination = AppRoutes.ROUTE_HUB,
                            enterTransition = { fadeIn() },
                            exitTransition = { fadeOut() }
                        ) {
                            composable(AppRoutes.ROUTE_HUB) {
                                HubScreen(
                                    state = state,
                                    onSelectCategory = viewModel::selectCategory,
                                    onToggleModule = viewModel::toggleModule,
                                    onOpenModule = { module ->
                                        navController.navigate(module.descriptor.route) {
                                            launchSingleTop = true
                                        }
                                    }
                                )
                            }

                            composable(AppRoutes.ROUTE_SETTINGS) {
                                SettingsScreen(
                                    state = state,
                                    onUpdateTheme = viewModel::updateThemeSettings,
                                    onToggleAllNotifications = viewModel::setAllExecutionNotifications,
                                    onToggleCaffeineStatusIcon = viewModel::setCaffeineStatusBarIcon,
                                    onToggleRoutinesNotifications = viewModel::setRoutinesNotifications,
                                    onResetOneHand = viewModel::resetOneHandDefaults
                                )
                            }

                            composable(OneHandModule.ROUTE) {
                                OneHandScreen(
                                    state = state,
                                    onNavigateBack = { navController.popBackStack() },
                                    onUpdateConfig = viewModel::updateOneHandConfig,
                                    onUpdateHandle = viewModel::updateHandleConfig,
                                    onResetDefaults = viewModel::resetOneHandDefaults,
                                    onSandboxGestureTriggered = viewModel::onSandboxGestureTriggered
                                )
                            }

                            composable(CaffeineModule.ROUTE) {
                                CaffeineScreen(
                                    state = state,
                                    onNavigateBack = { navController.popBackStack() },
                                    onToggleCaffeine = viewModel::toggleCaffeine,
                                    onUpdateCaffeineConfig = viewModel::updateCaffeineConfig
                                )
                            }

                            composable(AutomateModule.ROUTE) {
                                AutomateScreen(
                                    state = state,
                                    onNavigateBack = { navController.popBackStack() },
                                    onToggleMaster = viewModel::toggleAutomateMaster,
                                    onToggleRoutine = viewModel::toggleRoutine,
                                    onSaveRoutine = viewModel::saveRoutine,
                                    onDeleteRoutine = viewModel::deleteRoutine,
                                    onTestRoutine = viewModel::testRoutine,
                                    onRestoreRoutine = viewModel::restoreRoutine,
                                    onToggleMode = viewModel::toggleMode,
                                    onClearLogs = viewModel::clearAutomateLogs
                                )
                            }

                            composable(com.example.modules.shortcuts.ShortcutsModule.ROUTE) {
                                com.example.modules.shortcuts.ui.ShortcutsScreen(
                                    state = state,
                                    onNavigateBack = { navController.popBackStack() }
                                )
                            }

                            composable(com.example.modules.essentialkey.EssentialKeyModule.ROUTE) {
                                com.example.modules.essentialkey.ui.EssentialKeyScreen(
                                    state = state,
                                    onNavigateBack = { navController.popBackStack() }
                                )
                            }

                            composable(com.example.modules.compat.CompatibilityModule.ROUTE) {
                                com.example.modules.compat.ui.CompatibilityScreen(
                                    state = state,
                                    onNavigateBack = { navController.popBackStack() }
                                )
                            }

                            composable(com.example.modules.deepbluetooth.DeepBluetoothModule.ROUTE) {
                                com.example.modules.deepbluetooth.ui.DeepBluetoothScreen(
                                    state = state,
                                    onNavigateBack = { navController.popBackStack() }
                                )
                            }

                            composable(ClipboardModule.ROUTE) {
                                ClipboardScreen(
                                    state = state,
                                    onNavigateBack = { navController.popBackStack() },
                                    onToggleEnabled = viewModel::toggleClipboardEnabled,
                                    onCaptureNow = viewModel::captureClipboardNow,
                                    onCopyEntry = viewModel::copyClipboardEntry,
                                    onTogglePin = viewModel::togglePinClipboardEntry,
                                    onDeleteEntry = viewModel::deleteClipboardEntry,
                                    onClearUnpinned = viewModel::clearUnpinnedClipboard
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
