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
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.content.pm.PackageManager
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
    var voiceExpanded by remember { mutableStateOf(false) }
    var nativeSection by remember { mutableStateOf<String?>(null) }
    var climateExpanded by remember { mutableStateOf(false) }
    var automationExpanded by remember { mutableStateOf(false) }
    var memoryExpanded by remember { mutableStateOf(false) }
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
            SettingsEntry("Climate Analytics", tr("HomePod измервания и исторически графики", "HomePod readings and history charts"), "◉", { climateExpanded = true })
            SettingsEntry("Home Hub", tr("Връзка, сървър и профил", "Connection, server and account"), "⌂", { nativeSection = "hub" })
            SettingsEntry("STEVORA AI", tr("Глас и разрешения за микрофона", "Voice and microphone permissions"), "✦", { nativeSection = "voice" })
            SettingsEntry(tr("Устройство и поверителност", "Device & privacy"), tr("Разрешения, известия и сигурност", "Permissions, notifications and security"), "◈", { nativeSection = "privacy" })
            SettingsEntry(tr("Приложение и външен вид", "App & appearance"), tr("Език, тема и системна интеграция", "Language, theme and system integration"), "◐", { nativeSection = "appearance" })
        } else if (checked && accountVerified) {
            Text(tr("ЛИЧНИ НАСТРОЙКИ", "STEVORA PERSONAL"), fontSize = 12.sp, color = accent)
            Text("Device control, climate statistics and the personalized voice assistant.",
                fontSize = 14.sp, color = muted)
            SettingsEntry("Climate Analytics", "Live HomePod readings and session statistics", "◉", { climateExpanded = true })
            SettingsEntry("Voice Assistant", tr("Микрофон и настройки на гласа", "Microphone and voice information"), "✦", { nativeSection = "voice" })
            SettingsEntry(tr("Устройство и поверителност", "Device & privacy"), tr("Разрешения и известия", "Permissions and notifications"), "◈", { nativeSection = "privacy" })
            SettingsEntry(tr("Приложение и външен вид", "App & appearance"), tr("Език и тема", "Language and theme"), "◐", { nativeSection = "appearance" })
        }
        if (checked && accountVerified && voiceExpanded) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = surface),
            ) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(tr("ЛИЧНИ ГЛАСОВИ НАСТРОЙКИ", "PERSONAL VOICE SETTINGS"), color = accent, fontSize = 13.sp)
                    Text(
                        "Your Assist pipeline is selected automatically by your Home Assistant account. " +
                            "These options affect only this device.",
                        color = muted, fontSize = 12.sp,
                    )
                    Text(
                        "Hey STEVORA wake-word recognition is reserved for a later update after voice model training. " +
                            "It is not enabled in Companion 2.0.",
                        color = muted, fontSize = 12.sp,
                    )
                    OutlinedButton(onClick = onVoiceSettings, modifier = Modifier.fillMaxWidth()) {
                        Text(tr("Микрофон и настройки на Assist", "Microphone & Assist setup"))
                    }
                    Text(
                        "Voice settings are personal. Home Hub, AI agents and server configuration remain admin-managed.",
                        color = muted, fontSize = 12.sp,
                    )
                }
            }
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
        Text("STEVORA Companion 2.0 • Release Candidate", fontSize = 12.sp, color = muted)
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
                val server = manager.getServer()
                SettingsInfo(tr("Активен профил", "Active account"), accountName)
                SettingsInfo(
                    tr("Статус", "Status"),
                    if (server == null) tr("Няма свързан Home Hub", "No Home Hub connected")
                    else tr("Home Hub е конфигуриран", "Home Hub configured"),
                )
                if (admin && server != null) {
                    SettingsInfo(tr("Локален адрес", "Local address"),
                        server.connection.internalUrl ?: tr("Не е зададен", "Not configured"))
                    SettingsInfo(tr("Външен адрес", "External address"),
                        server.connection.externalUrl ?: tr("Не е зададен", "Not configured"))
                }
                Text(tr(
                    "Адресите и удостоверяването се управляват сигурно от Companion. За смяна на акаунта използвай Изход от основните настройки.",
                    "Connections and authentication remain securely managed by Companion. To change account, use Sign out in the main settings.",
                ), color = muted, fontSize = 13.sp)
            }
            "voice" -> {
                val granted = context.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) ==
                    PackageManager.PERMISSION_GRANTED
                SettingsInfo(tr("Достъп до микрофона", "Microphone access"),
                    if (granted) tr("Разрешен", "Allowed") else tr("Не е разрешен", "Not allowed"))
                SettingsEntry(tr("Разрешение за микрофона", "Microphone permission"),
                    tr("Отвори Android разрешенията", "Open Android permissions"), "◉") {
                    openSystem(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, true)
                }
                SettingsInfo("Assist", tr(
                    "Използва конфигурацията на Home Assistant акаунта.",
                    "Uses your Home Assistant account's Assist configuration.",
                ))
                SettingsInfo("Hey STEVORA", tr(
                    "Постоянното разпознаване не е активирано — необходим е валидиран модел.",
                    "Always-on wake word is not enabled until a validated model is available.",
                ))
            }
            "privacy" -> {
                val mic = context.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) ==
                    PackageManager.PERMISSION_GRANTED
                SettingsInfo(tr("Микрофон", "Microphone"),
                    if (mic) tr("Разрешен", "Allowed") else tr("Не е разрешен", "Not allowed"))
                SettingsEntry(tr("Известия", "Notifications"),
                    tr("Управление на Android известията", "Manage Android notifications"), "◉") {
                    openSystem(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                }
                SettingsEntry(tr("Разрешения на приложението", "App permissions"),
                    tr("Микрофон, местоположение и други", "Microphone, location and more"), "◇") {
                    openSystem(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, true)
                }
                Text(tr(
                    "STEVORA не съхранява повторно пароли или токени в това меню.",
                    "STEVORA does not duplicate passwords or tokens in this settings screen.",
                ), color = muted, fontSize = 12.sp)
            }
            else -> {
                SettingsInfo(tr("Визуална тема", "Visual theme"),
                    tr("STEVORA Premium Dark — активна", "STEVORA Premium Dark — active"))
                SettingsInfo(tr("Език на интерфейса", "Interface language"),
                    if (bg) "Български" else "English")
                SettingsEntry(tr("Езикови настройки", "Language preferences"),
                    tr("Промяна чрез Android, когато се поддържа", "Change via Android when supported"), "◎") {
                    if (Build.VERSION.SDK_INT >= 33) {
                        openSystem(Settings.ACTION_APP_LOCALE_SETTINGS, true)
                    } else {
                        openSystem(Settings.ACTION_LOCALE_SETTINGS)
                    }
                }
                SettingsEntry(tr("Настройки на приложението", "Application settings"),
                    tr("Android системни разрешения и данни", "Android app permissions and data"), "◇") {
                    openSystem(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, true)
                }
                Text(tr(
                    "Смяната на темата вътре в приложението предстои; показаната тема е действително активната.",
                    "In-app theme switching is not yet supported; the displayed theme is the currently active one.",
                ), color = muted, fontSize = 12.sp)
            }
        }
    }
}
