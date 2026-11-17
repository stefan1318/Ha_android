package io.homeassistant.companion.android.stevora

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.size
import androidx.compose.ui.graphics.Brush
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
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private val bg = StevoraDesign.background
private val tile = StevoraDesign.surface
private val panelBorder = StevoraDesign.border
private val cyan = StevoraDesign.accent
private val white = StevoraDesign.foreground
private val secondary = StevoraDesign.muted

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
    StevoraControl("media_player.living_room_tv_2", "living", "Телевизор", "Living Room TV"),
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
internal fun StevoraLivePanel(initialRoom: String = "all") {
    val context = LocalContext.current.applicationContext
    val bgLanguage = LocalConfiguration.current.locales[0].language == "bg"
    val manager = remember(context) {
        EntryPointAccessors.fromApplication(context, StevoraServerEntryPoint::class.java).serverManager()
    }
    var entities by remember { mutableStateOf<List<Entity>>(emptyList()) }
    var status by remember { mutableStateOf(localized(bgLanguage, "Свързване със STEVORA…", "Connecting to STEVORA…")) }
    var busy by remember { mutableStateOf<String?>(null) }
    var liveConnected by remember { mutableStateOf(false) }
    var selectedRoom by remember(initialRoom) { mutableStateOf(initialRoom) }
    val scope = rememberCoroutineScope()

    // STEVORA 2.1: one initial snapshot per connection, then push updates.
    // Reconnect after stream completion/errors; never poll the entire home every 5s.
    LaunchedEffect(manager, bgLanguage) {
        while (isActive) {
            liveConnected = false
            try {
                if (manager.getServer() == null) {
                    entities = emptyList()
                    status = localized(bgLanguage, "Добавете Home Hub в настройките", "Connect a Home Hub in Settings")
                } else {
                    val repo = manager.webSocketRepository()
                    // Subscribe before the snapshot to avoid losing changes during initialization.
                    val changes = repo.getStateChanges()
                    if (changes == null) {
                        status = localized(bgLanguage, "Няма връзка — нов опит…", "Hub unavailable — retrying")
                    } else {
                        // Fail closed if visibility cannot be verified; never expose hidden controls.
                        val hiddenIds = try {
                            repo.getEntityRegistry().orEmpty()
                                .filter { it.hiddenBy != null || it.entityCategory != null }
                                .map { it.entityId }
                                .toSet()
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            visibleEntityIds
                        }
                        val allowedIds = visibleEntityIds - hiddenIds
                        val snapshot = repo.getStates()
                        if (snapshot == null) {
                            status = localized(bgLanguage, "Няма връзка — нов опит…", "Hub unavailable — retrying")
                        } else {
                            entities = snapshot.filter { it.entityId in allowedIds }
                            liveConnected = true
                            status = localized(bgLanguage, "Свързано • на живо", "Connected • Live") + " • " +
                                entities.count { it.entityId in controlById } + " " +
                                localized(bgLanguage, "контроли", "controls")
                            changes.collect { event ->
                                if (event.entityId in allowedIds) {
                                    entities = entities.filterNot { it.entityId == event.entityId } +
                                        listOfNotNull(event.newState)
                                }
                            }
                            // A completed subscription is not a healthy live connection.
                            status = localized(bgLanguage, "Възстановяване на връзката…", "Reconnecting…")
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                status = localized(bgLanguage, "Проблем с връзката — нов опит…", "Connection unavailable — retrying")
            }
            delay(5_000L) // Retry only after disconnect; not a state refresh interval.
        }
    }

    fun execute(entity: Entity, service: String, data: Map<String, Any?> = emptyMap()) {
        if (!liveConnected || busy != null || entity.entityId !in controlById) return
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

    BoxWithConstraints(modifier = Modifier.fillMaxSize().background(StevoraDesign.gradient)) {
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
                        val state = sensor?.state?.takeUnless {
                            it == "unknown" || it == "unavailable"
                        }
                        StevoraMetricCard(
                            label = label,
                            value = if (state == null) "—" else "$state $unit",
                            detail = "HomePod",
                            modifier = Modifier.weight(1f),
                        )
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

                    val activeColor by animateColorAsState(
                        targetValue = if (entity.state == "on" || (entity.domain == "climate" && entity.state != "off")) StevoraDesign.surfaceRaised else tile,
                        animationSpec = tween(320),
                        label = "stevora-device-state",
                    )
                    Card(
                        colors = CardDefaults.cardColors(containerColor = activeColor),
                        shape = RoundedCornerShape(20.dp),
                        border = BorderStroke(1.dp, panelBorder),
                        modifier = Modifier.fillMaxWidth().animateContentSize(),
                    ) {
                        Column(
                            Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(if (bgLanguage) control.labelBg else control.labelEn,
                                    color = white, fontSize = 17.sp)
                                if (busy == entity.entityId) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        color = cyan,
                                        strokeWidth = 2.dp,
                                    )
                                } else {
                                    Box(
                                        Modifier.size(9.dp).clip(RoundedCornerShape(50))
                                            .background(if (entity.state == "on" || (entity.domain == "climate" && entity.state != "off")) StevoraDesign.success else secondary),
                                    )
                                }
                            }
                            val available = entity.state != "unavailable" && entity.state != "unknown"
                            if (entity.domain == "media_player") {
                                // HA can report unavailable while the television still accepts
                                // power commands. Keep controls visible and let the server decide.
                                Text(
                                    localized(bgLanguage, "Развлечения • Дневна", "Entertainment • Living Room"),
                                    color = cyan, fontSize = 12.sp, letterSpacing = 1.sp,
                                )
                                Text(
                                    if (available) entity.state else localized(bgLanguage,
                                        "Няма връзка • командите остават достъпни",
                                        "Offline status • controls remain available"),
                                    color = secondary, fontSize = 12.sp,
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Button(
                                        modifier = Modifier.weight(1f),
                                        enabled = liveConnected && busy != entity.entityId,
                                        onClick = { execute(entity, "turn_on") },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF254158)),
                                    ) { Text(localized(bgLanguage, "⏻ Включи", "⏻ Power on"), fontSize = 12.sp) }
                                    OutlinedButton(
                                        modifier = Modifier.weight(1f),
                                        enabled = busy != entity.entityId,
                                        onClick = { execute(entity, "turn_off") },
                                    ) { Text(localized(bgLanguage, "Изключи", "Power off"), fontSize = 12.sp) }
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    listOf(
                                        "−" to "volume_down",
                                        "⏯" to "media_play_pause",
                                        "+" to "volume_up",
                                    ).forEach { (label, service) ->
                                        OutlinedButton(
                                            modifier = Modifier.weight(1f),
                                            enabled = busy != entity.entityId,
                                            onClick = { execute(entity, service) },
                                        ) { Text(label, fontSize = 18.sp) }
                                    }
                                }
                            } else if (!available) {
                                Text(localized(bgLanguage, "Недостъпно", "Unavailable"), color = secondary)
                            } else if (entity.domain == "climate") {
                                val current = entity.attributes["current_temperature"] as? Number
                                val currentReading = current?.toFloat()
                                val animatedRoomTemp by animateFloatAsState(
                                    targetValue = currentReading ?: 0f,
                                    animationSpec = tween(600),
                                    label = "stevora-room-temperature",
                                )
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = StevoraDesign.background),
                                    shape = RoundedCornerShape(18.dp),
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Column(
                                        Modifier.fillMaxWidth().padding(16.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(5.dp),
                                    ) {
                                        Text(localized(bgLanguage, "ТЕМПЕРАТУРА В СТАЯТА", "ROOM TEMPERATURE"),
                                            color = secondary, fontSize = 11.sp)
                                        Text(if (currentReading == null) "—" else "%.1f °C".format(animatedRoomTemp),
                                            color = white, fontSize = 35.sp)
                                    }
                                }
                                val mode = entity.state
                                val modes = (entity.attributes["hvac_modes"] as? List<*>)
                                    ?.filterIsInstance<String>().orEmpty()
                                Text(localized(bgLanguage, "Режим: ", "Mode: ") +
                                    hvacLabel(mode, bgLanguage), color = secondary, fontSize = 13.sp)
                                // Wrap modes into a responsive two-column grid instead of
                                // clipping buttons inside a narrow tablet card.
                                modes.chunked(2).forEach { modeRow ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        modeRow.forEach { supportedMode ->
                                            val selected = supportedMode == mode
                                            Button(
                                                modifier = Modifier.weight(1f),
                                                onClick = {
                                                    if (!selected) execute(entity, "set_hvac_mode", mapOf("hvac_mode" to supportedMode))
                                                },
                                                enabled = busy != entity.entityId,
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = if (selected) cyan else Color(0xFF254158),
                                                    contentColor = if (selected) bg else white,
                                                ),
                                            ) {
                                                Text(hvacLabel(supportedMode, bgLanguage), fontSize = 12.sp, maxLines = 1)
                                            }
                                        }
                                        if (modeRow.size == 1) Spacer(Modifier.weight(1f))
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
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceEvenly,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
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
                                            val animatedSetpoint by animateFloatAsState(
                                                targetValue = setpoint.toFloat(),
                                                animationSpec = tween(350),
                                                label = "stevora-target-temperature",
                                            )
                                            Text(animatedSetpoint.roundToInt().toString() + " °C",
                                                color = cyan, fontSize = 29.sp)
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
                                if (entity.entityId == "light.rgb_cct") {
                                    val brightness = (entity.attributes["brightness"] as? Number)?.toInt()
                                    val percent = ((brightness ?: 255) * 100f / 255f).roundToInt().coerceIn(1, 100)
                                    var brightnessPercent by remember(entity.entityId, percent) {
                                        mutableStateOf(percent.toFloat())
                                    }
                                    val animatedBrightness by animateFloatAsState(
                                        targetValue = brightnessPercent,
                                        animationSpec = tween(260),
                                        label = "stevora-led-brightness",
                                    )
                                    val rgb = (entity.attributes["rgb_color"] as? List<*>)
                                        ?.mapNotNull { (it as? Number)?.toInt() }
                                    val stripColor = if (rgb != null && rgb.size >= 3) {
                                        Color(rgb[0].coerceIn(0, 255), rgb[1].coerceIn(0, 255), rgb[2].coerceIn(0, 255))
                                    } else Color.White
                                    val animatedStripColor by animateColorAsState(
                                        targetValue = if (entity.state == "on") stripColor else StevoraDesign.border,
                                        animationSpec = tween(450),
                                        label = "stevora-led-color",
                                    )
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    ) {
                                        Box(
                                            Modifier.size(38.dp).clip(RoundedCornerShape(12.dp))
                                                .background(animatedStripColor),
                                        )
                                        Column {
                                            Text(localized(bgLanguage, "LED ЦВЯТ", "LED COLOR"),
                                                color = secondary, fontSize = 11.sp)
                                            Text(if (entity.state == "on")
                                                localized(bgLanguage, "Активна", "Active")
                                                else localized(bgLanguage, "Изключена", "Off"),
                                                color = white)
                                        }
                                    }
                                    if (entity.state == "on") {
                                        Text(localized(bgLanguage, "Яркост", "Brightness") +
                                            ": " + animatedBrightness.roundToInt() + "%", color = white)
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
                                        Text(localized(bgLanguage, "ЦВЕТОВИ СЦЕНИ", "COLOR PRESETS"),
                                            color = secondary, fontSize = 11.sp)
                                        Row(
                                            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        ) {
                                            listOf(
                                                Triple("Бяло", "White", listOf(255, 255, 255)),
                                                Triple("Топло", "Warm", listOf(255, 190, 110)),
                                                Triple("Розово", "Pink", listOf(255, 80, 160)),
                                                Triple("Червено", "Red", listOf(255, 0, 0)),
                                                Triple("Синьо", "Blue", listOf(30, 110, 255)),
                                                Triple("Лилаво", "Purple", listOf(160, 60, 255)),
                                            ).forEach { (labelBg, labelEn, preset) ->
                                                Column(
                                                    horizontalAlignment = Alignment.CenterHorizontally,
                                                    verticalArrangement = Arrangement.spacedBy(5.dp),
                                                ) {
                                                    val color = Color(preset[0], preset[1], preset[2])
                                                    Card(
                                                        modifier = Modifier.size(44.dp).clickable(
                                                            enabled = busy != entity.entityId,
                                                        ) {
                                                            execute(entity, "turn_on", mapOf("rgb_color" to preset))
                                                        },
                                                        shape = RoundedCornerShape(14.dp),
                                                        border = BorderStroke(2.dp, if (rgb == preset) cyan else panelBorder),
                                                        colors = CardDefaults.cardColors(containerColor = color),
                                                    ) {}
                                                    Text(if (bgLanguage) labelBg else labelEn,
                                                        color = secondary, fontSize = 11.sp)
                                                }
                                            }
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
