package com.bal.reminders

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.navigation.compose.hiltViewModel
import com.bal.reminders.data.ThemeMode
import com.bal.reminders.ui.BalRoot
import com.bal.reminders.ui.MainViewModel
import com.bal.reminders.ui.theme.BalTheme
import dagger.hilt.android.AndroidEntryPoint
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    /** Set from a notification tap; consumed by navigation to open the details screen. */
    private val requestedReminderId = MutableStateFlow<Long?>(null)

    override fun attachBaseContext(newBase: Context) {
        // «رَنّة» is Arabic regardless of the system language: locale-wrap the
        // context so resources, plurals and date names resolve in Arabic.
        val config = Configuration(newBase.resources.configuration)
        val locale = Locale("ar")
        Locale.setDefault(locale)
        config.setLocale(locale)
        config.setLayoutDirection(locale)
        super.attachBaseContext(newBase.createConfigurationContext(config))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        readReminderExtra(intent)

        setContent {
            val viewModel: MainViewModel = hiltViewModel()
            val state by viewModel.state.collectAsStateWithLifecycle()
            splash.setKeepOnScreenCondition { !state.loaded }

            if (state.loaded) {
                val dark = when (state.themeMode) {
                    ThemeMode.LIGHT -> false
                    ThemeMode.DARK -> true
                    ThemeMode.SYSTEM -> androidx.compose.foundation.isSystemInDarkTheme()
                }
                // The system bars carry the app's appearance, not the system's.
                // Someone running رَنّة in «نهاري» on a dark-themed phone was
                // getting light icons over a light background — invisible.
                SideEffect { applyBarAppearance(dark) }
                BalTheme(darkTheme = dark) {
                    BalRoot(
                        showOnboarding = !state.onboardingDone,
                        requestedReminderId = requestedReminderId,
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        readReminderExtra(intent)
    }

    private fun readReminderExtra(intent: Intent?) {
        val id = intent?.getLongExtra(EXTRA_REMINDER_ID, -1L) ?: -1L
        if (id > 0) requestedReminderId.value = id
    }

    companion object {
        const val EXTRA_REMINDER_ID = "reminder_id"
    }
}

/**
 * Match the status- and navigation-bar icon appearance to the app's own theme.
 *
 * `enableEdgeToEdge()` decides light or dark icons from the *system* setting at
 * the moment it is called. رَنّة lets the user pick an appearance independently
 * of the system, and that choice can also change while the activity is alive,
 * so the bars are re-stated whenever it does.
 */
internal fun ComponentActivity.applyBarAppearance(dark: Boolean) {
    WindowInsetsControllerCompat(window, window.decorView).apply {
        isAppearanceLightStatusBars = !dark
        isAppearanceLightNavigationBars = !dark
    }
}
