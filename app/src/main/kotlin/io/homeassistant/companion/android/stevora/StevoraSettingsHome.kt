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
import io.homeassistant.companion.android.sensors.LocationSensorManager
import io.homeassistant.companion.android.settings.SettingsActivity
import android.content.Intent
import android.media.MediaRecorder
import java.io.File
import androidx.compose.runtime.collectAsState
import android.content.ComponentName
import android.appwidget.AppWidgetManager
import io.homeassistant.companion.android.widgets.entity.EntityWidget
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.content.pm.PackageManager
import io.homeassistant.companion.android.common.data.servers.firstUrlOrNull
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
    var managersExpanded by remember { mutableStateOf(false) }
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
    if (managersExpanded) {
        StevoraManagersHome(onBack = { managersExpanded = false })
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
            if (!checked) tr("Проверка на правата…", "Checking account permissions…")
            else if (!accountVerified) tr("Профилът не е потвърден", "Account verification unavailable") else accountName,
            fontSize = 14.sp, color = muted,
        )
        if (checked && accountVerified && admin) {
            Text(tr("АДМИНИСТРАТОРСКИ ПАНЕЛ", "ADMIN CONTROL CENTER"), fontSize = 12.sp, color = accent)
            // These entries remain informational until STEVORA's protected
            // memory and automation API contracts are verified and implemented.
            SettingsEntry("Device & Room Manager / Assistant Manager",
                tr("Управление на стаи, устройства и асистенти", "Manage rooms, devices and assistants"),
                "▦", { managersExpanded = true })
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
            SettingsEntry(tr("Климатична статистика", "Climate analytics"), tr("Датчици от този дом и история на измерванията", "Sensors in this home and their history"), "◉", { climateExpanded = true })
            SettingsEntry(tr("Връзка и система", "Connection & Ecosystem"), tr("Връзка, сървър и STEVORA manifest", "Server connection and STEVORA manifest"), "⌂", { nativeSection = "hub" })
            SettingsEntry("Hey Stevora · Voice Training", tr("Гласови образци, асистент и микрофон", "Voice samples, assistant and microphone"), "✦", { nativeSection = "voice" })
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
            SettingsEntry(tr("Климатична статистика", "Climate analytics"), tr("Датчици от този дом и история на измерванията", "Sensors in this home and their history"), "◉", { climateExpanded = true })
            SettingsEntry("Hey Stevora · Voice Training", tr("Запис на гласови образци и настройки", "Record voice samples and configure voice"), "✦", { nativeSection = "voice" })
            SettingsEntry(tr("Устройство и поверителност", "Device & privacy"), tr("Разрешения и известия", "Permissions and notifications"), "◈", { nativeSection = "privacy" })
            SettingsEntry(tr("Приложение и външен вид", "App & appearance"), tr("Език и тема", "Language and theme"), "◐", { nativeSection = "appearance" })
        }
        if (checked && accountVerified) {
            Text(tr("ПРОФИЛ И СИГУРНОСТ", "ACCOUNT & SECURITY"), fontSize = 12.sp, color = accent)
            SettingsEntry(
                tr("Отвори STEVORA OS", "Open STEVORA OS"),
                tr("Системни настройки и профил в свързания дом", "System settings and account in the active home"),
                "⌂",
                {
                    scope.launch {
                        val id = manager.getServer()?.id ?: return@launch
                        val uri = manager.connectionStateProvider(id).urlFlow().firstUrlOrNull()?.toString()?.let(Uri::parse)
                        if (uri != null && uri.scheme in setOf("http", "https") && uri.userInfo == null) {
                            try {
                                context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                            } catch (_: android.content.ActivityNotFoundException) {
                                logoutError = tr("Няма достъпен браузър", "No browser available")
                            }
                        }
                    }
                },
            )
            SettingsInfo(accountName, if (admin) tr("Администратор", "Administrator") else tr("Личен профил", "Personal account"))
            SettingsEntry(
                tr("Изход", "Sign out"),
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
    var manifestStatus by remember { mutableStateOf<String?>(null) }
    var manifestChecking by remember { mutableStateOf(false) }
    val detectedHomes by StevoraHomeProjection.home.collectAsState()
    var recording by remember { mutableStateOf<MediaRecorder?>(null) }
    var pendingAudio by remember { mutableStateOf<File?>(null) }
    val sampleFolder = remember(context) { File(context.filesDir, "stevora/voice-samples").apply { mkdirs() } }
    var sampleCount by remember { mutableStateOf(0) }
    fun refreshCount() {
        sampleCount = sampleFolder.listFiles()?.count { it.isFile && it.name.startsWith("sample_") && it.extension == "m4a" } ?: 0
    }
    fun stopRecording() {
        val active = recording ?: return
        recording = null
        try { active.stop() } catch (_: RuntimeException) { pendingAudio?.delete() }
        active.release()
        pendingAudio = null
        refreshCount()
    }
    fun startRecording() {
        if (recording != null || sampleCount >= 20) return
        val index = (1..20).firstOrNull { !File(sampleFolder, "sample_%02d.m4a".format(it)).exists() } ?: return
        val file = File(sampleFolder, "sample_%02d.m4a".format(index))
        var instance: MediaRecorder? = null
        try {
            instance = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(context) else MediaRecorder()
            instance.setAudioSource(MediaRecorder.AudioSource.MIC)
            instance.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            instance.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            instance.setOutputFile(file.absolutePath)
            instance.prepare()
            instance.start()
            recording = instance
            pendingAudio = file
        } catch (_: Exception) {
            instance?.release()
            file.delete()
            saveStatus = tr("Неуспешен запис от микрофона", "Microphone recording failed")
        }
    }
    LaunchedEffect(section) { refreshCount() }
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
        val primaryUrl = current?.connection?.externalUrl.orEmpty()
        remoteUrl = primaryUrl.takeUnless { it == localUrl && it.startsWith("http://", ignoreCase = true) }.orEmpty()
        preferLocal = current?.connection?.prioritizeInternal ?: false
        micAllowed = context.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        notificationsAllowed = if (Build.VERSION.SDK_INT >= 33) {
            context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        } else true
        if (section == "hub" && current != null) {
            manifestChecking = true
            manifestStatus = tr("Проверка на STEVORA manifest…", "Checking STEVORA manifest…")
            try {
                val json = manager.webSocketRepository(current.id).getStevoraManifestJson(if (bg) "bg" else "en")
                val valid = json != null && StevoraHomeProjection.apply(current.id, json)
                if (!valid) StevoraHomeProjection.clear(current.id)
                manifestStatus = if (valid) tr(
                    "STEVORA manifest е получен и валидиран.",
                    "STEVORA manifest received and validated.",
                ) else tr(
                    "Няма валиден manifest. Проверете интеграцията и администраторските права.",
                    "No valid manifest. Check integration setup and administrator permissions.",
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                StevoraHomeProjection.clear(current.id)
                manifestStatus = tr("Грешка при проверка: ", "Discovery failed: ") +
                    (e.message ?: "Unknown error")
            } finally {
                manifestChecking = false
            }
        }
        if (section == "voice" && current != null) {
            try {
                val socket = manager.webSocketRepository(current.id)
                pipelines = listOfNotNull(socket.resolveStevoraPipeline()?.name)
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
    fun isPrivateLanHost(host: String?): Boolean {
        val parts = host?.split('.') ?: return false
        if (parts.size != 4) return false
        val bytes = parts.map { it.toIntOrNull()?.takeIf { octet -> octet in 0..255 && octet.toString() == it } ?: return false }
        return bytes[0] == 10 || (bytes[0] == 172 && bytes[1] in 16..31) || (bytes[0] == 192 && bytes[1] == 168)
    }
    fun validUrl(value: String, allowHttp: Boolean): Boolean {
        if (value.isBlank()) return true
        return try {
            val uri = URI(value.trim())
            val scheme = uri.scheme?.lowercase()
            (scheme == "https" || (allowHttp && scheme == "http" && isPrivateLanHost(uri.host))) &&
                !uri.host.isNullOrBlank() && uri.userInfo == null &&
                uri.fragment == null && uri.query == null
        } catch (_: Exception) { false }
    }
    val title = when (section) {
        "hub" -> "Connection & Ecosystem"
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
                val activeServerId = server?.id
                SettingsInfo("STEVORA Ecosystem", if (activeServerId == null) {
                    tr("Няма свързан сървър", "No server connected")
                } else if (detectedHomes.containsKey(activeServerId)) {
                    tr("STEVORA manifest потвърден · инсталация ", "STEVORA manifest confirmed · installation ") +
                        detectedHomes[activeServerId]?.installationId.orEmpty()
                } else {
                    tr("Не е потвърден STEVORA manifest · режимът е неизвестен", "STEVORA manifest not confirmed · server mode unknown")
                })
                // Registry-only discovery preview: no device actions or inferred permissions.
                // Always scope the projection to the active Home Assistant server.
                val discovered = activeServerId?.let { detectedHomes[it] }
                if (discovered != null) {
                    val mappedCount = discovered.entityMappings.values.sumOf { it.size }
                    SettingsInfo(
                        tr("Открит дом", "Discovered home"),
                        tr("Стаи: ", "Rooms: ") + discovered.rooms.size +
                            tr(" · Свързани обекти: ", " · Mapped entities: ") + mappedCount +
                            " · Manifest v" + discovered.revision,
                    )
                    // Expand one discovered room at a time. The manifest contains
                    // entity IDs, not live states or service permissions.
                    var expandedRoomId by remember(activeServerId) { mutableStateOf<String?>(null) }
                    discovered.rooms.forEach { room ->
                        val entityIds = discovered.entityMappings[room.id].orEmpty()
                        OutlinedButton(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = {
                                expandedRoomId = if (expandedRoomId == room.id) null else room.id
                            },
                        ) {
                            Text(
                                room.name + " · " + entityIds.size + " " +
                                    tr("обекта", "entities") +
                                    if (expandedRoomId == room.id) " ▲" else " ▼",
                            )
                        }
                        if (expandedRoomId == room.id) {
                            if (entityIds.isEmpty()) {
                                Text(
                                    tr("Няма открити обекти", "No discovered entities"),
                                    color = muted, fontSize = 12.sp,
                                )
                            } else {
                                entityIds.groupBy { it.substringBefore('.') }
                                    .toSortedMap()
                                    .forEach { (domain, ids) ->
                                        Text(
                                            domain + " (" + ids.size + ")",
                                            color = foreground, fontSize = 13.sp,
                                        )
                                        ids.sorted().forEach { entityId ->
                                            Text(discovered.localizedEntityNames[entityId] ?: entityId, color = muted, fontSize = 12.sp)
                                        }
                                    }
                            }
                        }
                    }
                    Text(
                        tr(
                            "Това е преглед само за четене. Управлението на устройства се разрешава отделно от Home Assistant.",
                            "Read-only discovery. Device control is authorized separately by Home Assistant.",
                        ),
                        color = muted,
                        fontSize = 12.sp,
                    )
                }
                if (manifestStatus != null) Text(manifestStatus.orEmpty(), color = muted, fontSize = 13.sp)
                if (manifestChecking) CircularProgressIndicator()
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
                            (localUrl.isNotBlank() || remoteUrl.isNotBlank()),
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
                                                externalUrl = remoteUrl.ifBlank { localUrl },
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
                StevoraSectionHeading("Hey Stevora · Voice Training", tr("Нативен запис на устройството", "Native on-device recording"))
                SettingsInfo(tr("Гласови образци", "Voice samples"), "$sampleCount / 20")
                Button(
                    enabled = micAllowed && (recording != null || sampleCount < 20),
                    onClick = { if (recording != null) stopRecording() else startRecording() },
                ) { Text(if (recording != null) tr("Спри и запази", "Stop and save") else tr("Запиши образец", "Record sample")) }
                OutlinedButton(
                    enabled = recording == null && sampleCount > 0,
                    onClick = {
                        sampleFolder.listFiles()?.filter { it.name.startsWith("sample_") && it.extension == "m4a" }
                            ?.maxByOrNull { it.name }?.delete()
                        refreshCount()
                    },
                ) { Text(tr("Изтрий последния запис", "Delete latest sample")) }
                SettingsInfo(tr("Обучение и активиране", "Training and activation"), tr(
                    "Все още няма интегриран обучаващ модел. Записите са реални, но не активират Hey Stevora.",
                    "No training engine is integrated yet. Recordings are real but do not activate Hey Stevora.",
                ))
                if (saveStatus.isNotBlank()) Text(saveStatus, color = muted)
                StevoraSectionHeading("Assist pipelines", pipelineStatus)
                pipelines.forEach { SettingsInfo("STEVORA Assist", it) }
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
                SettingsEntry(
                    tr("Споделяне на местоположение", "Location sharing"),
                    tr("Избери за кои сървъри да изпращаш местоположение", "Choose which servers receive background location"),
                    "◎",
                    {
                        val screen = SettingsActivity.Deeplink.Sensor(LocationSensorManager.backgroundLocation.id)
                        context.startActivity(SettingsActivity.newInstance(context, screen).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    },
                )
                SettingsInfo(tr("Микрофон", "Microphone"),
                    if (micAllowed) tr("Разрешен", "Allowed") else tr("Не е разрешен", "Not allowed"))
                if (!micAllowed) Button(onClick = {
                    micPermission.launch(android.Manifest.permission.RECORD_AUDIO)
                }) { Text(tr("Разреши микрофона", "Allow microphone")) }
                StevoraCopyFcmToken(bg)
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
                            selected = bg == (code == "bg"),
                            onClick = {
                                scope.launch {
                                    try {
                                        val id = manager.getServer()?.id ?: return@launch
                                        if (manager.webSocketRepository(id).saveStevoraLanguage(code) && manager.getServer()?.id == id) {
                                            EntryPointAccessors.fromApplication(
                                                context.applicationContext, StevoraServerEntryPoint::class.java,
                                            ).languagesManager().saveLang(code)
                                        } else {
                                            saveStatus = tr("Езикът не бе запазен", "Language could not be saved")
                                        }
                                    } catch (e: CancellationException) {
                                        throw e
                                    } catch (_: Exception) {
                                        saveStatus = tr("Няма връзка с дома", "Home unavailable")
                                    }
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
