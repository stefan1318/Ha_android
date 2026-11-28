package io.homeassistant.companion.android.stevora

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.collect
import org.json.JSONObject

private val homeBg = StevoraDesign.background
private val homeTile = StevoraDesign.surface
private val homeAccent = StevoraDesign.accent
private val homeText = StevoraDesign.foreground
private val homeMuted = StevoraDesign.muted

private fun adaptiveOverviewNames(payload: String, language: String): Map<String, String>? {
    val catalog = JSONObject(payload)
    if (catalog.optInt("schema_version") != 1 || catalog.optString("installation_id").isBlank()) return null
    val capabilities = catalog.optJSONArray("capabilities") ?: return null
    return buildMap {
        for (index in 0 until capabilities.length()) {
            val capability = capabilities.optJSONObject(index) ?: continue
            val entityId = capability.optString("entity_id")
            if (entityId.isBlank() || entityId.contains("backlight", ignoreCase = true)) continue
            val name = capability.optJSONObject("names")?.optString(language).orEmpty()
            put(entityId, name)
        }
    }
}

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
    var temperatureUnit by remember { mutableStateOf("") }
    var humidity by remember { mutableStateOf("—") }
    var activeLights by remember { mutableStateOf<Int?>(null) }
    var climate by remember { mutableStateOf("—") }
    var fanState by remember { mutableStateOf("—") }

    var climateTitle by remember { mutableStateOf(if (bg) "Климатизация" else "Climate") }
    // Overview derives all measurements from the active HA installation.
    var activeServerId by remember { mutableStateOf<Int?>(null) }
    var manifestGeneration by remember { androidx.compose.runtime.mutableIntStateOf(0) }
    LaunchedEffect(manager, activeServerId) {
        val id = activeServerId ?: return@LaunchedEffect
        manager.webSocketRepository(id).getStevoraManifestChanges()?.collect {
            manifestGeneration++
        }
    }
    LaunchedEffect(manager, bg, manifestGeneration) {
        var previousServerId: Int? = null
        while (isActive) {
            try {
                val server = manager.getServer()
                activeServerId = server?.id
                if (server == null) {
                    previousServerId = null
                    online = false
                    temperature = "—"
                    humidity = "—"
                    activeLights = null
                    climate = "—"
                    fanState = "—"
                } else {
                    if (server.id != previousServerId) {
                        temperature = "—"
                        humidity = "—"
                        activeLights = null
                        climate = "—"
                        fanState = "—"
                        online = false
                        previousServerId = server.id
                    }
                    val repo = manager.webSocketRepository(server.id)
                    val changes = repo.getStateChanges()
                    val snapshot = repo.getStates()
                    if (snapshot == null || changes == null) {
                        online = false
                    } else {
                        val language = if (bg) "bg" else "en"
                        val catalogNames = repo.getStevoraCapabilityCatalogJson(language)
                            ?.let { adaptiveOverviewNames(it, language) }
                        val raw = repo.getStevoraManifestJson(language)
                        val valid = raw != null && StevoraHomeProjection.apply(server.id, raw)
                        if (!valid) StevoraHomeProjection.clear(server.id)
                        val manifest = if (valid) StevoraHomeProjection.home.value[server.id] else null
                        val readableIds = catalogNames?.keys
                            ?: manifest?.entityMappings?.values?.flatten().orEmpty().toSet()
                        val current = snapshot.filter {
                            it.entityId in readableIds &&
                                !it.entityId.contains("backlight", ignoreCase = true)
                        }.associateBy { it.entityId }.toMutableMap()
                        fun publish() {
                            fun available(s: String?): String =
                                s?.takeUnless { it == "unavailable" || it == "unknown" } ?: "—"
                            val temp = current.values.firstOrNull {
                                it.domain == "sensor" && it.attributes["device_class"] == "temperature"
                            }
                            val humid = current.values.firstOrNull {
                                it.domain == "sensor" && it.attributes["device_class"] == "humidity"
                            }
                            val ac = current.values.firstOrNull { it.domain == "climate" }
                            val fan = current.values.firstOrNull { it.domain == "fan" }
                            temperature = available(temp?.state?.takeIf { it.toDoubleOrNull() != null } ?: ac?.attributes?.get("current_temperature")?.toString())
                            temperatureUnit = temp?.attributes?.get("unit_of_measurement") as? String ?: ac?.attributes?.get("temperature_unit") as? String ?: ""
                            humidity = available(humid?.state ?: ac?.attributes?.get("current_humidity")?.toString())
                            climate = when (val mode = ac?.state) {
                                "off" -> if (bg) "Изключено" else "Off"
                                "heat" -> if (bg) "Отопление" else "Heating"
                                "cool" -> if (bg) "Охлаждане" else "Cooling"
                                else -> available(mode).replace('_', ' ')
                            }
                            climateTitle = catalogNames?.get(ac?.entityId)?.takeIf { it.isNotBlank() }
                                ?: manifest?.localizedEntityNames?.get(ac?.entityId)
                                ?: if (bg) "Климатизация" else "Climate"
                            fanState = available(fan?.state)
                            activeLights = current.values.count {
                                it.domain == "light" && it.state == "on"
                            }
                        }
                        online = catalogNames != null || manifest != null
                        publish()
                        kotlinx.coroutines.withTimeoutOrNull(20_000L) {
                            changes.collect { event ->
                                if (event.entityId in readableIds) {
                                    val next = event.newState
                                    if (next == null) current.remove(event.entityId)
                                    else current[event.entityId] = next
                                    publish()
                                }
                            }
                        }
                        online = false
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                online = false
            }
            delay(2_000L)
        }
    }
    Column(
        modifier = Modifier.fillMaxSize().background(StevoraDesign.gradient).verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 22.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("STEVORA", color = homeText, fontSize = 30.sp, letterSpacing = 3.sp)
        Text(if (bg) "HOME • ОБЩ ПРЕГЛЕД" else "HOME • OVERVIEW", color = homeAccent, fontSize = 12.sp)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(if (bg) "МОЯТ ДОМ" else "MY HOME", color = homeMuted, fontSize = 12.sp, letterSpacing = 2.sp)
            Text(if (online) "● ONLINE" else "● OFFLINE",
                color = if (online) Color(0xFF4CE0B3) else Color(0xFFFFA76B), fontSize = 12.sp)
        }
        Card(
            modifier = Modifier.fillMaxWidth(),
            border = BorderStroke(1.dp, StevoraDesign.border),
            shape = RoundedCornerShape(26.dp),
            colors = CardDefaults.cardColors(containerColor = StevoraDesign.surfaceRaised),
        ) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("CLIMATE CENTER", color = homeText, fontSize = 20.sp)
                Text(climateTitle + " • " + (if (bg) "данни в реално време" else "live readings"),
                    color = homeMuted, fontSize = 12.sp)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        val value = temperature.toFloatOrNull()
                        Canvas(modifier = Modifier.size(144.dp)) {
                            drawArc(
                                color = Color(0xFF29425E), startAngle = 135f, sweepAngle = 270f,
                                useCenter = false, style = Stroke(width = 12.dp.toPx(), cap = StrokeCap.Round),
                            )
                            if (value != null) {
                                drawArc(
                                    brush = Brush.sweepGradient(listOf(homeAccent, Color(0xFFFFAE70), homeAccent)),
                                    startAngle = 135f,
                                    sweepAngle = (270f * ((value - 10f) / 25f).coerceIn(0f, 1f)),
                                    useCenter = false,
                                    style = Stroke(width = 12.dp.toPx(), cap = StrokeCap.Round),
                                )
                            }
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(if (temperature == "—") "—" else "$temperature$temperatureUnit", color = homeText, fontSize = 35.sp)
                            Text(if (bg) "В СТАЯТА" else "ROOM TEMP", color = homeMuted, fontSize = 10.sp, letterSpacing = 1.sp)
                        }
                    }
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        ClimateSmallMetric(if (bg) "Влажност" else "Humidity",
                            if (humidity == "—") "—" else "$humidity %")
                        ClimateSmallMetric(if (bg) "Режим" else "Mode", climate)
                    }
                }
            }
        }
        StevoraSectionHeading(
            if (bg) "СЪСТОЯНИЕ" else "STATUS",
            if (bg) "Данни от свързаните устройства" else "Live device readings",
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.weight(1f)) {
                HomeMetric(if (bg) "Включени лампи" else "Lights ON", activeLights?.toString() ?: "—")
            }
            Box(Modifier.weight(1f)) {
                HomeMetric(if (bg) "Вентилатор" else "Fan", fanState)
            }
        }
        StevoraSectionHeading(
            if (bg) "БЪРЗ ДОСТЪП" else "QUICK ACCESS",
            if (bg) "Нативни контроли и асистент" else "Native controls and assistant",
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Button(
                modifier = Modifier.weight(1f).height(54.dp),
                shape = RoundedCornerShape(18.dp),
                onClick = onRooms,
                colors = ButtonDefaults.buttonColors(containerColor = homeAccent, contentColor = homeBg),
            ) { Text(if (bg) "Помещения" else "Rooms") }
            Button(
                modifier = Modifier.weight(1f).height(54.dp),
                shape = RoundedCornerShape(18.dp),
                onClick = onAi,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1B3C61), contentColor = homeText),
            ) { Text("✦ STEVORA AI") }
        }
        OutlinedButton(
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(16.dp),
            onClick = onSystem,
        ) { Text(if (bg) "Диагностика на Home Hub" else "Home Hub diagnostics") }
    }
}

@Composable
private fun HomeMetric(label: String, value: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, Color(0xFF29455A)),
        colors = CardDefaults.cardColors(containerColor = homeTile),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(label, color = homeMuted, fontSize = 12.sp)
            Text(value, color = homeText, fontSize = 22.sp)
        }
    }
}

@Composable
private fun ClimateSmallMetric(label: String, value: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, Color(0xFF29455A)),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0D1C30)),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(value, color = homeText, fontSize = 21.sp)
            Text(label, color = homeMuted, fontSize = 11.sp)
        }
    }
}
