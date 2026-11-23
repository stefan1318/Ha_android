package io.homeassistant.companion.android.stevora

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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import androidx.compose.ui.res.stringResource
import androidx.compose.runtime.saveable.rememberSaveable
import io.homeassistant.companion.android.common.R

private fun JSONArray?.records(): List<JSONObject> =
    (0 until (this?.length() ?: 0)).mapNotNull { index -> this?.optJSONObject(index) }

/** Admin authorization is enforced by the STEVORA integration, never by Compose. */
@Composable
internal fun StevoraManagersHome(page: StevoraSettingsPage, serverId: Int, onBack: () -> Unit) {
    val context = LocalContext.current.applicationContext
    val bg = LocalConfiguration.current.locales[0].language == "bg"
    fun tr(a: String, b: String) = if (bg) a else b
    val manager = remember(context) {
        EntryPointAccessors.fromApplication(context, StevoraServerEntryPoint::class.java).serverManager()
    }
    val scope = rememberCoroutineScope()
    var rooms by remember { mutableStateOf<JSONObject?>(null) }
    var assistants by remember { mutableStateOf<JSONObject?>(null) }
    var status by remember { mutableStateOf("") }
    var refresh by remember { mutableStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    var roomNames by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var editingEntity by remember { mutableStateOf("") }
    var deviceName by remember { mutableStateOf("") }
    var deviceIcon by remember { mutableStateOf("") }
    var deviceLayout by remember { mutableStateOf("compact") }
    var deviceArea by remember { mutableStateOf("") }
    var automaticControls by remember { mutableStateOf(true) }
    var deviceControls by remember { mutableStateOf("") }
    var deviceUsers by remember { mutableStateOf("") }
    var localizedRoomNames by remember { mutableStateOf<Map<String, Map<String, String>>>(emptyMap()) }
    var localizedDeviceNames by remember { mutableStateOf<Map<String, Map<String, String>>>(emptyMap()) }

    LaunchedEffect(manager, serverId, page, refresh) {
        busy = true
        try {
            val socket = manager.webSocketRepository(serverId)
            rooms = if (page == StevoraSettingsPage.Assistants) null
            else socket.stevoraManagerCommand("stevora/admin/rooms/get")?.let(::JSONObject)
            val aliases = rooms?.optJSONObject("room_aliases")
            roomNames = buildMap {
                aliases?.keys()?.forEach { key -> put(key, aliases.optString(key)) }
            }
            val translations = rooms?.optJSONObject("translations")
            fun readLabels(kind: String): Map<String, Map<String, String>> {
                val items = translations?.optJSONObject(kind)
                return buildMap {
                    items?.keys()?.forEach { key ->
                        val names = items.optJSONObject(key)
                        put(key, mapOf("bg" to (names?.optString("bg") ?: ""),
                                       "en" to (names?.optString("en") ?: "")))
                    }
                }
            }
            localizedRoomNames = readLabels("rooms")
            localizedDeviceNames = readLabels("entities")
            assistants = if (page == StevoraSettingsPage.Assistants)
                socket.stevoraManagerCommand("stevora/admin/assistants/get")?.let(::JSONObject) else null
            if (rooms == null && assistants == null) status =
                tr("Нужна е STEVORA интеграция и администраторски достъп.",
                   "STEVORA integration and administrator access are required.")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            rooms = null
            assistants = null
            status = e.message ?: tr("Няма връзка", "Connection unavailable")
        } finally {
            busy = false
        }
    }
    fun changeVisibility(entityId: String, mode: String) {
        val revision = rooms?.optInt("revision") ?: return
        scope.launch {
            busy = true
            try {
                val fields = mapOf<String, Any?>(
                    "expected_revision" to revision,
                    "entities" to listOf(mapOf("entity_id" to entityId, "visibility" to mode))
                )
                val result = manager.webSocketRepository(serverId)
                    .stevoraManagerCommand("stevora/admin/rooms/update", fields)
                status = if (result != null) tr("Запазено", "Saved") else
                    tr("Отказано или конфликт на версията", "Denied or revision conflict")
                refresh++
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                status = e.message ?: "Error"
            } finally {
                busy = false
            }
        }
    }
    fun setDefaultPipeline(id: String?) {
        val snapshot = assistants ?: return
        val revision = snapshot.optInt("revision")
        val overrides = snapshot.optJSONObject("settings")?.optJSONObject("user_pipelines")
        val userPipelines = mutableMapOf<String, String>()
        overrides?.keys()?.forEach { key -> userPipelines[key] = overrides.optString(key) }
        scope.launch {
            busy = true
            try {
                val fields = mapOf<String, Any?>(
                    "expected_revision" to revision,
                    "default_pipeline" to id,
                    "user_pipelines" to userPipelines
                )
                val result = manager.webSocketRepository(serverId)
                    .stevoraManagerCommand("stevora/admin/assistants/update", fields)
                status = if (result != null) tr("Асистентът е запазен", "Assistant saved") else
                    tr("Отказано или конфликт на версията", "Denied or revision conflict")
                refresh++
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                status = e.message ?: "Error"
            } finally {
                busy = false
            }
        }
    }

    fun saveRooms(areaId: String, order: List<String>?) {
        val snapshot = rooms ?: return
        val aliases = snapshot.optJSONObject("entity_aliases")
        val entityAliases = mutableMapOf<String, String>()
        aliases?.keys()?.forEach { key -> entityAliases[key] = aliases.optString(key) }
        val existingNames = snapshot.optJSONObject("room_aliases")
        val names = mutableMapOf<String, String>()
        existingNames?.keys()?.forEach { key -> names[key] = existingNames.optString(key) }
        roomNames[areaId]?.let { names[areaId] = it }
        val fields = mutableMapOf<String, Any?>("expected_revision" to snapshot.optInt("revision"),
            "room_aliases" to names, "entity_aliases" to entityAliases)
        if (order != null) fields["room_order"] = order
        scope.launch {
            busy = true
            try {
                val result = manager.webSocketRepository(serverId).stevoraManagerCommand(
                    "stevora/admin/rooms/aliases/update",
                    fields
                )
                status = if (result != null) tr("Имената са запазени", "Room names saved") else
                    tr("Отказано или конфликт на версията", "Denied or revision conflict")
                refresh++
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                status = e.message ?: "Error"
            } finally {
                busy = false
            }
        }
    }

    fun editDevice(entityId: String) {
        val profile = rooms?.optJSONObject("device_profiles")?.optJSONObject(entityId)
        editingEntity = entityId
        deviceName = profile?.optString("name") ?: ""
        deviceIcon = profile?.optString("icon") ?: ""
        deviceLayout = profile?.optString("layout") ?: "compact"
        deviceArea = profile?.optString("area_id")?.takeUnless { it == "null" } ?: ""
        fun values(key: String): String {
            val items = profile?.optJSONArray(key)
            return (0 until (items?.length() ?: 0)).joinToString(",") { items!!.optString(it) }
        }
        automaticControls = profile?.optJSONArray("controls") == null
        deviceControls = values("controls")
        deviceUsers = values("user_ids")
    }
    fun saveDevice() {
        val snapshot = rooms ?: return
        val entityId = editingEntity.takeIf { it.isNotEmpty() } ?: return
        val stored = snapshot.optJSONObject("device_profiles")?.optJSONObject(entityId)
        val profile = mutableMapOf<String, Any?>(
            "name" to deviceName, "icon" to deviceIcon, "layout" to deviceLayout,
            "area_id" to deviceArea.ifBlank { null },
            "display_category" to (stored?.optString("display_category", "auto") ?: "auto"),
            "user_ids" to deviceUsers.split(",").map(String::trim).filter(String::isNotBlank)
        )
        if (!automaticControls) profile["controls"] = deviceControls.split(",").map(String::trim).filter(String::isNotBlank)
        scope.launch {
            busy = true
            try {
                val result = manager.webSocketRepository(serverId).stevoraManagerCommand(
                    "stevora/admin/devices/update",
                    mapOf("expected_revision" to snapshot.optInt("revision"),
                          "profiles" to mapOf(entityId to profile))
                )
                status = if (result != null) tr("Устройството е запазено", "Device saved") else
                    tr("Отказано или конфликт на версията", "Denied or revision conflict")
                refresh++
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                status = e.message ?: "Error"
            } finally {
                busy = false
            }
        }
    }

    fun saveTranslation(kind: String, id: String) {
        val snapshot = rooms ?: return
        val labels = if (kind == "rooms") localizedRoomNames[id] else localizedDeviceNames[id]
        scope.launch {
            busy = true
            try {
                val result = manager.webSocketRepository(serverId).stevoraManagerCommand(
                    "stevora/admin/translations/update",
                    mapOf("expected_revision" to snapshot.optInt("revision"),
                          "rooms" to (if (kind == "rooms") mapOf(id to (labels ?: emptyMap())) else emptyMap<String, Any>()),
                          "entities" to (if (kind == "entities") mapOf(id to (labels ?: emptyMap())) else emptyMap<String, Any>()))
                )
                status = if (result != null) tr("Преводите са запазени", "Translations saved") else
                    tr("Отказано или конфликт", "Denied or revision conflict")
                refresh++
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                status = e.message ?: "Error"
            } finally {
                busy = false
            }
        }
    }

    fun setUserPipeline(userId: String, pipelineId: String?) {
        val snapshot = assistants ?: return
        val revision = snapshot.optInt("revision")
        val settings = snapshot.optJSONObject("settings")
        val defaultId = settings?.optString("default_pipeline")?.takeIf { it.isNotBlank() && it != "null" }
        val existing = settings?.optJSONObject("user_pipelines")
        val overrides = mutableMapOf<String, String>()
        existing?.keys()?.forEach { key -> overrides[key] = existing.optString(key) }
        if (pipelineId == null) overrides.remove(userId) else overrides[userId] = pipelineId
        scope.launch {
            busy = true
            try {
                val result = manager.webSocketRepository(serverId).stevoraManagerCommand(
                    "stevora/admin/assistants/update",
                    mapOf("expected_revision" to revision, "default_pipeline" to defaultId,
                          "user_pipelines" to overrides)
                )
                status = if (result != null) tr("Запазено", "Saved") else
                    tr("Отказано или конфликт на версията", "Denied or revision conflict")
                refresh++
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                status = e.message ?: "Error"
            } finally {
                busy = false
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().background(StevoraDesign.background)
            .verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        OutlinedButton(onClick = onBack) { Text(tr("‹ Назад", "‹ Back")) }
        Text(stringResource(when (page) {
            StevoraSettingsPage.Rooms -> R.string.stevora_rooms
            StevoraSettingsPage.Devices -> R.string.stevora_devices
            else -> R.string.stevora_assistants
        }), color = StevoraDesign.foreground)
        if (busy) androidx.compose.material3.LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        if (status.isNotEmpty()) Text(status, color = StevoraDesign.accent)
        assistants?.let { snapshot ->
            val inherit = StevoraChoice("", tr("Автоматично", "Automatic"))
            val pipelines = remember(snapshot) {
                snapshot.optJSONArray("pipelines").records().map { StevoraChoice(it.getString("id"), it.optString("name")) }
            }
            val settings = snapshot.optJSONObject("settings")
            val default = settings?.optString("default_pipeline").orEmpty().takeUnless { it == "null" }.orEmpty()
            if (!busy) StevoraSettingsPicker(tr("Основен асистент", "Default assistant"), listOf(inherit) + pipelines, default) {
                setDefaultPipeline(it.ifEmpty { null })
            }
            var user by rememberSaveable(serverId) { mutableStateOf<String?>(null) }
            val users = remember(snapshot) {
                snapshot.optJSONArray("users").records().map { StevoraChoice(it.getString("id"), it.optString("name")) }
            }
            StevoraSettingsPicker(stringResource(R.string.stevora_choose_user), users, user) { user = it }
            val selected = users.firstOrNull { it.id == user }
            if (selected != null && !busy) {
                val pipeline = settings?.optJSONObject("user_pipelines")?.optString(selected.id).orEmpty()
                StevoraSettingsPicker(tr("Личен асистент", "Personal assistant"),
                    listOf(StevoraChoice("", tr("Наследи основния", "Use default"))) + pipelines, pipeline) {
                    setUserPipeline(selected.id, it.ifEmpty { null })
                }
            }
        }
        val areas = remember(rooms) { rooms?.optJSONArray("rooms").records() }
        val roomOptions = remember(rooms, bg) {
            areas.map { area ->
                val id = area.getString("id")
                StevoraChoice(id, localizedRoomNames[id]?.get(if (bg) "bg" else "en").orEmpty().ifBlank {
                    roomNames[id].orEmpty().ifBlank { area.optString("name", id) }
                }, area.optString("name"))
            }
        }
        if (page == StevoraSettingsPage.Rooms && rooms != null) {
            var selectedRoom by rememberSaveable(serverId) { mutableStateOf<String?>(null) }
            StevoraSettingsPicker(stringResource(R.string.stevora_choose_room), roomOptions, selectedRoom) { selectedRoom = it }
            areas.firstOrNull { it.optString("id") == selectedRoom }?.let { area ->
                val areaId = area.getString("id")
                OutlinedTextField(
                    value = roomNames[areaId] ?: area.optString("name"),
                    onValueChange = { name -> roomNames = roomNames + (areaId to name) },
                    label = { Text(tr("Име на стая", "Room name")) },
                    modifier = Modifier.fillMaxWidth()
                )
                val order = remember(rooms) {
                    val stored = rooms?.optJSONArray("room_order")
                    val ids = areas.map { it.optString("id") }
                    ((0 until (stored?.length() ?: 0)).map { stored!!.getString(it) }.filter { it in ids } + ids).distinct()
                }
                val position = order.indexOf(areaId)
                var rank by remember(areaId, rooms) { mutableStateOf(position) }
                val positions = remember(order) { order.indices.map { StevoraChoice(it.toString(), (it + 1).toString()) } }
                StevoraSettingsPicker(tr("Позиция", "Position"), positions, rank.toString()) { rank = it.toInt() }
                OutlinedButton(onClick = {
                    val next = if (rank != position && rank in order.indices) order.toMutableList().apply {
                        remove(areaId)
                        add(rank, areaId)
                    } else null
                    saveRooms(areaId, next)
                }, enabled = !busy) {
                    Text(tr("Запази името", "Save name"))
                }
                val id = areaId
                for (locale in listOf("bg", "en")) {
                    OutlinedTextField(
                        value = localizedRoomNames[id]?.get(locale) ?: "",
                        onValueChange = { value ->
                            localizedRoomNames = localizedRoomNames + (id to
                                ((localizedRoomNames[id] ?: emptyMap()) + (locale to value)))
                        },
                        label = { Text(locale.uppercase()) }
                    )
                }
                OutlinedButton(onClick = { saveTranslation("rooms", id) }, enabled = !busy) {
                    Text(tr("Запази преводите", "Save translations"))
                }
            }
        }
        if (page == StevoraSettingsPage.Devices && rooms != null) {
            val inventory = remember(rooms) { rooms?.optJSONArray("entities").records() }
            val options = remember(rooms, bg) {
                val names = roomOptions.associate { it.id to it.name }
                inventory.map { entity ->
                    val id = entity.getString("entity_id")
                    val original = entity.optString("name", id)
                    val label = localizedDeviceNames[id]?.get(if (bg) "bg" else "en").orEmpty().ifBlank {
                        rooms?.optJSONObject("device_profiles")?.optJSONObject(id)?.optString("name").orEmpty().ifBlank { original }
                    }
                    StevoraChoice(id, label, listOfNotNull(names[entity.optString("area_id")], original.takeIf { it != label }, id).joinToString(" · "))
                }
            }
            var selectedDevice by rememberSaveable(serverId) { mutableStateOf<String?>(null) }
            StevoraSettingsPicker(stringResource(R.string.stevora_choose_device), options, selectedDevice) { selectedDevice = it }
            val entity = inventory.firstOrNull { it.optString("entity_id") == selectedDevice }
            if (entity != null) {
                val id = entity.getString("entity_id")
                LaunchedEffect(id, rooms) { editDevice(id) }
                if (editingEntity == id) {
                    for (locale in listOf("bg", "en")) {
                        OutlinedTextField(
                            value = localizedDeviceNames[id]?.get(locale) ?: "",
                            onValueChange = { value ->
                                localizedDeviceNames = localizedDeviceNames + (id to
                                    ((localizedDeviceNames[id] ?: emptyMap()) + (locale to value)))
                            },
                            label = { Text(tr("Име", "Name") + " · " + locale.uppercase()) }
                        )
                    }
                    OutlinedButton(onClick = { saveTranslation("entities", id) }, enabled = !busy) {
                        Text(tr("Запази преводите", "Save translations"))
                    }
                    OutlinedTextField(deviceName, { deviceName = it }, label = { Text(tr("Име", "Name")) })
                    OutlinedTextField(deviceIcon, { deviceIcon = it }, label = { Text("mdi:icon") })
                    Text(tr("Оформление", "Layout"), color = StevoraDesign.foreground)
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        for (layout in listOf("info", "compact", "expanded")) {
                            OutlinedButton(onClick = { deviceLayout = layout }) {
                                Text((if (deviceLayout == layout) "✓" else "") + layout)
                            }
                        }
                    }
                    StevoraSettingsPicker(stringResource(R.string.stevora_choose_room),
                        listOf(StevoraChoice("", tr("Автоматично", "Automatic"))) + roomOptions, deviceArea) { deviceArea = it }
                    OutlinedButton(onClick = { automaticControls = !automaticControls }) {
                        Text(if (automaticControls) tr("Контроли: автоматични", "Controls: automatic") else tr("Контроли: избрани", "Controls: selected"))
                    }
                    if (!automaticControls) OutlinedTextField(deviceControls, { deviceControls = it },
                        label = { Text(tr("Контроли (със запетая)", "Controls (comma-separated)")) })
                    OutlinedTextField(deviceUsers, { deviceUsers = it },
                        label = { Text(tr("Потребителски ID", "User IDs")) })
                    Button(onClick = { saveDevice() }, enabled = !busy) {
                        Text(tr("Запази устройството", "Save device"))
                    }
                }
                if (entity.optBoolean("disabled") || entity.optBoolean("hidden")) {
                    Text(tr("Недостъпно в HA", "Unavailable in HA"), color = StevoraDesign.muted)
                } else if (!busy) {
                    val visibility = rooms?.optJSONObject("visibility")?.optString(id).orEmpty().ifBlank { "default" }
                    StevoraSettingsPicker(tr("Видимост", "Visibility"), listOf(
                        StevoraChoice("default", tr("Автоматично", "Automatic")),
                        StevoraChoice("include", stringResource(R.string.stevora_visible)),
                        StevoraChoice("exclude", stringResource(R.string.stevora_hidden)),
                    ), visibility) { changeVisibility(id, it) }
                }
            }
        }
        Button(onClick = { refresh++ }, enabled = !busy) {
            Text(tr("Обнови", "Refresh"))
        }
    }
}
