package com.bal.reminders.ui

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.bal.reminders.R
import com.bal.reminders.ui.about.AboutScreen
import com.bal.reminders.ui.about.LicensesScreen
import com.bal.reminders.ui.about.PrivacyScreen
import com.bal.reminders.ui.details.DetailsScreen
import com.bal.reminders.ui.editor.EditorScreen
import com.bal.reminders.ui.home.HomeScreen
import com.bal.reminders.ui.list.RemindersScreen
import com.bal.reminders.ui.log.LogScreen
import com.bal.reminders.ui.onboarding.OnboardingScreen
import com.bal.reminders.ui.permissions.PermissionsScreen
import com.bal.reminders.ui.settings.SettingsScreen
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull

private data class Tab(val route: String, val labelRes: Int, val selectedIcon: androidx.compose.ui.graphics.vector.ImageVector, val icon: androidx.compose.ui.graphics.vector.ImageVector)

// Stacked screens slide in along the reading direction (RTL-aware via
// SlideDirection.Start/End); tab switches keep the calm crossfade defaults.
private val pushEnter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
    slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Start, tween(260)) + fadeIn(tween(260))
}
private val pushPopExit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
    slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.End, tween(220)) + fadeOut(tween(220))
}

@Composable
fun BalRoot(
    showOnboarding: Boolean,
    requestedReminderId: MutableStateFlow<Long?>,
) {
    val navController = rememberNavController()
    val start = remember { if (showOnboarding) Routes.ONBOARDING else Routes.HOME }

    // A notification tap opens the reminder's details.
    LaunchedEffect(requestedReminderId) {
        requestedReminderId.filterNotNull().collect { id ->
            requestedReminderId.value = null
            navController.navigate(Routes.details(id))
        }
    }

    val tabs = remember {
        listOf(
            Tab(Routes.HOME, R.string.tab_today, Icons.Rounded.WbSunny, Icons.Outlined.WbSunny),
            Tab(Routes.REMINDERS, R.string.tab_reminders, Icons.Rounded.Notifications, Icons.Outlined.Notifications),
            Tab(Routes.SETTINGS, R.string.tab_settings, Icons.Rounded.Settings, Icons.Outlined.Settings),
        )
    }
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val onTab = tabs.any { it.route == currentRoute }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (onTab) {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                    tabs.forEach { tab ->
                        val selected = currentRoute == tab.route
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                navController.navigate(tab.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = {
                                Icon(
                                    if (selected) tab.selectedIcon else tab.icon,
                                    contentDescription = null,
                                )
                            },
                            label = { Text(stringResource(tab.labelRes)) },
                        )
                    }
                }
            }
        },
        floatingActionButton = {
            if (currentRoute == Routes.HOME || currentRoute == Routes.REMINDERS) {
                FloatingActionButton(
                    onClick = { navController.navigate(Routes.editorNew()) },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = stringResource(R.string.action_new_reminder))
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = start,
            modifier = Modifier.padding(padding),
            enterTransition = { fadeIn(tween(220)) },
            exitTransition = { fadeOut(tween(120)) },
            popEnterTransition = { fadeIn(tween(220)) },
            popExitTransition = { fadeOut(tween(120)) },
        ) {
            composable(Routes.ONBOARDING) {
                OnboardingScreen(
                    onDone = {
                        navController.navigate(Routes.HOME) {
                            popUpTo(Routes.ONBOARDING) { inclusive = true }
                        }
                    },
                )
            }
            composable(Routes.HOME) {
                HomeScreen(
                    onOpenDetails = { navController.navigate(Routes.details(it)) },
                    onOpenEditorDraft = { route -> navController.navigate(route) },
                    onOpenPermissions = { navController.navigate(Routes.PERMISSIONS) },
                    onOpenTemplate = { navController.navigate(Routes.editorTemplate(it)) },
                )
            }
            composable(Routes.REMINDERS) {
                RemindersScreen(
                    onOpenDetails = { navController.navigate(Routes.details(it)) },
                    onOpenLog = { navController.navigate(Routes.LOG) },
                )
            }
            composable(Routes.SETTINGS) {
                SettingsScreen(
                    onOpenPermissions = { navController.navigate(Routes.PERMISSIONS) },
                    onOpenLog = { navController.navigate(Routes.LOG) },
                    onOpenAbout = { navController.navigate(Routes.ABOUT) },
                )
            }
            composable(Routes.LOG, enterTransition = pushEnter, popExitTransition = pushPopExit) {
                LogScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.PERMISSIONS, enterTransition = pushEnter, popExitTransition = pushPopExit) {
                PermissionsScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.ABOUT, enterTransition = pushEnter, popExitTransition = pushPopExit) {
                AboutScreen(
                    onBack = { navController.popBackStack() },
                    onOpenPrivacy = { navController.navigate(Routes.PRIVACY) },
                    onOpenLicenses = { navController.navigate(Routes.LICENSES) },
                )
            }
            composable(Routes.PRIVACY, enterTransition = pushEnter, popExitTransition = pushPopExit) {
                PrivacyScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.LICENSES, enterTransition = pushEnter, popExitTransition = pushPopExit) {
                LicensesScreen(onBack = { navController.popBackStack() })
            }
            composable(
                Routes.DETAILS,
                arguments = listOf(navArgument("id") { type = NavType.LongType }),
                enterTransition = pushEnter,
                popExitTransition = pushPopExit,
            ) {
                DetailsScreen(
                    onBack = { navController.popBackStack() },
                    onEdit = { navController.navigate(Routes.editorEdit(it)) },
                )
            }
            composable(
                Routes.EDITOR,
                enterTransition = pushEnter,
                popExitTransition = pushPopExit,
                arguments = listOf(
                    navArgument("id") { type = NavType.LongType; defaultValue = 0L },
                    navArgument("title") { type = NavType.StringType; nullable = true },
                    navArgument("type") { type = NavType.StringType; nullable = true },
                    navArgument("time") { type = NavType.StringType; nullable = true },
                    navArgument("date") { type = NavType.StringType; nullable = true },
                    navArgument("days") { type = NavType.StringType; nullable = true },
                    navArgument("dom") { type = NavType.StringType; nullable = true },
                    navArgument("month") { type = NavType.StringType; nullable = true },
                    navArgument("hy") { type = NavType.StringType; nullable = true },
                    navArgument("hm") { type = NavType.StringType; nullable = true },
                    navArgument("hd") { type = NavType.StringType; nullable = true },
                    navArgument("cal") { type = NavType.StringType; nullable = true },
                    navArgument("template") { type = NavType.StringType; nullable = true },
                ),
            ) {
                EditorScreen(onDone = { navController.popBackStack() })
            }
        }
    }
}
