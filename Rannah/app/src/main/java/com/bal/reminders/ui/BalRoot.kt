package com.bal.reminders.ui

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.bal.reminders.ui.about.AboutScreen
import com.bal.reminders.ui.about.PrivacyScreen
import com.bal.reminders.ui.details.DetailsScreen
import com.bal.reminders.ui.editor.EditorScreen
import com.bal.reminders.ui.home.ChecklistScreen
import com.bal.reminders.ui.onboarding.OnboardingScreen
import com.bal.reminders.ui.permissions.PermissionsScreen
import com.bal.reminders.ui.settings.SettingsScreen
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull

// Pushed screens slide in along the reading direction (RTL-aware); the home
// checklist just fades so it never feels like a stack.
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
    val start = if (showOnboarding) Routes.ONBOARDING else Routes.HOME

    // A notification/alarm tap opens the reminder's details.
    LaunchedEffect(requestedReminderId) {
        requestedReminderId.filterNotNull().collect { id ->
            requestedReminderId.value = null
            navController.navigate(Routes.details(id))
        }
    }

    NavHost(
        navController = navController,
        startDestination = start,
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
            ChecklistScreen(
                onOpenDetails = { navController.navigate(Routes.details(it)) },
                onOpenEditor = { navController.navigate(Routes.editorNew()) },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                onOpenPermissions = { navController.navigate(Routes.PERMISSIONS) },
            )
        }
        composable(Routes.SETTINGS, enterTransition = pushEnter, popExitTransition = pushPopExit) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onOpenPermissions = { navController.navigate(Routes.PERMISSIONS) },
                onOpenAbout = { navController.navigate(Routes.ABOUT) },
            )
        }
        composable(Routes.PERMISSIONS, enterTransition = pushEnter, popExitTransition = pushPopExit) {
            PermissionsScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.ABOUT, enterTransition = pushEnter, popExitTransition = pushPopExit) {
            AboutScreen(
                onBack = { navController.popBackStack() },
                onOpenPrivacy = { navController.navigate(Routes.PRIVACY) },
            )
        }
        composable(Routes.PRIVACY, enterTransition = pushEnter, popExitTransition = pushPopExit) {
            PrivacyScreen(onBack = { navController.popBackStack() })
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
            ),
        ) {
            EditorScreen(onDone = { navController.popBackStack() })
        }
    }
}
