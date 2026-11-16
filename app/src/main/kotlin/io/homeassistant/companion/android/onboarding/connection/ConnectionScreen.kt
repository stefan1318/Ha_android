package io.homeassistant.companion.android.onboarding.connection

import android.annotation.SuppressLint
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.annotation.VisibleForTesting
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material3.TextButton
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import io.homeassistant.companion.android.R
import io.homeassistant.companion.android.common.compose.theme.HAThemeForPreview
import io.homeassistant.companion.android.frontend.filechooser.FileChooserEffect
import io.homeassistant.companion.android.frontend.filechooser.FileChooserRequest
import io.homeassistant.companion.android.frontend.permissions.PendingPermissionHandler
import io.homeassistant.companion.android.frontend.permissions.PermissionRequest
import io.homeassistant.companion.android.loading.LoadingScreen
import io.homeassistant.companion.android.util.compose.HAPreviews
import io.homeassistant.companion.android.util.compose.webview.HAWebView
import timber.log.Timber

@VisibleForTesting
const val CONNECTION_SCREEN_TAG = "connection_screen"

@VisibleForTesting
const val CONNECTION_SCREEN_ERROR_PLACEHOLDER_TAG = "connection_screen_error"

private val ICON_SIZE = 64.dp

@Composable
internal fun ConnectionScreen(onBackClick: () -> Unit, viewModel: ConnectionViewModel, modifier: Modifier = Modifier) {
    val url by viewModel.urlFlow.collectAsState()
    val isLoading by viewModel.isLoadingFlow.collectAsState()
    val error by viewModel.errorFlow.collectAsState()
    val pendingFileChooser by viewModel.pendingFileChooser.collectAsState()
    val pendingPermissionRequest by viewModel.pendingPermissionRequest.collectAsState()
    val webViewDebugEnabled by viewModel.webViewDebugEnabled.collectAsState()
    val isError = error != null

    ConnectionScreen(
        url = url,
        isLoading = isLoading,
        isError = isError,
        getWebViewClient = viewModel::getWebViewClient,
        webChromeClient = viewModel.webChromeClient,
        pendingFileChooser = pendingFileChooser,
        pendingPermissionRequest = pendingPermissionRequest,
        onBackClick = onBackClick,
        onWebViewCreationFailed = viewModel::onWebViewCreationFailed,
        webViewDebugEnabled = webViewDebugEnabled,
        modifier = modifier,
    )
}

@Composable
internal fun ConnectionScreen(
    url: String?,
    isLoading: Boolean,
    isError: Boolean,
    getWebViewClient: suspend () -> WebViewClient,
    webChromeClient: WebChromeClient,
    onBackClick: () -> Unit,
    onWebViewCreationFailed: (Throwable) -> Unit,
    webViewDebugEnabled: Boolean?,
    modifier: Modifier = Modifier,
    pendingFileChooser: FileChooserRequest? = null,
    pendingPermissionRequest: PermissionRequest? = null,
) {
    FileChooserEffect(pendingRequest = pendingFileChooser)
    PendingPermissionHandler(pendingRequest = pendingPermissionRequest)

    BoxWithConstraints(modifier = modifier.fillMaxSize().background(Color(0xFF08131F)).testTag(CONNECTION_SCREEN_TAG)) {
        val compact = maxHeight < 600.dp
        val headerHeight = if (compact) 66.dp else 120.dp
        val footerHeight = if (compact) 48.dp else 64.dp
        if (!isError) {
            url?.let {
                var webView by remember { mutableStateOf<WebView?>(null) }
                HAWebView(
                    modifier = Modifier
                        .fillMaxSize()
                        .windowInsetsPadding(WindowInsets.safeDrawing)
                        .padding(top = headerHeight, bottom = footerHeight, start = 16.dp, end = 16.dp),
                    configure = {
                        this.webChromeClient = webChromeClient
                        webView = this
                    },
                    onBackPressed = onBackClick,
                    onWebViewCreationFailed = onWebViewCreationFailed,
                    debuggingEnabled = webViewDebugEnabled,
                )
                webView?.let { view ->
                    LaunchedEffect(view, url) {
                        view.webViewClient = getWebViewClient()
                        view.loadUrl(url)
                    }
                }
            } ?: Timber.i("ConnectionScreen: url is null")
        } else {
            ErrorPlaceholder()
        }

        if (!isError) {
            Column(
                modifier = Modifier.align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .background(Color(0xFF08131F))
                    .padding(horizontal = 24.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Image(
                    imageVector = ImageVector.vectorResource(R.drawable.ic_launcher_foreground),
                    contentDescription = "STEVORA",
                    modifier = Modifier.size(if (compact) 22.dp else 48.dp),
                )
                Text("STEVORA", color = Color.White, fontSize = if (compact) 16.sp else 24.sp, letterSpacing = 3.sp, fontWeight = FontWeight.Bold)
                Text(
                    if (LocalConfiguration.current.locales[0].language == "bg") "СИГУРЕН ВХОД В HOME HUB" else "SECURE HOME HUB SIGN IN",
                    color = Color(0xFF27B9F5),
                    fontSize = 11.sp,
                )
            }
            Row(
                modifier = Modifier.align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .background(Color(0xFF08131F))
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                TextButton(onClick = onBackClick) {
                    Text(if (LocalConfiguration.current.locales[0].language == "bg") "Назад" else "Back", color = Color(0xFF27B9F5))
                }
                Text(
                    if (LocalConfiguration.current.locales[0].language == "bg") "Защитено удостоверяване" else "Protected authentication",
                    color = Color(0xFFA6BCCD),
                    fontSize = 11.sp,
                )
            }
        }
        if (isLoading) {
            LoadingScreen(modifier = Modifier.fillMaxSize())
        }
    }
}

/**
 * This placeholder is used to hide the ugly error screen from the webview, while a toast is displayed
 * before leaving this screen.
 */
@Composable
private fun ErrorPlaceholder() {
    Box(
        modifier = Modifier.fillMaxSize().testTag(CONNECTION_SCREEN_ERROR_PLACEHOLDER_TAG),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            imageVector = ImageVector.vectorResource(R.drawable.ic_launcher_foreground),
            contentDescription = null,
            modifier = Modifier.size(ICON_SIZE),
        )
    }
}

@SuppressLint("MissingOnRenderProcessGone")
@HAPreviews
@Composable
private fun ConnectionScreenPreview() {
    HAThemeForPreview {
        ConnectionScreen(
            url = "https://www.home-assistant.io",
            isLoading = false,
            isError = false,
            getWebViewClient = { WebViewClient() },
            webChromeClient = WebChromeClient(),
            onBackClick = {},
            onWebViewCreationFailed = {},
            webViewDebugEnabled = null,
            modifier = Modifier.fillMaxSize(),
        )
    }
}
