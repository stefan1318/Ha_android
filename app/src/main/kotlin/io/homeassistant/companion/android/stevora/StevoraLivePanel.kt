package io.homeassistant.companion.android.stevora

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
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
import kotlin.math.roundToInt

private val bg = Color(0xFF08131F)
private val tile = Color(0xFF122437)
private val panelBorder = Color(0xFF29455A)
private val cyan = Color(0xFF27B9F5)
private val white = Color(0xFFF4FAFF)
private val secondary = Color(0xFFA6BCCD)

// This is a curated STEVORA panel, NOT the HA entity/device inspector.
// Add a control here only when it belongs in the STEVORA user panel.
// Do not add backlights, duplicate switch channels, diagnostic entities or raw sensors.
private data class StevoraControl(
    val id: String,
    val room: String,
    val labelBg: String,
    val labelEn: String,
)

private val roomOrder = listOf("living", "kitchen", "hall", "bathroom", "terrace")
private val roomNames = mapOf(
    "living" to ("Хол" to "Living Room"),
    "kitchen" to ("Кухня" to "Kitchen"),
    "hall" to ("Коридор" to "Hall"),
    "bathroom" to ("Баня" to "Bathroom"),
    "terrace" to ("Тераса" to "Terrace"),
)

// Explicit IDs avoid re-introducing things removed from the STEVORA dashboard.
// The room here is intentional: HA currently assigns a terrace light to Kitchen.
private val panelControls = listOf(
    StevoraControl("climate.air_conditioner", "living", "Климатик", "Air conditioner"),
    StevoraControl("light.rgb_cct", "living", "LED лента", "LED strip"),
    StevoraControl("light.living_room_switch_1", "living", "Лява светлина", "Left light"),
    StevoraControl("light.living_room_switch_2", "living", "Средна светлина", "Middle light"),
    StevoraControl("light.living_room_switch_3", "living", "Дясна светлина", "Right light"),
    StevoraControl("light.wifi_bel_2gang_switch_2", "kitchen", "Осветление", "Kitchen light"),
    StevoraControl("light.terrace_switch_1", "hall", "Осветление", "Hall light"),
    StevoraControl("light.bathroom_switch_2", "bathroom", "Осветление", "Bathroom light"),
    StevoraControl("fan.bathroom_switch_1", "bathroom", "Вентилатор", "Bathroom fan"),
    StevoraControl("light.wifi_bel_2gang_switch_1", "terrace", "Осветление", "Terrace light"),
)
private val controlById = panelControls.associateBy { it.id }

// Only the two HomePod display helpers used by STEVORA. These are read-only
// telemetry, not switches, and must never appear in the device control list.
private const val HOMEPOD_TEMP = "sensor.realna_temperatura_homepod"
private const val HOMEPOD_HUMIDITY = "sensor.realna_vlazhnost_homepod"
private val visibleEntityIds = controlById.keys + setOf(HOMEPOD_TEMP, HOMEPOD_HUMIDITY)

private fun localized(bgLanguage: Boolean, bgText: String, enText: String): String =
    if (bgLanguage) bgText else enText

private fun hvacLabel(mode: String, bgLanguage: Boolean): String = when (mode) {
    "off" -> localized(bgLanguage, "Изкл.", "Off")
    "heat" -> localized(bgLanguage, "Топло", "Heat")
    "cool" -> localized(bgLanguage, "Студено", "Cool")
    "dry" -> localized(bgLanguage, "Изсуш.", "Dry")
    "fan_only" -> localized(bgLanguage, "Вент.", "Fan")
    "heat_cool" -> localized(bgLanguage, "Авто", "Auto")
    else -> mode
}

@EntryPoint
@InstallIn(SingletonComponent::class)
internal interface StevoraServerEntryPoint {
    fun serverManager(): ServerManager
}

/**
 * Native, authenticated, room-based STEVORA controls.
 * Only explicit panel entities are rendered; the HA entity registry additionally
 * suppresses hidden, configuration and diagnostic entries.
 * The dashboard and Home Assistant entities are never modified by this screen.
 */
@Composable
internal fun StevoraLivePanel() {
    val context = LocalContext.current.applicationContext
    val bgLanguage = LocalConfiguration.current.locales[0].language == "bg"
    val manager = remember(context) {
        EntryPointAccessors.fromApplication(context, StevoraServerEntryPoint::class.java).serverManager()
    }
    var entities by remember { mutableStateOf<List<Entity>>(emptyList()) }
    var status by remember { mutableStateOf(localized(bgLanguage, "Свързване със STEVORA…", "Connecting to STEVORA…")) }
    var busy by remember { mutableStateOf<String?>(null) }
    var selectedRoom by remember { mutableStateOf("all") }
    val scope = rememberCoroutineScope()

    LaunchedEffect(manager) {
        while (isActive) {
            try {
                if (manager.getServer() == null) {
                    entities = emptyList()
                    status = localized(bgLanguage, "Добавете Home Hub в настройките", "Connect a Home Hub in Settings")
                } else {
                    val repo = manager.webSocketRepository()
                    val snapshot = repo.getStates()
                    if (snapshot == null) {
                        status = localized(bgLanguage, "Няма връзка — нов опит…", "Hub unavailable — retrying")
                    } else {
                        // Registry failure must not turn the display into an unfiltered HA list.
                        val hiddenIds = try {
                            repo.getEntityRegistry().orEmpty()
                                .filter { it.hiddenBy != null || it.entityCategory != null }
                                .map { it.entityId }
                                .toSet()
                        } catch (e: Exception) {
                            emptySet()
                        }
                        entities = snapshot.filter {
                            it.entityId in visibleEntityIds && it.entityId !in hiddenIds
                        }
                        status = localized(bgLanguage, "Свързано", "Connected") + " • " +
                            entities.count { it.entityId in controlById } + " " + localized(bgLanguage, "контроли", "controls")
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                status = localized(bgLanguage, "Проблем с връзката — нов опит…", "Connection unavailable — retrying")
            }
            delay(5_000L)
        }
    }

    fun execute(entity: Entity, service: String, data: Map<String, Any?> = emptyMap()) {
        if (busy != null || entity.entityId !in controlById) return
        busy = entity.entityId
        scope.launch {
            try {
                manager.integrationRepository().callAction(
                    entity.domain,
                    service,
                    data + ("entity_id" to entity.entityId),
                )
                // Follow-up refresh without importing every entity on the server.
                manager.webSocketRepository().getStates()?.let { snapshot ->
                    val previouslyVisible = entities.map { it.entityId }.toSet()
                    entities = snapshot.filter { it.entityId in previouslyVisible }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                status = localized(bgLanguage, "Командата не беше изпълнена", "Command failed — check the hub")
            } finally {
                busy = null
            }
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize().background(bg)) {
        val wide = maxWidth >= 700.dp
        val columns = if (wide) 2 else 1
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .padding(horizontal = if (wide) 28.dp else 16.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Text("STEVORA", fontSize = 28.sp, color = white, letterSpacing = 3.sp)
                    Text(localized(bgLanguage, "HOME • КОНТРОЛЕН ЦЕНТЪР", "HOME • COMMAND CENTER"),
                        fontSize = 11.sp, color = cyan, letterSpacing = 1.sp)
                }
                Text(if (status.startsWith("Connected") || status.startsWith("Свързано")) "● ONLINE" else "● OFFLINE",
                    color = if (status.startsWith("Connected") || status.startsWith("Свързано")) Color(0xFF4CE0B3) else secondary,
                    fontSize = 12.sp)
            }
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = tile),
                shape = RoundedCornerShape(18.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text(localized(bgLanguage, "Моят дом", "My Home"), color = white, fontSize = 22.sp)
                        Text(status, color = secondary, fontSize = 12.sp)
                    }
                    Text("⌂", color = cyan, fontSize = 38.sp)
                }
            }
            val temp = entities.firstOrNull { it.entityId == HOMEPOD_TEMP }
            val humidity = entities.firstOrNull { it.entityId == HOMEPOD_HUMIDITY }
            if (temp != null || humidity != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    listOf(
                        Triple(localized(bgLanguage, "Температура", "Temperature"), temp, "°C"),
                        Triple(localized(bgLanguage, "Влажност", "Humidity"), humidity, "%"),
                    ).forEach { (label, sensor, unit) ->
                        Card(
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(containerColor = tile),
                        ) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                                Text(label.uppercase(), color = secondary, fontSize = 11.sp, letterSpacing = 1.sp)
                                val state = sensor?.state?.takeUnless { it == "unknown" || it == "unavailable" }
                                Text(if (state == null) "—" else "$state $unit", color = white, fontSize = 24.sp)
                                Text("HomePod", color = cyan, fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
            Text(localized(bgLanguage, "ПОМЕЩЕНИЯ", "ROOMS"), color = secondary,
                fontSize = 12.sp, letterSpacing = 2.sp)
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                (listOf("all") + roomOrder).forEach { room ->
                    val title = if (room == "all") localized(bgLanguage, "Всички", "All rooms")
                        else roomNames[room]?.let { if (bgLanguage) it.first else it.second } ?: room
                    val active = selectedRoom == room
                    Card(
                        modifier = Modifier.clickable { selectedRoom = room },
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = if (active) cyan else tile),
                    ) {
                        Text(title, modifier = Modifier.padding(horizontal = 16.dp, vertical = 11.dp),
                            color = if (active) bg else white, fontSize = 13.sp)
                    }
                }
            }
            (if (selectedRoom == "all") roomOrder else listOf(selectedRoom)).forEach { room ->
                val items = panelControls
                    .filter { it.room == room }
                    .mapNotNull { control ->
                        entities.firstOrNull { it.entityId == control.id }?.let { control to it }
                    }
                if (items.isNotEmpty()) {
                    val names = requireNotNull(roomNames[room])
                    Text(if (bgLanguage) names.first else names.second, color = white, fontSize = 21.sp)
                    items.chunked(columns).forEach { group ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            group.forEach { (control, entity) ->
                                androidx.compose.foundation.layout.Box(modifier = Modifier.weight(1f)) {

                    Card(
                        colors = CardDefaults.cardColors(containerColor = tile),
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Text(if (bgLanguage) control.labelBg else control.labelEn,
                                color = white, fontSize = 17.sp)
                            val available = entity.state != "unavailable" && entity.state != "unknown"
                            if (!available) {
                                Text(localized(bgLanguage, "Недостъпно", "Unavailable"), color = secondary)
                            } else if (entity.domain == "climate") {
                                val current = entity.attributes["current_temperature"] as? Number
                                if (current != null) {
                                    Text(localized(bgLanguage, "В помещението: ", "Room: ") +
                                        current.toFloat().toString() + " °C", color = secondary)
                                }
                                val mode = entity.state
                                val modes = (entity.attributes["hvac_modes"] as? List<*>)
                                    ?.filterIsInstance<String>().orEmpty()
                                Text(localized(bgLanguage, "Режим: ", "Mode: ") +
                                    hvacLabel(mode, bgLanguage), color = secondary, fontSize = 13.sp)
                                Row(
                                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    modes.forEach { supportedMode ->
                                        val selected = supportedMode == mode
                                        Button(
                                            onClick = {
                                                if (!selected) execute(entity, "set_hvac_mode", mapOf("hvac_mode" to supportedMode))
                                            },
                                            enabled = busy != entity.entityId,
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = if (selected) cyan else Color(0xFF254158),
                                                contentColor = if (selected) bg else white,
                                            ),
                                        ) { Text(hvacLabel(supportedMode, bgLanguage)) }
                                    }
                                }
                                // The Tuya integration reports 88°C as max. Never show an 88°C
                                // slider: STEVORA uses a deliberate 16–30°C, whole-degree UI.
                                val target = entity.attributes["temperature"] as? Number
                                if (target != null) {
                                    val minTemp = maxOf(16, (entity.attributes["min_temp"] as? Number)?.toInt() ?: 16)
                                    val maxTemp = minOf(30, (entity.attributes["max_temp"] as? Number)?.toInt() ?: 30)
                                    val setpoint = target.toFloat().roundToInt().coerceIn(minTemp, maxTemp)
                                    val canSet = mode in setOf("heat", "cool", "heat_cool") &&
                                        busy != entity.entityId && maxTemp > minTemp
                                    Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                        OutlinedButton(
                                            onClick = {
                                                execute(entity, "set_temperature",
                                                    mapOf("temperature" to (setpoint - 1)))
                                            },
                                            enabled = canSet && setpoint > minTemp,
                                        ) { Text("−", fontSize = 23.sp) }
                                        Column {
                                            Text(localized(bgLanguage, "Зададена", "Target"),
                                                color = secondary, fontSize = 12.sp)
                                            Text(setpoint.toString() + " °C", color = white, fontSize = 25.sp)
                                        }
                                        OutlinedButton(
                                            onClick = {
                                                execute(entity, "set_temperature",
                                                    mapOf("temperature" to (setpoint + 1)))
                                            },
                                            enabled = canSet && setpoint < maxTemp,
                                        ) { Text("+", fontSize = 23.sp) }
                                    }
                                }
                            } else if (entity.domain == "light" || entity.domain == "fan") {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Text(
                                        localized(bgLanguage, if (entity.state == "on") "Включена" else "Изключена",
                                            if (entity.state == "on") "On" else "Off"),
                                        color = secondary,
                                    )
                                    Switch(
                                        checked = entity.state == "on",
                                        enabled = busy != entity.entityId,
                                        onCheckedChange = { on ->
                                            execute(entity, if (on) "turn_on" else "turn_off")
                                        },
                                    )
                                }
                                // Fan uses only supported on/off services; no invented speed control.
                                // The RGB strip is the only color/brightness control.
                                if (entity.entityId == "light.rgb_cct" && entity.state == "on") {
                                    val brightness = (entity.attributes["brightness"] as? Number)?.toInt()
                                    if (brightness != null) {
                                        val percent = (brightness * 100f / 255f).roundToInt().coerceIn(1, 100)
                                        var brightnessPercent by remember(entity.entityId, percent) {
                                            mutableStateOf(percent.toFloat())
                                        }
                                        Text(localized(bgLanguage, "Яркост", "Brightness") +
                                            ": " + brightnessPercent.roundToInt() + "%", color = secondary)
                                        Slider(
                                            value = brightnessPercent,
                                            valueRange = 1f..100f,
                                            onValueChange = { brightnessPercent = it },
                                            onValueChangeFinished = {
                                                execute(entity, "turn_on", mapOf(
                                                    "brightness" to (brightnessPercent * 255f / 100f).roundToInt()))
                                            },
                                            enabled = busy != entity.entityId,
                                        )
                                    }
                                    Row(
                                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        listOf(
                                            Triple("Бяло", "White", listOf(255, 255, 255)),
                                            Triple("Розово", "Pink", listOf(255, 80, 160)),
                                            Triple("Червено", "Red", listOf(255, 0, 0)),
                                        ).forEach { (labelBg, labelEn, rgb) ->
                                            OutlinedButton(
                                                onClick = { execute(entity, "turn_on", mapOf("rgb_color" to rgb)) },
                                                enabled = busy != entity.entityId,
                                            ) { Text(if (bgLanguage) labelBg else labelEn) }
                                        }
                                    }
                                }
                            }
                        }
                                }
                            }
                            }
                            repeat(columns - group.size) {
                                Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
            if (entities.isEmpty()) {
                Text(localized(bgLanguage,
                    "Няма активни STEVORA контроли. Проверете връзката и настройките на панела.",
                    "No STEVORA controls available. Check the connection and panel configuration."),
                    color = secondary)
            }
        }
    }
}
