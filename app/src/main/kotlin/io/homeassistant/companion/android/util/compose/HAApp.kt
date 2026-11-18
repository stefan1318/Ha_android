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
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.platform.LocalConfiguration
import io.homeassistant.companion.android.stevora.StevoraHomeOverview
import io.homeassistant.companion.android.stevora.StevoraLivePanel
import io.homeassistant.companion.android.stevora.StevoraAiHome
import io.homeassistant.companion.android.stevora.StevoraSettingsHome
import io.homeassistant.companion.android.stevora.StevoraSystemHome
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
    var section by remember { mutableStateOf("home") }
    val bgLanguage = LocalConfiguration.current.locales[0].language == "bg"
    val tabs = listOf(
        Triple("home", "⌂", if (bgLanguage) "Начало" else "Home"),
        Triple("rooms", "▦", if (bgLanguage) "Стаи" else "Rooms"),
        Triple("ai", "✦", "AI"),
        Triple("system", "◈", if (bgLanguage) "Система" else "System"),
        Triple("settings", "⚙", if (bgLanguage) "Настройки" else "Settings"),
    )
    Scaffold(
        modifier = modifier,
        containerColor = Color(0xFF08131F),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (onHome) {
                NavigationBar(containerColor = Color(0xFF122437), contentColor = Color(0xFFF4FAFF)) {
                    tabs.forEach { (key, glyph, label) ->
                        NavigationBarItem(
                            selected = section == key,
                            onClick = { section = key },
                            icon = { Text(glyph) },
                            label = { Text(label, maxLines = 1) },
                        )
                    }
                }
            }
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            // Keep the NavHost composed while entering or leaving the native STEVORA shell.
            // Removing it on FrontendRoute destroys the navigation graph during onboarding.
            Box(Modifier.fillMaxSize().alpha(if (onHome) 0f else 1f)) {
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
            if (onHome) {
                when (section) {
                    "home" -> StevoraHomeOverview(
                        onRooms = { section = "rooms" },
                        onAi = { section = "ai" },
                        onSystem = { section = "system" },
                    )
                    "rooms" -> StevoraLivePanel(initialRoom = "living")
                    "ai" -> StevoraAiHome(
                        onStartConversation = { context.startActivity(AssistActivity.newInstance(context)) },
                        onConfigurePipeline = { context.startActivity(AssistActivity.newInstance(context)) },
                        onBack = { section = "home" },
                    )
                    "system" -> StevoraSystemHome(onBack = { section = "home" })
                    "settings" -> StevoraSettingsHome(
                        onServerSettings = { context.startActivity(SettingsActivity.newInstance(context)) },
                        onVoiceSettings = { context.startActivity(SettingsActivity.newInstance(context, SettingsActivity.Deeplink.AssistSettings)) },
                        onSystemSettings = { context.startActivity(SettingsActivity.newInstance(context)) },
                        onBack = { section = "home" },
                    )
                }
            }
        }
    }
}
