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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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

// Server-controlled room/entity inventory; never carry Dunav entity IDs into another home.
// Only the selected OS manifest may populate this panel.
private data class StevoraControl(
    val id: String,
    val room: String,
    val labelBg: String,
    val labelEn: String,
    val actions: Set<String>,
)

private val visibleDomains = setOf(
    "climate", "light", "switch", "fan", "media_player", "cover",
    "lock", "sensor", "binary_sensor",
)
private fun shownInStevora(entityId: String, friendlyName: String = ""): Boolean =
    entityId.substringBefore('.') in visibleDomains &&
        !entityId.contains("backlight", ignoreCase = true) &&
        !friendlyName.contains("backlight", ignoreCase = true)

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
    fun languagesManager(): io.homeassistant.companion.android.settings.language.LanguagesManager
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
    var roomOrder by remember { mutableStateOf<List<String>>(emptyList()) }
    var roomNames by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var panelControls by remember { mutableStateOf<List<StevoraControl>>(emptyList()) }
    var activeControlIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var adaptiveActions by remember { mutableStateOf<Map<String, List<StevoraAdaptiveAction>>?>(null) }
    var activeServerId by remember { mutableStateOf<Int?>(null) }
    var temperatureSensorId by remember { mutableStateOf<String?>(null) }
    var humiditySensorId by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val prefs = remember(context) { context.getSharedPreferences("stevora.favorites", android.content.Context.MODE_PRIVATE) }
    var favoriteScope by remember { mutableStateOf<String?>(null) }
    var favorites by remember { mutableStateOf(emptySet<String>()) }
    var favoritesOnly by remember { mutableStateOf(false) }

    // Discover against the currently active HA server. Never use another home's
    // cached manifest or hardcoded Dunav entity IDs after switching servers.
    var manifestGeneration by remember { androidx.compose.runtime.mutableIntStateOf(0) }
    LaunchedEffect(manager, activeServerId) {
        val id = activeServerId ?: return@LaunchedEffect
        manager.webSocketRepository(id).getStevoraManifestChanges()?.collect {
            manifestGeneration++
        }
    }
    LaunchedEffect(manager, bgLanguage, manifestGeneration) {
        while (isActive) {
            liveConnected = false
            adaptiveActions = null
            try {
                val server = manager.getServer()
                if (server == null) {
                    favoriteScope = null
                    favorites = emptySet()
                    activeServerId = null
                    roomOrder = emptyList()
                    roomNames = emptyMap()
                    panelControls = emptyList()
                    activeControlIds = emptySet()
                    entities = emptyList()
                    temperatureSensorId = null
                    humiditySensorId = null
                    status = localized(bgLanguage, "Добавете Home Hub в настройките", "Connect a Home Hub in Settings")
                } else {
                    if (activeServerId != server.id) {
                        // Clear the prior home's visible UI before any network request.
                        entities = emptyList()
                        roomOrder = emptyList()
                        roomNames = emptyMap()
                        panelControls = emptyList()
                        activeControlIds = emptySet()
                        temperatureSensorId = null
                        humiditySensorId = null
                        favoriteScope = null
                        favorites = emptySet()
                        favoritesOnly = false
                        activeServerId = server.id
                    }
                    val repo = manager.webSocketRepository(server.id)
                    val changes = repo.getStateChanges()
                    if (changes == null) {
                        status = localized(bgLanguage, "Няма връзка — нов опит…", "Hub unavailable — retrying")
                    } else {
                        val manifestRaw = repo.getStevoraManifestJson(if (bgLanguage) "bg" else "en")
                        val manifestOk = manifestRaw != null &&
                            StevoraHomeProjection.apply(server.id, manifestRaw)
                        if (!manifestOk) StevoraHomeProjection.clear(server.id)
                        val manifest = if (manifestOk) StevoraHomeProjection.home.value[server.id] else null

                        if (manifest == null) {
                            entities = emptyList()
                            roomOrder = emptyList()
                            roomNames = emptyMap()
                            panelControls = emptyList()
                            activeControlIds = emptySet()
                            temperatureSensorId = null
                            humiditySensorId = null
                            status = localized(bgLanguage, "Липсва съвместим STEVORA OS манифест", "Compatible STEVORA OS manifest unavailable")
                            kotlinx.coroutines.delay(5_000)
                            continue
                        }

                        val language = if (bgLanguage) "bg" else "en"
                        adaptiveActions = parseStevoraAdaptiveActions(
                            repo.getStevoraCapabilityCatalogJson(language),
                            manifest.installationId,
                            language,
                        )

                        // The entity registry marks HA-hidden and config/diagnostic helpers.
                        // A missing registry must never make actions available.
                        val registry = repo.getEntityRegistry().orEmpty()
                        val excludedIds = registry.filter {
                            it.hiddenBy != null || it.entityCategory != null
                        }.map { it.entityId }.toSet()
                        val snapshot = repo.getStates()
                        if (snapshot == null) {
                            status = localized(bgLanguage, "Няма връзка — нов опит…", "Hub unavailable — retrying")
                        } else {
                            favoriteScope = repo.getCurrentUser()?.let { "${server.id}/${manifest.installationId}/${it.id}" }
                            favorites = favoriteScope?.let { prefs.getStringSet(it, emptySet())?.toSet() }.orEmpty()
                            val sourceRooms = manifest.rooms
                            val mappedEntities = manifest.entityMappings
                            val byId = snapshot.associateBy { it.entityId }
                            val display = sourceRooms.flatMap { room ->
                                mappedEntities[room.id].orEmpty().distinct().mapNotNull { id ->
                                    val entity = byId[id] ?: return@mapNotNull null
                                    val name = entity.attributes["friendly_name"] as? String
                                        ?: id.substringAfter('.').replace('_', ' ')
                                    if (id in excludedIds || !shownInStevora(id, name)) null
                                    else StevoraControl(
                                        id = id,
                                        room = room.id,
                                        labelBg = manifest.localizedEntityNames[id] ?: name,
                                        labelEn = manifest.localizedEntityNames[id] ?: name,
                                        actions = manifest.controlsByEntity[id].orEmpty(),
                                    )
                                }
                            }.distinctBy { it.id }
                            val allowed = display.map { it.id }.toSet()
                            activeServerId = server.id
                            roomOrder = sourceRooms.map { it.id }
                            roomNames = sourceRooms.associate { it.id to it.name }
                            panelControls = display
                            activeControlIds = display.filter { it.actions.isNotEmpty() }.map { it.id }.toSet()
                            if (selectedRoom != "all" && selectedRoom !in roomOrder) selectedRoom = "all"
                            entities = snapshot.filter { it.entityId in allowed }
                            // Informational telemetry is picked by HA device class, not a
                            // particular home's HomePod entity ID.
                            val sensors = entities.filter { it.domain == "sensor" }
                            temperatureSensorId = sensors.firstOrNull {
                                it.attributes["device_class"] == "temperature"
                            }?.entityId
                            humiditySensorId = sensors.firstOrNull {
                                it.attributes["device_class"] == "humidity"
                            }?.entityId
                            liveConnected = true
                            status = localized(bgLanguage,
                                "STEVORA manifest • свързано",
                                "STEVORA manifest • connected",
                            ) + " • " + display.size + " entities"

                            // Refresh active server + manifest at least every 20 s, even if
                            // state-change events keep the previous server's stream alive.
                            kotlinx.coroutines.withTimeoutOrNull(20_000L) {
                                changes.collect { event ->
                                    if (event.entityId in allowed) {
                                        entities = entities.filterNot { it.entityId == event.entityId } +
                                            listOfNotNull(event.newState)
                                    }
                                }
                            }
                            status = localized(bgLanguage, "Опресняване на STEVORA…", "Refreshing STEVORA…")
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                liveConnected = false
                status = localized(bgLanguage, "Проблем с връзката — нов опит…", "Connection unavailable — retrying")
            }
            liveConnected = false
            delay(2_000L)
        }
    }

    fun executeAdaptive(entity: Entity, action: StevoraAdaptiveAction, parameters: Map<String, Any>, confirmed: Boolean) {
        val target = activeServerId ?: return
        if (!liveConnected || busy != null || entity.entityId !in activeControlIds ||
            entity.state in setOf("unknown", "unavailable") ||
            action !in adaptiveActions?.get(entity.entityId).orEmpty()
        ) return
        busy = entity.entityId
        scope.launch {
            try {
                if (manager.getServer()?.id != target) return@launch
                val accepted = manager.webSocketRepository(target).executeStevoraCapabilityAction(
                    action.capabilityId, action.id, parameters, confirmed,
                )
                if (!accepted) status = localized(bgLanguage, "Командата беше отказана", "Command rejected")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                status = localized(bgLanguage, "Командата не беше изпълнена", "Command failed — check the hub")
            } finally {
                busy = null
            }
        }
    }

    fun execute(entity: Entity, service: String, data: Map<String, Any?> = emptyMap()) {
        if (!liveConnected || busy != null || entity.entityId !in activeControlIds) return
        val targetServerId = activeServerId ?: return
        busy = entity.entityId
        scope.launch {
            try {
                // Never dispatch a command to a previous home after server switching.
                if (manager.getServer()?.id != targetServerId) {
                    status = localized(bgLanguage, "Домът е сменен — обновете екрана", "Home changed — refresh the view")
                    return@launch
                }
                val required = when (service) {
                    "turn_on", "turn_off" -> when {
                        "color_temp_kelvin" in data -> "color_temp"
                        "effect" in data -> "effect"
                        "brightness" in data -> "brightness"
                        "rgb_color" in data -> "color"
                        else -> "toggle"
                    }
                    "media_play_pause" -> "play_pause"
                    "volume_set" -> "volume"
                    "set_cover_position" -> "position"
                    "set_cover_tilt_position" -> "tilt"
                    "set_percentage" -> "percentage"
                    "set_direction" -> "direction"
                    "oscillate" -> "oscillate"
                    "set_preset_mode" -> "preset_mode"
                    "set_fan_mode" -> "fan_mode"
                    "set_swing_mode" -> "swing_mode"
                    "select_source" -> "source"
                    "set_temperature" -> "temperature"
                    "set_hvac_mode" -> "hvac_mode"
                    "open_cover", "stop_cover", "close_cover" -> service.removeSuffix("_cover")
                    else -> return@launch
                }
                val allowed = StevoraHomeProjection.home.value[targetServerId]?.controlsByEntity?.get(entity.entityId).orEmpty()
                if (required !in allowed || entity.state in setOf("unknown", "unavailable")) return@launch
                manager.integrationRepository(targetServerId).callAction(
                    entity.domain,
                    service,
                    data + ("entity_id" to entity.entityId),
                )
                // State_changed WebSocket events update the affected tile.
                // Do not issue a whole-home snapshot after each command.
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
                Text(if (liveConnected) "● ONLINE" else "● OFFLINE",
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
            val temp = entities.firstOrNull { it.entityId == temperatureSensorId }
            val humidity = entities.firstOrNull { it.entityId == humiditySensorId }
            if (temp != null || humidity != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    listOf(
                        Triple(localized(bgLanguage, "Температура", "Temperature"), temp, temp?.attributes?.get("unit_of_measurement") as? String ?: ""),
                        Triple(localized(bgLanguage, "Влажност", "Humidity"), humidity, "%"),
                    ).forEach { (label, sensor, unit) ->
                        val state = sensor?.state?.takeUnless {
                            it == "unknown" || it == "unavailable"
                        }
                        StevoraMetricCard(
                            label = label,
                            value = if (state == null) "—" else "$state $unit",
                            detail = localized(bgLanguage, "HA датчик", "HA sensor"),
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
                if (favoriteScope != null) {
                    OutlinedButton(onClick = { favoritesOnly = !favoritesOnly }) {
                        Text((if (favoritesOnly) "★ " else "☆ ") + localized(bgLanguage, "Избрани", "Favorites"))
                    }
                }
                (listOf("all") + roomOrder).forEach { room ->
                    val title = if (room == "all") localized(bgLanguage, "Всички", "All rooms")
                        else roomNames[room] ?: room
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
                    .filter { it.room == room && (!favoritesOnly || it.id in favorites) }
                    .mapNotNull { control ->
                        entities.firstOrNull { it.entityId == control.id }?.let { control to it }
                    }
                if (items.isNotEmpty()) {
                    Text(roomNames[room] ?: room, color = white, fontSize = 21.sp)
                    items.chunked(columns).forEach { group ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            group.forEach { (control, entity) ->
                                androidx.compose.foundation.layout.Box(modifier = Modifier.weight(1f)) {

                    var expanded by androidx.compose.runtime.saveable.rememberSaveable(activeServerId, entity.entityId) { mutableStateOf(false) }
                    val activeColor by animateColorAsState(
                        targetValue = if (entity.state == "on" || (entity.domain == "climate" && entity.state != "off")) StevoraDesign.surfaceRaised else tile,
                        animationSpec = tween(320),
                        label = "stevora-device-state",
                    )
                    Card(
                        colors = CardDefaults.cardColors(containerColor = activeColor),
                        shape = RoundedCornerShape(20.dp),
                        border = BorderStroke(1.dp, panelBorder),
                        modifier = Modifier.fillMaxWidth().animateContentSize().clickable { expanded = !expanded },
                    ) {
                        Column(
                            Modifier.padding(horizontal = 14.dp, vertical = if (expanded) 14.dp else 6.dp),
                            verticalArrangement = Arrangement.spacedBy(if (expanded) 10.dp else 2.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                androidx.compose.material3.TextButton(onClick = { expanded = !expanded }) {
                                    Text((if (bgLanguage) control.labelBg else control.labelEn) + if (expanded) " ▴" else " ▾",
                                        color = white, fontSize = 17.sp)
                                }
                                favoriteScope?.let { key ->
                                    androidx.compose.material3.TextButton(onClick = {
                                        favorites = if (entity.entityId in favorites) favorites - entity.entityId else favorites + entity.entityId
                                        prefs.edit().putStringSet(key, favorites).apply()
                                    }) {
                                        Text(if (entity.entityId in favorites) "★" else "☆",
                                            modifier = Modifier.semantics { contentDescription = localized(bgLanguage, "Избрани", "Favorite") })
                                    }
                                }
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
                            if (expanded && available && liveConnected) {
                                StevoraDeviceOptions(entity, control.actions, bgLanguage) { service, fields -> execute(entity, service, fields) }
                            }
                            val catalog = adaptiveActions
                            if (expanded && available && liveConnected && catalog != null &&
                                entity.domain !in setOf("light", "climate")
                            ) {
                                catalog[entity.entityId].orEmpty().forEach { action ->
                                    androidx.compose.runtime.key(activeServerId, entity.entityId, action.id) {
                                        StevoraAdaptiveActionControl(action, bgLanguage, busy == null) { parameters, confirmed ->
                                            executeAdaptive(entity, action, parameters, confirmed)
                                        }
                                    }
                                }
                            } else if (!expanded || control.actions.isEmpty() || !available || !liveConnected) {
                                Text(
                                    (when (entity.state) {
                                        "off" -> localized(bgLanguage, "Изключено", "Off")
                                        "on" -> localized(bgLanguage, "Включено", "On")
                                        "unavailable", "unknown" -> localized(bgLanguage, "Няма данни", "Unavailable")
                                        else -> entity.state.replace('_', ' ')
                                    }) + " " + (entity.attributes["unit_of_measurement"] as? String ?: ""),
                                    color = secondary,
                                )
                            } else if (entity.domain == "media_player") {
                                if ("play_pause" in control.actions) {
                                    OutlinedButton(onClick = { execute(entity, "media_play_pause") }) {
                                        Text(localized(bgLanguage, "Пусни / пауза", "Play / pause"))
                                    }
                                }
                                val volume = (entity.attributes["volume_level"] as? Number)?.toFloat()
                                if ("volume" in control.actions && volume != null) {
                                    var level by remember(entity.entityId, volume) { mutableStateOf(volume.coerceIn(0f, 1f)) }
                                    Text(localized(bgLanguage, "Сила на звука", "Volume"), color = secondary)
                                    Slider(value = level, onValueChange = { level = it }, onValueChangeFinished = {
                                        execute(entity, "volume_set", mapOf("volume_level" to level))
                                    })
                                }
                            } else if (entity.domain == "cover") {
                                listOf("open", "stop", "close").filter { it in control.actions }.forEach { action ->
                                    OutlinedButton(onClick = { execute(entity, action + "_cover") }) {
                                        Text(when (action) {
                                            "open" -> localized(bgLanguage, "Отвори", "Open")
                                            "close" -> localized(bgLanguage, "Затвори", "Close")
                                            else -> localized(bgLanguage, "Спри", "Stop")
                                        })
                                    }
                                }
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
                                modes.filter { "hvac_mode" in control.actions }.chunked(2).forEach { modeRow ->
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
                                val target = entity.attributes["temperature"] as? Number
                                val minTemp = (entity.attributes["min_temp"] as? Number)?.toDouble()?.let { kotlin.math.ceil(it).toInt() }
                                val maxTemp = (entity.attributes["max_temp"] as? Number)?.toDouble()?.let { kotlin.math.floor(it).toInt() }
                                if ("temperature" in control.actions && target != null && minTemp != null && maxTemp != null && maxTemp >= minTemp) {
                                    val setpoint = target.toFloat().roundToInt().coerceIn(minTemp, maxTemp)
                                    val canSet = busy != entity.entityId
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
                            } else if (entity.domain == "light" || entity.domain == "fan" || entity.domain == "switch") {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Text(
                                        localized(bgLanguage, if (entity.state == "on") "Включена" else "Изключена",
                                            if (entity.state == "on") "On" else "Off"),
                                        color = secondary,
                                    )
                                    if ("toggle" in control.actions) Switch(
                                        checked = entity.state == "on",
                                        enabled = busy != entity.entityId,
                                        onCheckedChange = { on ->
                                            execute(entity, if (on) "turn_on" else "turn_off")
                                        },
                                    )
                                }
                                // Fan uses only supported on/off services; no invented speed control.
                                // The RGB strip is the only color/brightness control.
                                val colorModes = (entity.attributes["supported_color_modes"] as? List<*>)
                                    ?.filterIsInstance<String>().orEmpty()
                                val hasRgb = colorModes.any {
                                    it in setOf("rgb", "rgbw", "rgbww", "hs", "xy")
                                }
                                if (entity.domain == "light" && ("brightness" in control.actions || (hasRgb && "color" in control.actions))) {
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
                                        if ("brightness" in control.actions) {
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
                                        }
                                        if (hasRgb && "color" in control.actions) {
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
                            }
                            repeat(columns - group.size) {
                                Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
            if (favoritesOnly && favorites.none { id -> panelControls.any { it.id == id } }) {
                Text(localized(bgLanguage, "Няма избрани устройства", "No favorite devices"), color = secondary)
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
