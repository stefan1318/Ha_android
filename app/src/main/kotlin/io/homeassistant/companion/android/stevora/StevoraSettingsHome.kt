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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val background = Color(0xFF08131F)
private val surface = Color(0xFF122437)
private val accent = Color(0xFF27B9F5)
private val foreground = Color(0xFFF4FAFF)
private val muted = Color(0xFFA6BCCD)

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
    Column(
        modifier = Modifier.fillMaxSize().background(background)
            .verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("STEVORA", fontSize = 28.sp, color = foreground, letterSpacing = 3.sp)
        Text("SETTINGS • CONTROL CENTER", fontSize = 12.sp, color = accent)
        Text("Manage your hub, voice assistant and device permissions.",
            fontSize = 14.sp, color = muted)
        SettingsEntry("Home Hub", "Server connection and authentication", "⌂", onServerSettings)
        SettingsEntry("STEVORA AI", "Voice pipeline and wake word preferences", "✦", onVoiceSettings)
        SettingsEntry("Device & privacy", "Permissions, sensors and advanced Companion settings", "◈", onSystemSettings)
        SettingsEntry("Back to home", "Return to your command center", "‹", onBack)
        Text("STEVORA Companion 2.0 • Preview", fontSize = 12.sp, color = muted)
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
