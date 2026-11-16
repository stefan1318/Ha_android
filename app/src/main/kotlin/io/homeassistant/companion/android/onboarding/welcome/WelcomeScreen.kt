package io.homeassistant.companion.android.onboarding.welcome

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.compose.theme.HAThemeForPreview
import io.homeassistant.companion.android.util.compose.HAPreviews
import kotlinx.coroutines.launch

@Composable
internal fun WelcomeScreen(
    onConnectClick: () -> Unit,
    onLearnMoreClick: suspend () -> Unit,
    modifier: Modifier = Modifier,
) {
    val coroutineScope = rememberCoroutineScope()

    val bg = LocalConfiguration.current.locales[0].language == "bg"
    WelcomeTemplate(
        title = "STEVORA",
        details = if (bg) "Вашият интелигентен дом. Свържете STEVORA Home Hub, за да започнете." else "Your intelligent home. Connect your STEVORA Home Hub to get started.",
        primaryButtonText = if (bg) "Свързване към STEVORA" else "Connect to STEVORA",
        onPrimaryClick = onConnectClick,
        secondaryButtonText = stringResource(commonR.string.welcome_learn_more),
        onSecondaryClick = { coroutineScope.launch { onLearnMoreClick() } },
        modifier = modifier,
    )
}

@HAPreviews
@Composable
private fun WelcomeScreenPreview() {
    HAThemeForPreview {
        WelcomeScreen(onConnectClick = {}, onLearnMoreClick = {})
    }
}
