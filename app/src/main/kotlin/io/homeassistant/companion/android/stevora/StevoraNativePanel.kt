package io.homeassistant.companion.android.stevora

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.homeassistant.companion.android.R

private val background = Color(0xFF08131F)
private val surface = Color(0xFF122437)
private val primary = Color(0xFF27B9F5)
private val muted = Color(0xFFA6BCCD)
private val foreground = Color(0xFFF4FAFF)

/**
 * Product-owned, 100% native Compose panel.
 *
 * Never fabricate HA entity values or offer nonfunctional device toggles. The
 * authenticated server frontend remains an explicit fallback until a native
 * entity repository is wired into this screen.
 */
@Composable
internal fun StevoraNativePanel(
    onOpenDevices: () -> Unit,
    onOpenAssistant: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().background(background)
            .verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Text("STEVORA", color = foreground, fontSize = 30.sp, letterSpacing = 2.sp)
        Text("HOME CONTROL", color = primary, fontSize = 12.sp, letterSpacing = 3.sp)
        Spacer(Modifier.height(4.dp))
        Text(stringResource(R.string.stevora_native_welcome), color = foreground, fontSize = 25.sp)
        Text(stringResource(R.string.stevora_native_subtitle), color = muted, fontSize = 14.sp)
        Card(
            colors = CardDefaults.cardColors(containerColor = surface),
            shape = RoundedCornerShape(22.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.stevora_native_hub), color = foreground, fontSize = 19.sp)
                Text(stringResource(R.string.stevora_native_hub_description), color = muted, fontSize = 14.sp)
                Button(
                    onClick = onOpenDevices,
                    colors = ButtonDefaults.buttonColors(containerColor = primary, contentColor = background),
                ) {
                    Text(stringResource(R.string.stevora_native_open_devices))
                    Text("›", modifier = Modifier.padding(start = 8.dp))
                }
            }
        }
        Text(stringResource(R.string.stevora_native_sections), color = foreground, fontSize = 20.sp)
        NativeSection(
            title = stringResource(R.string.stevora_native_climate),
            subtitle = stringResource(R.string.stevora_native_climate_description),
            icon = { Text("❄", color = primary) },
            onClick = onOpenDevices,
        )
        NativeSection(
            title = stringResource(R.string.stevora_native_lights),
            subtitle = stringResource(R.string.stevora_native_lights_description),
            icon = { Text("●", color = primary) },
            onClick = onOpenDevices,
        )
        NativeSection(
            title = stringResource(R.string.stevora_native_devices),
            subtitle = stringResource(R.string.stevora_native_devices_description),
            icon = { Text("▦", color = primary) },
            onClick = onOpenDevices,
        )
        NativeSection(
            title = "STEVORA AI",
            subtitle = stringResource(R.string.stevora_ai_summary),
            icon = { Text("✦", color = primary) },
            onClick = onOpenAssistant,
        )
        Text(
            stringResource(R.string.stevora_native_notice),
            color = muted,
            fontSize = 12.sp,
        )
        Button(
            onClick = onOpenSettings,
            colors = ButtonDefaults.buttonColors(containerColor = surface, contentColor = foreground),
        ) { Text(stringResource(R.string.stevora_nav_settings)) }
    }
}

@Composable
private fun NativeSection(
    title: String,
    subtitle: String,
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = surface),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            icon()
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, color = foreground, fontSize = 17.sp)
                Text(subtitle, color = muted, fontSize = 12.sp)
            }
            Text("›", color = muted)
        }
    }
}
