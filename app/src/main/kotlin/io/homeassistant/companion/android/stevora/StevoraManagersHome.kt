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
import org.json.JSONObject

/** Admin authorization is enforced by the STEVORA integration, never by Compose. */
@Composable
internal fun StevoraManagersHome(onBack: () -> Unit) {
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
    var deviceControls by remember { mutableStateOf("") }
    var deviceUsers by remember { mutableStateOf("") }
    var localizedRoomNames by remember { mutableStateOf<Map<String, Map<String, String>>>(emptyMap()) }
    var localizedDeviceNames by remember { mutableStateOf<Map<String, Map<String, String>>>(emptyMap()) }

    LaunchedEffect(manager, refresh) {
        busy = true
        try {
            val socket = manager.webSocketRepository()
            rooms = socket.stevoraManagerCommand("stevora/admin/rooms/get")?.let(::JSONObject)
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
            assistants = socket.stevoraManagerCommand("stevora/admin/assistants/get")?.let(::JSONObject)
            status = if (rooms != null && assistants != null) "" else
                tr("Нужна е STEVORA интеграция и администраторски достъп.",
                   "STEVORA integration and administrator access are required.")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
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
                val result = manager.webSocketRepository()
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
                val result = manager.webSocketRepository()
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

    fun saveRooms() {
        val snapshot = rooms ?: return
        val aliases = snapshot.optJSONObject("entity_aliases")
        val entityAliases = mutableMapOf<String, String>()
        aliases?.keys()?.forEach { key -> entityAliases[key] = aliases.optString(key) }
        val roomOrder = snapshot.optJSONArray("room_order")
        val order = (0 until (roomOrder?.length() ?: 0)).map { roomOrder!!.optString(it) }
        scope.launch {
            busy = true
            try {
                val result = manager.webSocketRepository().stevoraManagerCommand(
                    "stevora/admin/rooms/aliases/update",
                    mapOf("expected_revision" to snapshot.optInt("revision"),
                          "room_aliases" to roomNames, "entity_aliases" to entityAliases,
                          "room_order" to order)
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
        deviceControls = values("controls")
        deviceUsers = values("user_ids")
    }
    fun saveDevice() {
        val snapshot = rooms ?: return
        val entityId = editingEntity.takeIf { it.isNotEmpty() } ?: return
        val profile = mapOf<String, Any?>(
            "name" to deviceName, "icon" to deviceIcon, "layout" to deviceLayout,
            "area_id" to deviceArea.ifBlank { null },
            "controls" to deviceControls.split(",").map(String::trim).filter(String::isNotBlank),
            "user_ids" to deviceUsers.split(",").map(String::trim).filter(String::isNotBlank)
        )
        scope.launch {
            busy = true
            try {
                val result = manager.webSocketRepository().stevoraManagerCommand(
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
                val result = manager.webSocketRepository().stevoraManagerCommand(
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
        val defaultId = settings?.optString("default_pipeline")?.takeIf { it.isNotBlank() }
        val existing = settings?.optJSONObject("user_pipelines")
        val overrides = mutableMapOf<String, String>()
        existing?.keys()?.forEach { key -> overrides[key] = existing.optString(key) }
        if (pipelineId == null) overrides.remove(userId) else overrides[userId] = pipelineId
        scope.launch {
            busy = true
            try {
                val result = manager.webSocketRepository().stevoraManagerCommand(
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
        Text("STEVORA • " + tr("МЕНИДЖЪРИ", "MANAGERS"), color = StevoraDesign.foreground)
        OutlinedButton(onClick = onBack) { Text(tr("Назад", "Back")) }
        if (status.isNotEmpty()) Text(status, color = StevoraDesign.accent)
        Text("Assistant Manager", color = StevoraDesign.foreground)
        val snapshot = assistants
        if (snapshot != null) {
            val selected = snapshot.optJSONObject("settings")?.optString("default_pipeline")
            Text(tr("Основен асистент: ", "Default assistant: ") + (selected ?: "—"),
                color = StevoraDesign.muted)
            OutlinedButton(onClick = { setDefaultPipeline(null) }, enabled = !busy) {
                Text(tr("HA по подразбиране", "HA default"))
            }
            val pipelines = snapshot.optJSONArray("pipelines")
            for (i in 0 until (pipelines?.length() ?: 0)) {
                val pipeline = pipelines?.optJSONObject(i) ?: continue
                val id = pipeline.optString("id")
                OutlinedButton(onClick = { setDefaultPipeline(id) }, enabled = !busy) {
                    Text((if (id == selected) "✓ " else "") + pipeline.optString("name", id))
                }
            }
        }
        Text(tr("Асистент по потребител", "Assistant per user"), color = StevoraDesign.foreground)
        val users = assistants?.optJSONArray("users")
        val pipelineOptions = assistants?.optJSONArray("pipelines")
        for (i in 0 until (users?.length() ?: 0)) {
            val user = users?.optJSONObject(i) ?: continue
            val userId = user.optString("id")
            Text(user.optString("name", userId), color = StevoraDesign.foreground)
            OutlinedButton(onClick = { setUserPipeline(userId, null) }, enabled = !busy) {
                Text(tr("Наследи основния", "Use default"))
            }
            for (j in 0 until (pipelineOptions?.length() ?: 0)) {
                val option = pipelineOptions?.optJSONObject(j) ?: continue
                OutlinedButton(onClick = { setUserPipeline(userId, option.optString("id")) }, enabled = !busy) {
                    Text(option.optString("name"))
                }
            }
        }
        Text("Device & Room Manager", color = StevoraDesign.foreground)
        val areaList = rooms?.optJSONArray("rooms")
        for (i in 0 until (areaList?.length() ?: 0)) {
            val area = areaList?.optJSONObject(i) ?: continue
            val areaId = area.optString("id")
            OutlinedTextField(
                value = roomNames[areaId] ?: area.optString("name"),
                onValueChange = { name -> roomNames = roomNames + (areaId to name) },
                label = { Text(tr("Име на стая", "Room name")) },
                modifier = Modifier.fillMaxWidth()
            )
        }
        for (i in 0 until (areaList?.length() ?: 0)) {
            val area = areaList?.optJSONObject(i) ?: continue
            val id = area.optString("id")
            Text(area.optString("name") + " · BG/EN", color = StevoraDesign.foreground)
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
                Text("Save BG/EN")
            }
        }
        OutlinedButton(onClick = { saveRooms() }, enabled = !busy) {
            Text(tr("Запази имената", "Save room names"))
        }
        val inventory = rooms?.optJSONArray("entities")
        for (i in 0 until (inventory?.length() ?: 0)) {
            val entity = inventory?.optJSONObject(i) ?: continue
            val id = entity.optString("entity_id")
            Text(entity.optString("name", id) + " • " + id, color = StevoraDesign.foreground)
            OutlinedButton(onClick = { editDevice(id) }, enabled = !busy) {
                Text(tr("Персонализирай", "Customize"))
            }
            if (editingEntity == id) {
                for (locale in listOf("bg", "en")) {
                    OutlinedTextField(
                        value = localizedDeviceNames[id]?.get(locale) ?: "",
                        onValueChange = { value ->
                            localizedDeviceNames = localizedDeviceNames + (id to
                                ((localizedDeviceNames[id] ?: emptyMap()) + (locale to value)))
                        },
                        label = { Text("Name " + locale.uppercase()) }
                    )
                }
                OutlinedButton(onClick = { saveTranslation("entities", id) }, enabled = !busy) {
                    Text("Save BG/EN")
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
                Text(tr("Стая", "Room"), color = StevoraDesign.foreground)
                OutlinedButton(onClick = { deviceArea = "" }) {
                    Text(tr("Автоматично", "Automatic"))
                }
                val choices = rooms?.optJSONArray("rooms")
                for (k in 0 until (choices?.length() ?: 0)) {
                    val area = choices?.optJSONObject(k) ?: continue
                    OutlinedButton(onClick = { deviceArea = area.optString("id") }) {
                        Text((if (deviceArea == area.optString("id")) "✓ " else "") + area.optString("name"))
                    }
                }
                OutlinedTextField(deviceControls, { deviceControls = it },
                    label = { Text(tr("Контроли (със запетая)", "Controls (comma-separated)")) })
                OutlinedTextField(deviceUsers, { deviceUsers = it },
                    label = { Text(tr("Потребителски ID", "User IDs")) })
                Button(onClick = { saveDevice() }, enabled = !busy) {
                    Text(tr("Запази устройството", "Save device"))
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (entity.optBoolean("disabled") || entity.optBoolean("hidden")) {
                    Text(tr("Недостъпно в HA", "Unavailable in HA"), color = StevoraDesign.muted)
                } else {
                    OutlinedButton(onClick = { changeVisibility(id, "default") }, enabled = !busy) {
                        Text(tr("Авто", "Auto"))
                    }
                    OutlinedButton(onClick = { changeVisibility(id, "include") }, enabled = !busy) {
                        Text("+")
                    }
                    OutlinedButton(onClick = { changeVisibility(id, "exclude") }, enabled = !busy) {
                        Text("−")
                    }
                }
            }
        }
        Button(onClick = { refresh++ }, enabled = !busy) {
            Text(tr("Обнови", "Refresh"))
        }
    }
}
