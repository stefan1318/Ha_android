package io.homeassistant.companion.android.stevora

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.Switch
import androidx.compose.material3.Slider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import android.content.Intent
import android.Manifest
import androidx.compose.runtime.DisposableEffect
import android.content.ComponentName
import android.appwidget.AppWidgetManager
import io.homeassistant.companion.android.widgets.entity.EntityWidget
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.content.pm.PackageManager
import android.app.LocaleManager
import android.os.LocaleList
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.FilterChip
import java.net.URI
import io.homeassistant.companion.android.launch.LaunchActivity.DeepLink
import io.homeassistant.companion.android.launch.LaunchActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CancellationException
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val background = StevoraDesign.background
private val surface = StevoraDesign.surface
private val accent = StevoraDesign.accent
private val foreground = StevoraDesign.foreground
private val muted = StevoraDesign.muted

/**
 * STEVORA-owned settings landing page. The existing Companion settings remain
 * the authoritative source for credentials, sensor permissions and HA options.
 * This UI never duplicates or stores authentication tokens.
 */
@Composable
internal fun StevoraSettingsHome(
    onServerSettings: () -> Unit,
    onVoiceSettings: () -> Unit,
    onSystemSettings: () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current.applicationContext
    val bg = LocalConfiguration.current.locales[0].language == "bg"
    fun tr(bgText: String, enText: String) = if (bg) bgText else enText
    val manager = remember(context) {
        EntryPointAccessors.fromApplication(context, StevoraServerEntryPoint::class.java).serverManager()
    }
    var admin by remember { mutableStateOf(false) }
    var checked by remember { mutableStateOf(false) }
    var accountName by remember { mutableStateOf("—") }
    var nativeSection by remember { mutableStateOf<String?>(null) }
    var climateExpanded by remember { mutableStateOf(false) }
    var automationExpanded by remember { mutableStateOf(false) }
    var memoryExpanded by remember { mutableStateOf(false) }
    var widgetMessage by remember { mutableStateOf<String?>(null) }
    var confirmLogout by remember { mutableStateOf(false) }
    var loggingOut by remember { mutableStateOf(false) }
    var logoutError by remember { mutableStateOf<String?>(null) }
    var accountVerified by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(manager) {
        try {
            val user = if (manager.getServer() != null) manager.webSocketRepository().getCurrentUser() else null
            admin = user?.isAdmin == true || user?.isOwner == true
            accountVerified = user != null
            accountName = user?.name ?: "—"
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            admin = false
            accountVerified = false
        } finally {
            checked = true
        }
    }
    if (nativeSection != null) {
        StevoraNativeSettingsSection(
            section = nativeSection ?: "",
            bg = bg,
            accountName = accountName,
            admin = admin,
            onBack = { nativeSection = null },
        )
        return
    }
    if (memoryExpanded) {
        StevoraMemoryManager(onBack = { memoryExpanded = false })
        return
    }
    if (automationExpanded) {
        StevoraAutomationStudio(onBack = { automationExpanded = false })
        return
    }
    if (climateExpanded) {
        StevoraClimateAnalytics(onBack = { climateExpanded = false })
        return
    }
    if (confirmLogout) {
        AlertDialog(
            onDismissRequest = { if (!loggingOut) confirmLogout = false },
            title = { Text(tr("Изход от STEVORA?", "Sign out of STEVORA?")) },
            text = { Text(tr("Излез от профила и се върни към запазения адрес за вход в Home Hub.", "Sign out and return directly to the saved Home Hub login address.")) },
            confirmButton = {
                Button(
                    enabled = !loggingOut,
                    onClick = {
                        confirmLogout = false
                        loggingOut = true
                        logoutError = null
                        scope.launch {
                            try {
                                val active = manager.getServer() ?: error("No active Home Hub account")
                                try {
                                    manager.authenticationRepository(active.id).revokeSession()
                                } catch (e: CancellationException) {
                                    throw e
                                } catch (_: Exception) {
                                    // Remote revocation can fail offline; local credential removal remains mandatory.
                                }
                                val loginUrl = active.connection.externalUrl
                                    ?: active.connection.internalUrl
                                    ?: error("No saved Home Hub address available")
                                StevoraHubSession.remember(context, active)
                                manager.removeServer(active.id)
                                context.startActivity(
                                    LaunchActivity.newInstance(
                                        context,
                                        DeepLink.OpenOnboarding(
                                            urlToOnboard = loginUrl,
                                            hideExistingServers = false,
                                            skipWelcome = true,
                                        ),
                                    ).apply {
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                                    },
                                )
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                logoutError = "Sign out failed: " + (e.message ?: "Unknown error")
                                loggingOut = false
                            }
                        }
                    },
                ) { Text(tr("Изход", "Sign out")) }
            },
            dismissButton = {
                OutlinedButton(enabled = !loggingOut, onClick = { confirmLogout = false }) { Text(tr("Отказ", "Cancel")) }
            },
        )
    }
    Column(
        modifier = Modifier.fillMaxSize().background(StevoraDesign.gradient)
            .verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("STEVORA", fontSize = 28.sp, color = foreground, letterSpacing = 3.sp)
        Text(tr("НАСТРОЙКИ • КОНТРОЛЕН ЦЕНТЪР", "SETTINGS • CONTROL CENTER"), fontSize = 12.sp, color = accent)
        Text(
            if (!checked) "Checking account permissions…"
            else if (!accountVerified) "Account verification unavailable" else "Account: $accountName • " + (if (admin) "ADMIN" else "USER / PANEL"),
            fontSize = 14.sp, color = muted,
        )
        if (checked && accountVerified && admin) {
            Text(tr("АДМИНИСТРАТОРСКИ ПАНЕЛ", "ADMIN CONTROL CENTER"), fontSize = 12.sp, color = accent)
            // These entries remain informational until STEVORA's protected
            // memory and automation API contracts are verified and implemented.
            SettingsEntry("Memory Manager", tr("Управление на паметта и предложенията", "Manage memory and review proposals"), "▣", { memoryExpanded = true })
            SettingsEntry("Automation Studio", tr("Управление на автоматизациите", "Manage Home Assistant automations"), "◇", { automationExpanded = true })
            SettingsEntry(
                tr("STEVORA widgets", "STEVORA widgets"),
                tr("Добави състояние на устройство на началния екран", "Pin an entity status widget to the home screen"),
                "▦",
                {
                    val appWidgets = AppWidgetManager.getInstance(context)
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O &&
                        appWidgets.isRequestPinAppWidgetSupported
                    ) {
                        val provider = ComponentName(context, EntityWidget::class.java)
                        val requested = appWidgets.requestPinAppWidget(provider, null, null)
                        widgetMessage = if (requested) tr(
                            "Потвърди добавянето на widget от Android.",
                            "Confirm the widget in the Android launcher.",
                        ) else tr(
                            "Launcher-ът отказа заявката. Добави widget през началния екран.",
                            "Launcher declined the request. Add a widget from your home screen.",
                        )
                    } else {
                        widgetMessage = tr(
                            "Отвори Widgets от началния екран и избери STEVORA.",
                            "Open Widgets from your home screen and choose STEVORA.",
                        )
                    }
                },
            )
            widgetMessage?.let { Text(it, color = muted, fontSize = 12.sp) }
            SettingsEntry("Climate Analytics", tr("HomePod измервания и исторически графики", "HomePod readings and history charts"), "◉", { climateExpanded = true })
            SettingsEntry("Home Hub", tr("Връзка, сървър и профил", "Connection, server and account"), "⌂", { nativeSection = "hub" })
            SettingsEntry("STEVORA AI", tr("Глас и разрешения за микрофона", "Voice and microphone permissions"), "✦", { nativeSection = "voice" })
            SettingsEntry(tr("Устройство и поверителност", "Device & privacy"), tr("Разрешения, известия и сигурност", "Permissions, notifications and security"), "◈", { nativeSection = "privacy" })
            SettingsEntry(tr("Приложение и външен вид", "App & appearance"), tr("Език, тема и системна интеграция", "Language, theme and system integration"), "◐", { nativeSection = "appearance" })
        } else if (checked && accountVerified) {
            Text(tr("ЛИЧНИ НАСТРОЙКИ", "STEVORA PERSONAL"), fontSize = 12.sp, color = accent)
            Text("Device control, climate statistics and the personalized voice assistant.",
                fontSize = 14.sp, color = muted)
            SettingsEntry(
                tr("STEVORA widgets", "STEVORA widgets"),
                tr("Добави състояние на устройство на началния екран", "Pin an entity status widget to the home screen"),
                "▦",
                {
                    val appWidgets = AppWidgetManager.getInstance(context)
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O &&
                        appWidgets.isRequestPinAppWidgetSupported
                    ) {
                        val provider = ComponentName(context, EntityWidget::class.java)
                        val requested = appWidgets.requestPinAppWidget(provider, null, null)
                        widgetMessage = if (requested) tr(
                            "Потвърди добавянето на widget от Android.",
                            "Confirm the widget in the Android launcher.",
                        ) else tr(
                            "Launcher-ът отказа заявката. Добави widget през началния екран.",
                            "Launcher declined the request. Add a widget from your home screen.",
                        )
                    } else {
                        widgetMessage = tr(
                            "Отвори Widgets от началния екран и избери STEVORA.",
                            "Open Widgets from your home screen and choose STEVORA.",
                        )
                    }
                },
            )
            widgetMessage?.let { Text(it, color = muted, fontSize = 12.sp) }
            SettingsEntry("Climate Analytics", "Live HomePod readings and session statistics", "◉", { climateExpanded = true })
            SettingsEntry("Voice Assistant", tr("Микрофон и настройки на гласа", "Microphone and voice information"), "✦", { nativeSection = "voice" })
            SettingsEntry(tr("Устройство и поверителност", "Device & privacy"), tr("Разрешения и известия", "Permissions and notifications"), "◈", { nativeSection = "privacy" })
            SettingsEntry(tr("Приложение и външен вид", "App & appearance"), tr("Език и тема", "Language and theme"), "◐", { nativeSection = "appearance" })
        }
        if (checked && accountVerified) {
            Text(tr("ПРОФИЛ И СИГУРНОСТ", "ACCOUNT & SECURITY"), fontSize = 12.sp, color = accent)
            SettingsInfo("Signed in as $accountName", if (admin) "Administrator account" else "Personal account")
            SettingsEntry(
                "Sign out",
                tr("Изход със запазване на настройките за връзка", "Sign out while keeping your Home Hub connection settings"),
                "⇥",
                { confirmLogout = true },
            )
        }
        if (loggingOut) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CircularProgressIndicator()
                Text(tr("Излизане…", "Signing out…"), color = muted)
            }
        }
        logoutError?.let { Text(it, color = Color(0xFFFFA76B), fontSize = 12.sp) }
        SettingsEntry(tr("Към началото", "Back to home"), tr("Връщане към контролния панел", "Return to your command center"), "‹", onBack)
        Text("STEVORA Companion 2.1 • Native Experience", fontSize = 12.sp, color = muted)
    }
}

@Composable
private fun SettingsEntry(
    title: String,
    subtitle: String,
    glyph: String,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = surface),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(glyph, color = accent, fontSize = 24.sp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, color = foreground, fontSize = 17.sp)
                Text(subtitle, color = muted, fontSize = 12.sp)
            }
            Text("›", color = muted, fontSize = 22.sp)
        }
    }
}

@Composable
private fun SettingsInfo(title: String, subtitle: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = surface),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(title, color = foreground, fontSize = 17.sp)
            Text(subtitle, color = muted, fontSize = 12.sp)
        }
    }
}

/**
 * Native STEVORA settings destinations. Android-protected permissions are opened
 * only through the OS's official consent screens; credentials stay in Companion.
 */
@Composable
private fun StevoraNativeSettingsSection(
    section: String,
    bg: Boolean,
    accountName: String,
    admin: Boolean,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val manager = remember(context) {
        EntryPointAccessors.fromApplication(
            context.applicationContext, StevoraServerEntryPoint::class.java,
        ).serverManager()
    }
    fun tr(a: String, b: String) = if (bg) a else b
    fun openSystem(action: String, packageUri: Boolean = false) {
        try {
            val intent = Intent(action).apply {
                if (packageUri) data = Uri.parse("package:" + context.packageName)
                if (action == Settings.ACTION_APP_NOTIFICATION_SETTINGS) {
                    putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                }
            }
            context.startActivity(intent)
        } catch (_: Exception) {
            context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:" + context.packageName)
            })
        }
    }
    val scope = rememberCoroutineScope()
    var server by remember { mutableStateOf<io.homeassistant.companion.android.database.server.Server?>(null) }
    var localUrl by remember { mutableStateOf("") }
    var remoteUrl by remember { mutableStateOf("") }
    var preferLocal by remember { mutableStateOf(false) }
    var saveStatus by remember { mutableStateOf("") }
    var confirmConnection by remember { mutableStateOf(false) }
    var pipelines by remember { mutableStateOf<List<String>>(emptyList()) }
    var pipelineStatus by remember { mutableStateOf("") }
    var micAllowed by remember { mutableStateOf(false) }
    var notificationsAllowed by remember { mutableStateOf(false) }
    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        micAllowed = it
    }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        notificationsAllowed = it
    }
    LaunchedEffect(section) {
        val current = manager.getServer()
        server = current
        localUrl = current?.connection?.internalUrl.orEmpty()
        remoteUrl = current?.connection?.externalUrl.orEmpty()
        preferLocal = current?.connection?.prioritizeInternal ?: false
        micAllowed = context.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        notificationsAllowed = if (Build.VERSION.SDK_INT >= 33) {
            context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        } else true
        if (section == "voice" && current != null) {
            try {
                val socket = manager.webSocketRepository()
                val user = socket.getCurrentUser()
                val engine = if (user?.isAdmin == true || user?.isOwner == true)
                    "conversation.living_room_openai_conversation"
                else "conversation.openai_conversation_for_panels"
                val all = socket.getAssistPipelines()?.pipelines.orEmpty()
                pipelines = all.filter { it.conversationEngine.equals(engine, ignoreCase = true) }
                    .map { it.name + " • " + it.id }
                pipelineStatus = if (pipelines.isEmpty())
                    tr("Няма съвместим Assist pipeline за този профил", "No role-compatible Assist pipeline")
                else tr("Изборът е защитен според ролята на акаунта", "Pipeline selection is role-restricted")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                pipelineStatus = e.message ?: "Assist unavailable"
            }
        }
    }
    fun validUrl(value: String, allowHttp: Boolean): Boolean {
        if (value.isBlank()) return true
        return try {
            val uri = URI(value.trim())
            val scheme = uri.scheme?.lowercase()
            (scheme == "https" || (allowHttp && scheme == "http")) &&
                !uri.host.isNullOrBlank() && uri.userInfo == null &&
                uri.fragment == null && uri.query == null
        } catch (_: Exception) { false }
    }
    val title = when (section) {
        "hub" -> "Home Hub"
        "voice" -> "STEVORA AI"
        "privacy" -> tr("Устройство и поверителност", "Device & privacy")
        else -> tr("Приложение и външен вид", "App & appearance")
    }
    Column(
        modifier = Modifier.fillMaxSize().background(StevoraDesign.gradient)
            .verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        OutlinedButton(onClick = onBack) { Text(tr("‹ Назад", "‹ Back")) }
        Text(title, fontSize = 27.sp, color = foreground)
        Text("STEVORA • NATIVE SETTINGS", color = accent, fontSize = 11.sp)
        when (section) {
            "hub" -> {
                SettingsInfo(tr("Активен профил", "Active account"), accountName)
                if (server == null) {
                    SettingsInfo("Home Hub", tr("Няма свързан сървър", "No server connected"))
                } else {
                    OutlinedTextField(
                        value = localUrl,
                        onValueChange = { localUrl = it.trim().take(512) },
                        label = { Text(tr("Локален адрес", "Local URL")) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = remoteUrl,
                        onValueChange = { remoteUrl = it.trim().take(512) },
                        label = { Text(tr("Външен адрес (HTTPS)", "External URL (HTTPS)")) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(checked = preferLocal, onCheckedChange = { preferLocal = it })
                        Text(tr("Предпочитай локалната връзка", "Prefer local connection"),
                            modifier = Modifier.padding(start = 12.dp), color = foreground)
                    }
                    Button(
                        enabled = validUrl(localUrl, true) && validUrl(remoteUrl, false) &&
                            remoteUrl.isNotBlank(),
                        onClick = { confirmConnection = true },
                    ) { Text(tr("Запази връзката", "Save connection")) }
                    Text(tr(
                        "Проверявай внимателно адресите. Грешна промяна може да прекъсне достъпа.",
                        "Check URLs carefully. Incorrect values may interrupt access.",
                    ), color = muted, fontSize = 12.sp)
                    if (confirmConnection) {
                        AlertDialog(
                            onDismissRequest = { confirmConnection = false },
                            title = { Text(tr("Промяна на Home Hub", "Update Home Hub")) },
                            text = { Text(tr("Запази новите адреси за връзка?", "Save the new connection addresses?")) },
                            confirmButton = {
                                Button(onClick = {
                                    confirmConnection = false
                                    scope.launch {
                                        try {
                                            val existing = server ?: error("No server")
                                            val updated = existing.copy(connection = existing.connection.copy(
                                                internalUrl = localUrl.ifBlank { null },
                                                externalUrl = remoteUrl,
                                                prioritizeInternal = preferLocal,
                                            ))
                                            manager.updateServer(updated)
                                            server = updated
                                            saveStatus = tr("Връзката е записана", "Connection saved")
                                        } catch (e: Exception) {
                                            saveStatus = e.message ?: tr("Грешка при запис", "Save failed")
                                        }
                                    }
                                }) { Text(tr("Потвърди", "Confirm")) }
                            },
                            dismissButton = {
                                OutlinedButton(onClick = { confirmConnection = false }) {
                                    Text(tr("Отказ", "Cancel"))
                                }
                            },
                        )
                    }
                    if (saveStatus.isNotEmpty()) Text(saveStatus, color = accent)
                }
            }
            "voice" -> {
                SettingsInfo(tr("Достъп до микрофона", "Microphone access"),
                    if (micAllowed) tr("Разрешен", "Allowed") else tr("Не е разрешен", "Not allowed"))
                if (!micAllowed) {
                    Button(onClick = { micPermission.launch(android.Manifest.permission.RECORD_AUDIO) }) {
                        Text(tr("Разреши микрофона", "Allow microphone"))
                    }
                }
                StevoraSectionHeading("Assist pipelines", pipelineStatus)
                pipelines.forEach { SettingsInfo("STEVORA Assist", it) }
                StevoraVoiceIdPanel(bg, micAllowed, { micPermission.launch(Manifest.permission.RECORD_AUDIO) })
                SettingsInfo("Hey STEVORA", tr(
                    "Постоянното разпознаване остава изключено до наличен валидиран модел.",
                    "Always-on wake-word detection remains disabled until a validated model exists.",
                ))
                Text(tr(
                    "Гласовите команди използват Assist pipeline според ролята на акаунта. Промяна на сървърния pipeline изисква отделна администраторска конфигурация.",
                    "Voice commands use a role-matched Assist pipeline. Server pipeline changes require separate administrator configuration.",
                ), color = muted, fontSize = 12.sp)
            }
            "privacy" -> {
                SettingsInfo(tr("Микрофон", "Microphone"),
                    if (micAllowed) tr("Разрешен", "Allowed") else tr("Не е разрешен", "Not allowed"))
                if (!micAllowed) Button(onClick = {
                    micPermission.launch(android.Manifest.permission.RECORD_AUDIO)
                }) { Text(tr("Разреши микрофона", "Allow microphone")) }
                SettingsInfo(tr("Известия", "Notifications"),
                    if (notificationsAllowed) tr("Разрешени", "Allowed") else tr("Не са разрешени", "Not allowed"))
                if (!notificationsAllowed && Build.VERSION.SDK_INT >= 33) {
                    Button(onClick = {
                        notificationPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                    }) { Text(tr("Разреши известията", "Allow notifications")) }
                }
                Text(tr(
                    "Android управлява отнемането на разрешения и допълнителните ограничения. STEVORA не може да ги заобикаля.",
                    "Android controls permission revocation and additional restrictions. STEVORA cannot bypass them.",
                ), color = muted, fontSize = 12.sp)
            }
            else -> {
                SettingsInfo(tr("Визуална тема", "Visual theme"), "STEVORA Premium Dark")
                Text(tr("Език на приложението", "App language"), color = foreground)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    listOf("bg" to "Български", "en" to "English").forEach { (code, label) ->
                        FilterChip(
                            selected = if (Build.VERSION.SDK_INT >= 33) {
                                val lm = context.getSystemService(LocaleManager::class.java)
                                lm.applicationLocales.toLanguageTags().startsWith(code)
                            } else bg == (code == "bg"),
                            onClick = {
                                if (Build.VERSION.SDK_INT >= 33) {
                                    context.getSystemService(LocaleManager::class.java)
                                        .applicationLocales = LocaleList.forLanguageTags(code)
                                } else {
                                    saveStatus = tr(
                                        "Изборът на език в приложението изисква Android 13+.",
                                        "In-app language selection requires Android 13+.",
                                    )
                                }
                            },
                            label = { Text(label) },
                        )
                    }
                }
                if (saveStatus.isNotEmpty()) Text(saveStatus, color = accent)
                Text(tr(
                    "Темата STEVORA Premium Dark е единната продуктова тема. Системните разрешения не се променят без съгласие.",
                    "STEVORA Premium Dark is the unified product theme. System permissions are never changed without consent.",
                ), color = muted, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun StevoraVoiceIdPanel(bg: Boolean, micAllowed: Boolean, askPermission: () -> Unit) {
    val context = LocalContext.current
    fun tr(a: String, b: String) = if (bg) a else b
    val profiles = remember { StevoraVoiceProfiles(context.applicationContext) }
    val recorder = remember { StevoraVoiceEnrollment(context.applicationContext) }
    var revision by remember { mutableStateOf(0) }
    var name by remember { mutableStateOf("") }
    var notice by remember { mutableStateOf("") }
    var deleting by remember { mutableStateOf(false) }
    var playing by remember { mutableStateOf(false) }
    var activePlayer by remember { mutableStateOf<android.media.MediaPlayer?>(null) }
    val speakers = remember(revision) { profiles.all() }
    val selected = remember(revision) { profiles.selected() }
    LaunchedEffect(selected?.id) {
        if (recorder.isRecording) recorder.stop(save = false)
        recorder.profileId = selected?.id
    }
    DisposableEffect(recorder) { onDispose {
        recorder.stop(save = false)
        activePlayer?.runCatching { stop(); release() }
    } }
    StevoraSectionHeading(tr("Voice ID • Гласово обучение", "Voice ID • Voice enrollment"),
        tr("До 12 гласа на общ панел", "Up to 12 speakers per shared panel"))
    speakers.forEach { speaker ->
        FilterChip(
            selected = selected?.id == speaker.id,
            onClick = {
                if (recorder.isRecording) recorder.stop(save = false)
                profiles.select(speaker.id)
                revision++
            },
            label = { Text(speaker.name) },
        )
    }
    OutlinedTextField(
        value = name,
        onValueChange = { name = it.take(40) },
        label = { Text(tr("Нов говорител", "New speaker")) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
    )
    OutlinedButton(enabled = name.isNotBlank() && speakers.size < 12, onClick = {
        try {
            val speaker = profiles.add(name)
            profiles.select(speaker.id)
            name = ""
            revision++
        } catch (e: Exception) { notice = e.message ?: "Unable to add speaker" }
    }) { Text(tr("Добави гласов профил", "Add voice profile")) }
    if (selected != null) {
        SettingsInfo(tr("Записи за ", "Samples for ") + selected.name,
            "${recorder.sampleCount} / ${StevoraVoiceEnrollment.MAX_SAMPLES}")
        if (!micAllowed) {
            Button(onClick = askPermission) { Text(tr("Разреши микрофона", "Allow microphone")) }
        } else {
            Button(
                enabled = recorder.isRecording || recorder.sampleCount < StevoraVoiceEnrollment.MAX_SAMPLES,
                onClick = {
                    if (recorder.isRecording) {
                        val saved = recorder.stop()
                        notice = if (saved) tr("Аудиото е проверено и запазено.", "Verified audio saved.")
                            else tr("Невалиден запис, не е запазен.", "Invalid recording discarded.")
                    } else {
                        try {
                            recorder.profileId = selected.id
                            recorder.start()
                            notice = tr("Записва се от микрофона. Кажи Hey Stevora.", "Microphone recording. Say Hey Stevora.")
                        } catch (e: Exception) { notice = tr("Грешка при запис: ", "Recording error: ") + e.message }
                    }
                    revision++
                },
            ) {
                Text(if (recorder.isRecording) tr("■ Спри и провери", "■ Stop and verify")
                    else tr("● Запиши Hey Stevora", "● Record Hey Stevora"))
            }
        }
        OutlinedButton(
            enabled = !recorder.isRecording && recorder.sampleCount > 0 && !playing,
            onClick = {
                try {
                    playing = true
                    activePlayer = recorder.playLatest {
                        playing = false
                        activePlayer = null
                    }
                    if (activePlayer == null) playing = false
                } catch (e: Exception) {
                    playing = false
                    notice = tr("Грешка при възпроизвеждане: ", "Playback error: ") + e.message
                }
            },
        ) { Text(if (playing) tr("Възпроизвежда се...", "Playing...")
            else tr("▶ Прослушай последния запис", "▶ Play latest recording")) }
        if (recorder.sampleCount == StevoraVoiceEnrollment.MAX_SAMPLES) {
            SettingsInfo(tr("Събирането е завършено", "Collection complete"),
                tr("20 проверени аудиофайла. Предстои модел за разпознаване и независима проверка. Активацията Hey Stevora още не е включена.",
                    "20 verified audio files. Speaker model and independent validation pending. Hey Stevora activation is not enabled."))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(tr("Съгласие за бъдещо самообучение", "Consent for future self-learning"),
                color = foreground, modifier = Modifier.weight(1f), fontSize = 13.sp)
            Switch(checked = selected.learningEnabled, onCheckedChange = {
                profiles.setLearningConsent(selected.id, it)
                revision++
            })
        }
        Text(tr("Реално самообучение и разпознаване още не са активни.",
            "Recognition and automatic learning are not active yet."), color = muted, fontSize = 12.sp)
        OutlinedButton(onClick = { deleting = true }) {
            Text(tr("Изтрий говорителя и записите", "Delete speaker and samples"))
        }
        if (deleting) AlertDialog(
            onDismissRequest = { deleting = false },
            title = { Text(tr("Изтриване на профила?", "Delete profile?")) },
            text = { Text(tr("Записите ще бъдат изтрити.", "All samples will be deleted.")) },
            confirmButton = {
                Button(onClick = {
                    recorder.stop(save = false)
                    recorder.profileId = selected.id
                    recorder.deleteAll()
                    profiles.remove(selected.id)
                    deleting = false
                    revision++
                }) { Text(tr("Изтрий", "Delete")) }
            },
            dismissButton = { OutlinedButton(onClick = { deleting = false }) { Text(tr("Отказ", "Cancel")) } },
        )
    }
    if (notice.isNotBlank()) Text(notice, color = accent, fontSize = 12.sp)
}
