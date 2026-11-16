package io.homeassistant.companion.android.stevora

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

private val homeBg = Color(0xFF08131F)
private val homeTile = Color(0xFF122437)
private val homeAccent = Color(0xFF27B9F5)
private val homeText = Color(0xFFF4FAFF)
private val homeMuted = Color(0xFFA6BCCD)

/** Overview only: detailed controls belong to Rooms, not Home. */
@Composable
internal fun StevoraHomeOverview(
    onRooms: () -> Unit,
    onAi: () -> Unit,
    onSystem: () -> Unit,
) {
    val context = LocalContext.current.applicationContext
    val bg = LocalConfiguration.current.locales[0].language == "bg"
    val manager = remember(context) {
        EntryPointAccessors.fromApplication(context, StevoraServerEntryPoint::class.java).serverManager()
    }
    var online by remember { mutableStateOf(false) }
    var temperature by remember { mutableStateOf("—") }
    var humidity by remember { mutableStateOf("—") }
    var activeLights by remember { mutableStateOf<Int?>(null) }
    var climate by remember { mutableStateOf("—") }
    var bathroomFan by remember { mutableStateOf("—") }

    LaunchedEffect(manager) {
        while (isActive) {
            try {
                val states = if (manager.getServer() != null) manager.webSocketRepository().getStates() else null
                online = states != null
                if (states != null) {
                    fun state(id: String): String = states.firstOrNull { it.entityId == id }?.state
                        ?.takeUnless { it == "unavailable" || it == "unknown" } ?: "—"
                    temperature = state("sensor.realna_temperatura_homepod")
                    humidity = state("sensor.realna_vlazhnost_homepod")
                    climate = state("climate.air_conditioner")
                    bathroomFan = state("fan.bathroom_switch_1")
                    activeLights = states.count {
                        it.entityId in setOf(
                            "light.rgb_cct", "light.living_room_switch_1",
                            "light.living_room_switch_2", "light.living_room_switch_3",
                            "light.wifi_bel_2gang_switch_2", "light.terrace_switch_1",
                            "light.bathroom_switch_2", "light.wifi_bel_2gang_switch_1",
                        ) && it.state == "on"
                    }
                } else {
                    temperature = "—"; humidity = "—"; activeLights = null
                    climate = "—"; bathroomFan = "—"
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                online = false
                temperature = "—"; humidity = "—"; activeLights = null
                climate = "—"; bathroomFan = "—"
            }
            delay(5_000L)
        }
    }
    Column(
        modifier = Modifier.fillMaxSize().background(homeBg).verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 22.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("STEVORA", color = homeText, fontSize = 30.sp, letterSpacing = 3.sp)
        Text(if (bg) "HOME • ОБЩ ПРЕГЛЕД" else "HOME • OVERVIEW", color = homeAccent, fontSize = 12.sp)
        HomeMetric(if (bg) "Home Hub" else "Home Hub", if (online) "● ONLINE" else "● OFFLINE")
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            androidx.compose.foundation.layout.Box(Modifier.weight(1f)) {
                HomeMetric(if (bg) "Температура" else "Temperature", if (temperature == "—") "—" else "$temperature °C")
            }
            androidx.compose.foundation.layout.Box(Modifier.weight(1f)) {
                HomeMetric(if (bg) "Влажност" else "Humidity", if (humidity == "—") "—" else "$humidity %")
            }
        }
        HomeMetric(if (bg) "Включени светлини" else "Lights on", activeLights?.toString() ?: "—")
        HomeMetric(if (bg) "Климатик" else "Climate", climate)
        HomeMetric(if (bg) "Вентилатор баня" else "Bathroom fan", bathroomFan)
        Button(
            modifier = Modifier.fillMaxWidth(),
            onClick = onRooms,
            colors = ButtonDefaults.buttonColors(containerColor = homeAccent, contentColor = homeBg),
        ) { Text(if (bg) "Управление на помещенията" else "Control rooms") }
        Button(modifier = Modifier.fillMaxWidth(), onClick = onAi) {
            Text(if (bg) "STEVORA AI асистент" else "STEVORA AI Assistant")
        }
        Button(modifier = Modifier.fillMaxWidth(), onClick = onSystem) {
            Text(if (bg) "Състояние на системата" else "System status")
        }
    }
}

@Composable
private fun HomeMetric(label: String, value: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = homeTile),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(label, color = homeMuted, fontSize = 12.sp)
            Text(value, color = homeText, fontSize = 22.sp)
        }
    }
}
