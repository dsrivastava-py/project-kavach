package com.kavach.app.presentation

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.rememberNavController
import com.kavach.app.presentation.navigation.KavachNavGraph
import com.kavach.app.presentation.navigation.Screen
import com.kavach.app.presentation.theme.KavachTheme
import com.kavach.app.presentation.viewmodel.AuthViewModel
import com.kavach.app.presentation.viewmodel.SettingsViewModel
import dagger.hilt.android.AndroidEntryPoint

/**
 * Single Activity — hosts the entire Compose navigation graph.
 *
 * - Splash screen is shown while the start destination is resolved.
 * - Dark theme preference is read from DataStore and applied to the root theme.
 * - Nav start destination is determined by auth state: logged-in → Dashboard, else → Auth.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Keep splash visible while we determine auth state
        var startDestinationReady = false
        splashScreen.setKeepOnScreenCondition { !startDestinationReady }

        setContent {
            val settingsViewModel: SettingsViewModel = hiltViewModel()
            val authViewModel: AuthViewModel         = hiltViewModel()

            val isDark     by settingsViewModel.isDarkTheme.collectAsStateWithLifecycle()
            val isLoggedIn by authViewModel.isLoggedIn.collectAsStateWithLifecycle(initialValue = null)

            // Don't render until we know auth state
            if (isLoggedIn == null) return@setContent

            startDestinationReady = true

            val startDestination = if (isLoggedIn == true) Screen.Dashboard.route else Screen.Auth.route
            val navController    = rememberNavController()

            KavachTheme(darkTheme = isDark) {
                KavachNavGraph(
                    navController    = navController,
                    startDestination = startDestination,
                )
            }
        }
    }
}
