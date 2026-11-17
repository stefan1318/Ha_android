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
import androidx.compose.ui.res.stringResource
import io.homeassistant.companion.android.R
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Background = StevoraDesign.background
private val SurfaceColor = StevoraDesign.surface
private val Accent = StevoraDesign.accent
private val Foreground = StevoraDesign.foreground
private val Muted = StevoraDesign.muted

/** Native product landing screen. Server data remains inside the authenticated frontend. */
@Composable
internal fun StevoraPremiumHome(
    onOpenPanel: () -> Unit,
    onOpenVoice: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenOnboarding: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().background(StevoraDesign.gradient)
            .verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Spacer(Modifier.height(12.dp))
        Text("STEVORA", fontSize = 34.sp, color = Foreground)
        Text("HYBRID PREMIUM", fontSize = 13.sp, color = Accent)
        Text(stringResource(R.string.stevora_home_subtitle), color = Muted, fontSize = 15.sp)
        Spacer(Modifier.height(8.dp))
        PremiumTile(stringResource(R.string.stevora_home_hub), stringResource(R.string.stevora_home_hub_summary), stringResource(R.string.stevora_open_panel), onOpenPanel)
        PremiumTile("STEVORA AI", stringResource(R.string.stevora_ai_summary), stringResource(R.string.stevora_open_assistant), onOpenVoice)
        PremiumTile(stringResource(R.string.stevora_personalize), stringResource(R.string.stevora_personalize_summary), stringResource(R.string.stevora_nav_settings), onOpenSettings)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onOpenOnboarding) { Text(stringResource(R.string.stevora_add_hub)) }
        }
        Text(stringResource(R.string.stevora_home_disclaimer), color = Muted, fontSize = 12.sp)
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
