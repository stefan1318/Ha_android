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
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import io.homeassistant.companion.android.R
import io.homeassistant.companion.android.assist.AssistActivity
import io.homeassistant.companion.android.launch.LaunchActivity
import io.homeassistant.companion.android.settings.SettingsActivity
import io.homeassistant.companion.android.frontend.navigation.FrontendTarget
import io.homeassistant.companion.android.frontend.navigation.navigateToFrontend
import io.homeassistant.companion.android.stevora.StevoraPremiumHome
import io.homeassistant.companion.android.stevora.StevoraAiHome
import io.homeassistant.companion.android.stevora.StevoraNativePanel
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
    var showAiHome by remember { mutableStateOf(false) }
    var showNativePanel by remember { mutableStateOf(false) }
    var showLiveControls by remember { mutableStateOf(false) }
    val currentEntry by navController.currentBackStackEntryAsState()
    val onFrontend = currentEntry?.destination?.hasRoute<FrontendRoute>() == true
    val showPremiumNavigation = onFrontend && startDestination != null
    // The Home Assistant frontend owns its navigation while its web panel is visible.
    // Only show native STEVORA tabs on the native Home / AI surfaces.
    val showNativeTabs = showPremiumNavigation && !showLiveControls
    Scaffold(
        modifier = modifier,
        containerColor = LocalHAColorScheme.current.colorSurfaceDefault,
        // Delegate the insets handling to content
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (showNativeTabs) {
                NavigationBar(containerColor = Color(0xFF122437), contentColor = Color(0xFFF4FAFF)) {
                    NavigationBarItem(selected = showPremiumHome && !showNativePanel, onClick = { showPremiumHome = true; showAiHome = false; showNativePanel = false; showLiveControls = false }, icon = { Text("⌂") }, label = { Text(stringResource(R.string.stevora_nav_home)) })
                    NavigationBarItem(selected = showNativePanel, onClick = {
                        showPremiumHome = false
                        showAiHome = false
                        showNativePanel = true
                        showLiveControls = false
                    }, icon = { Text("▦") }, label = { Text(stringResource(R.string.stevora_nav_panel)) })
                    NavigationBarItem(selected = showAiHome, onClick = { showPremiumHome = false; showAiHome = true; showNativePanel = false; showLiveControls = false }, icon = { Text("●") }, label = { Text(stringResource(R.string.stevora_nav_ai)) })
                    NavigationBarItem(selected = false, onClick = { context.startActivity(SettingsActivity.newInstance(context)) }, icon = { Text("⚙") }, label = { Text(stringResource(R.string.stevora_nav_settings)) })
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
                if (showPremiumNavigation && showNativePanel && !showLiveControls) {
                    StevoraNativePanel(
                        onOpenDevices = {
                            showLiveControls = true
                            navController.navigateToFrontend(FrontendTarget.Path("/stevora"))
                        },
                        onOpenAssistant = { showNativePanel = false; showAiHome = true; showPremiumHome = false },
                        onOpenSettings = { context.startActivity(SettingsActivity.newInstance(context)) },
                    )
                }
                if (showPremiumNavigation && showAiHome && !showLiveControls) {
                    StevoraAiHome(
                        onStartConversation = { context.startActivity(AssistActivity.newInstance(context)) },
                        onConfigurePipeline = { context.startActivity(SettingsActivity.newInstance(context, SettingsActivity.Deeplink.AssistSettings)) },
                        onBack = { showAiHome = false; showPremiumHome = true },
                    )
                }
                if (showPremiumNavigation && showPremiumHome && !showLiveControls) StevoraPremiumHome(
                    onOpenPanel = {
                        showPremiumHome = false
                        showAiHome = false
                        showNativePanel = true
                        showLiveControls = false
                    },
                    onOpenVoice = { showPremiumHome = false; showAiHome = true; showNativePanel = false },
                    onOpenSettings = { context.startActivity(SettingsActivity.newInstance(context)) },
                    onOpenOnboarding = {
                        context.startActivity(
                            LaunchActivity.newInstance(
                                context,
                                LaunchActivity.DeepLink.OpenOnboarding(
                                    urlToOnboard = null,
                                    hideExistingServers = false,
                                    skipWelcome = false,
                                ),
                            ),
                        )
                    },
                )
            }

        }
    }
}
