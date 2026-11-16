package io.homeassistant.companion.android.stevora

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import io.homeassistant.companion.android.common.data.integration.Entity
import io.homeassistant.companion.android.common.data.servers.ServerManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.coroutineScope

private val bg = Color(0xFF08131F)
private val tile = Color(0xFF122437)
private val cyan = Color(0xFF27B9F5)
private val white = Color(0xFFF4FAFF)
private val secondary = Color(0xFFA6BCCD)

@EntryPoint
@InstallIn(SingletonComponent::class)
internal interface StevoraServerEntryPoint {
    fun serverManager(): ServerManager
}

/** Uses the existing authenticated Companion server repositories; no URL or token is duplicated. */
@Composable
internal fun StevoraLivePanel() {
    val context = LocalContext.current.applicationContext
    val manager = remember(context) {
        EntryPointAccessors.fromApplication(context, StevoraServerEntryPoint::class.java).serverManager()
    }
    var entities by remember { mutableStateOf<List<Entity>>(emptyList()) }
    var status by remember { mutableStateOf("Connecting to Home Hub…") }
    var busy by remember { mutableStateOf<String?>(null) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    LaunchedEffect(manager) {
        while (isActive) {
            try {
                if (manager.getServer() == null) {
                    entities = emptyList()
                    status = "Connect a Home Hub in Settings"
                } else {
                    val repo = manager.webSocketRepository()
                    val snapshot = repo.getStates()
                    if (snapshot == null) {
                        status = "Hub unavailable — retrying"
                    } else {
                        entities = snapshot.filter { it.domain in setOf("light", "switch", "climate", "sensor", "binary_sensor", "cover", "fan") }
                        status = "Connected • ${entities.size} devices and sensors"
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                status = "Connection unavailable — retrying"
            }
            delay(5_000L)
        }
    }

    fun execute(entity: Entity, service: String, data: Map<String, Any?> = emptyMap()) {
        if (busy != null) return
        busy = entity.entityId
        scope.launch {
            try {
                manager.integrationRepository().callAction(
                    entity.domain,
                    service,
                    data + ("entity_id" to entity.entityId),
                )
                manager.webSocketRepository().getStates()?.let { snapshot ->
                    entities = snapshot.filter { it.domain in setOf("light", "switch", "climate", "sensor", "binary_sensor", "cover", "fan") }
                }
            } catch (e: Exception) {
                status = "Command failed — check the hub connection"
            } finally {
                busy = null
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().background(bg).verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("STEVORA", fontSize = 30.sp, color = white, letterSpacing = 2.sp)
        Text("NATIVE HOME CONTROL", fontSize = 12.sp, color = cyan, letterSpacing = 2.sp)
        Text(status, color = secondary, fontSize = 13.sp)
        val grouped = entities.groupBy { it.domain }
        val order = listOf("climate", "light", "switch", "fan", "cover", "sensor", "binary_sensor")
        order.forEach { domain ->
            val items = grouped[domain].orEmpty()
            if (items.isNotEmpty()) {
                Text(domain.replaceFirstChar { it.uppercase() }.replace('_', ' '), color = cyan, fontSize = 19.sp)
                items.sortedBy { it.attributes["friendly_name"]?.toString() ?: it.entityId }.forEach { entity ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = tile),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(entity.attributes["friendly_name"]?.toString() ?: entity.entityId, color = white, fontSize = 17.sp)
                            Text(entity.state + (entity.attributes["unit_of_measurement"]?.toString() ?: ""), color = secondary, fontSize = 13.sp)
                            val enabled = entity.state !in listOf("unavailable", "unknown")
                            if (domain in setOf("light", "switch", "fan")) {
                                Switch(
                                    checked = entity.state == "on",
                                    enabled = enabled && busy != entity.entityId,
                                    onCheckedChange = { checked -> execute(entity, if (checked) "turn_on" else "turn_off") },
                                )
                            }
                            if (domain == "light" && entity.state == "on" && entity.attributes["brightness"] is Number) {
                                val brightness = (entity.attributes["brightness"] as Number).toFloat()
                                var slider by remember(entity.entityId, brightness) { mutableStateOf(brightness) }
                                Text("Brightness", color = secondary, fontSize = 12.sp)
                                Slider(
                                    value = slider,
                                    onValueChange = { slider = it },
                                    onValueChangeFinished = { execute(entity, "turn_on", mapOf("brightness" to slider.toInt())) },
                                    valueRange = 1f..255f,
                                    enabled = enabled && busy != entity.entityId,
                                )
                            }
                            if (domain == "climate" && enabled) {
                                val target = (entity.attributes["temperature"] as? Number)?.toFloat()
                                val current = entity.attributes["current_temperature"]
                                if (current != null) Text("Room: $current °C", color = secondary)
                                if (target != null) {
                                    val min = (entity.attributes["min_temp"] as? Number)?.toFloat() ?: 16f
                                    val max = (entity.attributes["max_temp"] as? Number)?.toFloat() ?: 30f
                                    var desired by remember(entity.entityId, target) { mutableStateOf(target) }
                                    if (max > min) {
                                        Text("Target: ${desired.toInt()} °C", color = white)
                                        Slider(
                                            value = desired.coerceIn(min, max),
                                            onValueChange = { desired = it },
                                            onValueChangeFinished = {
                                                execute(entity, "set_temperature", mapOf("temperature" to desired.toInt()))
                                            },
                                            valueRange = min..max,
                                            steps = (max - min).toInt().coerceAtLeast(1) - 1,
                                            enabled = busy != entity.entityId,
                                        )
                                    }
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(
                                        enabled = busy != entity.entityId,
                                        onClick = { execute(entity, "turn_off") },
                                    ) { Text("Off") }
                                }
                            }
                            if (domain == "cover" && enabled) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(onClick = { execute(entity, "open_cover") }, enabled = busy != entity.entityId) { Text("Open") }
                                    Button(onClick = { execute(entity, "close_cover") }, enabled = busy != entity.entityId) { Text("Close") }
                                }
                            }
                        }
                    }
                }
            }
        }
        if (entities.isEmpty()) Text("No device states received yet.", color = secondary)
    }
}
