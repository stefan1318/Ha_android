package io.homeassistant.companion.android.util.compose

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult.ActionPerformed
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import io.homeassistant.companion.android.R
import io.homeassistant.companion.android.assist.AssistActivity
import io.homeassistant.companion.android.frontend.navigation.FrontendRoute
import io.homeassistant.companion.android.frontend.navigation.navigateToFrontend
import io.homeassistant.companion.android.launch.HAStartDestinationRoute
import io.homeassistant.companion.android.launch.PipReadiness
import io.homeassistant.companion.android.settings.SettingsActivity

/** STEVORA owns the native application shell; onboarding stays upstream-compatible. */
@Composable
internal fun HAApp(
    navController: NavHostController,
    startDestination: HAStartDestinationRoute?,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
    onRequestFullscreen: (Boolean) -> Unit = {},
    onPipReadinessChanged: (PipReadiness?) -> Unit = {},
) {
    val context = LocalContext.current
    val entry by navController.currentBackStackEntryAsState()
    val onHome = entry?.destination?.hasRoute<FrontendRoute>() == true
    Scaffold(
        modifier = modifier,
        containerColor = Color(0xFF08131F),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (onHome) {
                NavigationBar(containerColor = Color(0xFF122437), contentColor = Color(0xFFF4FAFF)) {
                    NavigationBarItem(
                        selected = true,
                        onClick = { navController.navigateToFrontend() },
                        icon = { Text("⌂") },
                        label = { Text(stringResource(R.string.stevora_nav_home)) },
                    )
                    NavigationBarItem(
                        selected = false,
                        onClick = { context.startActivity(AssistActivity.newInstance(context)) },
                        icon = { Text("●") },
                        label = { Text(stringResource(R.string.stevora_nav_ai)) },
                    )
                    NavigationBarItem(
                        selected = false,
                        onClick = { context.startActivity(SettingsActivity.newInstance(context)) },
                        icon = { Text("⚙") },
                        label = { Text(stringResource(R.string.stevora_nav_settings)) },
                    )
                }
            }
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
    ) { padding ->
        androidx.compose.foundation.layout.Box(Modifier.fillMaxSize().padding(padding)) {
            HANavHost(
                navController = navController,
                startDestination = startDestination,
                onRequestFullscreen = onRequestFullscreen,
                onPipReadinessChanged = onPipReadinessChanged,
                onShowSnackbar = { message, action ->
                    snackbarHostState.showSnackbar(message, action, duration = SnackbarDuration.Short) == ActionPerformed
                },
            )
        }
    }
}
