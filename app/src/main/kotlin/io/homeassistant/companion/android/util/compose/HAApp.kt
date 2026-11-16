package io.homeassistant.companion.android.util.compose

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult.ActionPerformed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.ui.platform.LocalContext
import io.homeassistant.companion.android.assist.AssistActivity
import io.homeassistant.companion.android.settings.SettingsActivity
import io.homeassistant.companion.android.frontend.navigation.FrontendTarget
import io.homeassistant.companion.android.frontend.navigation.navigateToFrontend
import io.homeassistant.companion.android.stevora.StevoraPremiumHome
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import io.homeassistant.companion.android.common.compose.theme.LocalHAColorScheme
import io.homeassistant.companion.android.frontend.navigation.FrontendRoute
import io.homeassistant.companion.android.launch.HAStartDestinationRoute
import io.homeassistant.companion.android.launch.PipReadiness
import io.homeassistant.companion.android.loading.LoadingScreen

/**
 * Main composable for the Home Assistant app.
 *
 * This composable sets up the basic structure of the app, including the [Scaffold] for layout,
 * a [SnackbarHost], and the [HANavHost] for handling navigation.
 *
 * It also handles horizontal window insets to ensure content is displayed correctly within the screen's safe areas,
 * except for the frontend destination, which draws edge-to-edge and handles all its insets itself.
 * Sub composable needs to handle vertical insets themselves.
 *
 * @param navController The NavHostController to use for navigation.
 * @param startDestination The initial destination of the navigation graph. If it is null [LoadingScreen]
 *                         is displayed.
 * @param snackbarHostState The [SnackbarHostState] used to show snackbars for the app.
 * @param modifier The modifier to be applied to this composable.
 */
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
    var showPremiumHome by remember { mutableStateOf(true) }
    val currentEntry by navController.currentBackStackEntryAsState()
    val onFrontend = currentEntry?.destination?.hasRoute<FrontendRoute>() == true
    val showPremiumNavigation = onFrontend && startDestination != null
    Scaffold(
        modifier = modifier,
        containerColor = LocalHAColorScheme.current.colorSurfaceDefault,
        // Delegate the insets handling to content
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (showPremiumNavigation) {
                NavigationBar {
                    NavigationBarItem(selected = showPremiumHome, onClick = { showPremiumHome = true }, icon = { Text("⌂") }, label = { Text("Home") })
                    NavigationBarItem(selected = !showPremiumHome, onClick = {
                        showPremiumHome = false
                        navController.navigateToFrontend(FrontendTarget.Path("/stevora"))
                    }, icon = { Text("▦") }, label = { Text("Panel") })
                    NavigationBarItem(selected = false, onClick = { context.startActivity(AssistActivity.newInstance(context)) }, icon = { Text("●") }, label = { Text("AI") })
                    NavigationBarItem(selected = false, onClick = { context.startActivity(SettingsActivity.newInstance(context)) }, icon = { Text("⚙") }, label = { Text("Settings") })
                }
            }
        },
        snackbarHost = {
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing),
            )
        },
    ) { padding ->
        val currentBackStackEntry by navController.currentBackStackEntryAsState()
        val isFrontend = currentBackStackEntry?.destination?.hasRoute<FrontendRoute>() == true
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .consumeWindowInsets(padding)
                .then(
                    if (isFrontend) {
                        Modifier
                    } else {
                        Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
                    },
                ),
        ) {
            Box(Modifier.fillMaxSize()) {
                HANavHost(
                    navController = navController,
                    startDestination = startDestination,
                    onRequestFullscreen = onRequestFullscreen,
                    onPipReadinessChanged = onPipReadinessChanged,
                    onShowSnackbar = { message, action ->
                        snackbarHostState.showSnackbar(message, action, duration = SnackbarDuration.Short) == ActionPerformed
                    },
                )
                if (showPremiumNavigation && showPremiumHome) StevoraPremiumHome(
                    onOpenPanel = {
                        showPremiumHome = false
                        navController.navigateToFrontend(FrontendTarget.Path("/stevora"))
                    },
                    onOpenVoice = { context.startActivity(AssistActivity.newInstance(context)) },
                    onOpenSettings = { context.startActivity(SettingsActivity.newInstance(context)) },
                    onOpenOnboarding = { context.startActivity(SettingsActivity.newInstance(context)) },
                )
            }

        }
    }
}
