package io.homeassistant.companion.android.stevora

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Background = Color(0xFF08131F)
private val SurfaceColor = Color(0xFF122437)
private val Accent = Color(0xFF27B9F5)
private val Foreground = Color(0xFFF4FAFF)
private val Muted = Color(0xFFA6BCCD)

/** Native product landing screen. Server data remains inside the authenticated frontend. */
@Composable
internal fun StevoraPremiumHome(
    onOpenPanel: () -> Unit,
    onOpenVoice: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenOnboarding: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().background(Background)
            .verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Spacer(Modifier.height(12.dp))
        Text("STEVORA", fontSize = 34.sp, color = Foreground)
        Text("HYBRID PREMIUM", fontSize = 13.sp, color = Accent)
        Text("Your home. Your control.", color = Muted, fontSize = 15.sp)
        Spacer(Modifier.height(8.dp))
        PremiumTile("Home Hub", "Open your authenticated STEVORA panel", "Open panel", onOpenPanel)
        PremiumTile("STEVORA AI", "Voice and text via the configured Assist pipeline", "Open assistant", onOpenVoice)
        PremiumTile("Personalize", "Configure voice, notifications and your server", "Settings", onOpenSettings)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onOpenOnboarding) { Text("Add a hub") }
        }
        Text("Your devices and automations remain managed by your connected STEVORA server.", color = Muted, fontSize = 12.sp)
    }
}

@Composable
private fun PremiumTile(title: String, description: String, action: String, onClick: () -> Unit) {
    Surface(color = SurfaceColor, shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, color = Foreground, style = MaterialTheme.typography.titleLarge)
            Text(description, color = Muted)
            Button(onClick = onClick, colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Background)) {
                Text(action)
            }
        }
    }
}
