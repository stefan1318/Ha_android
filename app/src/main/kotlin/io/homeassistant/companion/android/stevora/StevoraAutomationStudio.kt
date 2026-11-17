package io.homeassistant.companion.android.stevora

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.util.UUID

@Composable
internal fun StevoraAutomationStudio(onBack: () -> Unit) {
    val context = LocalContext.current.applicationContext
    val bg = LocalConfiguration.current.locales[0].language == "bg"
    fun tr(a: String, b: String) = if (bg) a else b
    val manager = remember(context) {
        EntryPointAccessors.fromApplication(context, StevoraServerEntryPoint::class.java).serverManager()
    }
    val scope = rememberCoroutineScope()
    var loading by remember { mutableStateOf(true) }
    var status by remember { mutableStateOf("") }
    var automations by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    var busy by remember { mutableStateOf<String?>(null) }
    var pendingAction by remember { mutableStateOf<Pair<String, String>?>(null) }
    var refresh by remember { mutableStateOf(0) }
    var filter by remember { mutableStateOf("") }
    var onlyEnabled by remember { mutableStateOf(false) }
    var inspecting by remember { mutableStateOf<String?>(null) }
    var config by remember { mutableStateOf<JsonObject?>(null) }
    var configLoading by remember { mutableStateOf(false) }
    var editId by remember { mutableStateOf<String?>(null) }
    var editText by remember { mutableStateOf("") }
    var baseline by remember { mutableStateOf<JsonObject?>(null) }
    var saveConfirm by remember { mutableStateOf(false) }
    var editError by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }

    LaunchedEffect(manager, refresh) {
        loading = true
        try {
            val socket = if (manager.getServer() != null) manager.webSocketRepository() else null
            val user = socket?.getCurrentUser()
            if (user == null || (!user.isAdmin && !user.isOwner)) {
                automations = emptyList()
                status = tr("Необходими са администраторски права", "Administrator permission required")
            } else {
                val states = socket.getStates()
                if (states == null) {
                    automations = emptyList()
                    status = tr("Home Hub е недостъпен", "Home Hub unavailable")
                } else {
                    automations = states.filter { it.entityId.startsWith("automation.") }
                        .sortedBy { it.entityId }.map { it.entityId to it.state }
                    status = tr("Автоматизации от Home Assistant", "Home Assistant automations")
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            automations = emptyList()
            status = tr("Неуспешно зареждане", "Unable to load automations")
        } finally {
            loading = false
        }
    }
    editId?.let { id ->
        AlertDialog(
            onDismissRequest = { if (!saving) editId = null },
            title = { Text(tr("Редактор на автоматизация", "Automation editor")) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text(id)
                    OutlinedTextField(
                        value = editText,
                        onValueChange = { editText = it.take(60000) },
                        modifier = Modifier.fillMaxWidth().height(320.dp),
                        label = { Text("JSON") },
                    )
                    if (editError.isNotEmpty()) Text(editError, color = Color(0xFFFF9D9D))
                }
            },
            confirmButton = {
                Button(enabled = !saving, onClick = {
                    try {
                        val parsed = Json.parseToJsonElement(editText).jsonObject
                        val alias = parsed["alias"]?.jsonPrimitive?.contentOrNull
                        val triggers = parsed["triggers"] ?: parsed["trigger"]
                        val actions = parsed["actions"] ?: parsed["action"]
                        require(!alias.isNullOrBlank()) { "Alias is required" }
                        require(triggers is kotlinx.serialization.json.JsonArray && triggers.isNotEmpty()) {
                            "At least one trigger is required"
                        }
                        require(actions is kotlinx.serialization.json.JsonArray && actions.isNotEmpty()) {
                            "At least one action is required"
                        }
                        editError = ""
                        saveConfirm = true
                    } catch (e: Exception) {
                        editError = e.message ?: tr("Невалиден JSON", "Invalid JSON")
                    }
                }) { Text(tr("Провери и запази", "Review & save")) }
            },
            dismissButton = {
                OutlinedButton(enabled = !saving, onClick = { editId = null }) {
                    Text(tr("Отказ", "Cancel"))
                }
            },
        )
    }
    if (saveConfirm && editId != null) {
        AlertDialog(
            onDismissRequest = { if (!saving) saveConfirm = false },
            title = { Text(tr("Потвърди записването", "Confirm save")) },
            text = { Text(tr(
                "Промяната ще бъде записана в Home Assistant и може да промени поведението на устройствата.",
                "This will save to Home Assistant and may change device behavior.",
            )) },
            confirmButton = {
                Button(enabled = !saving, onClick = {
                    val id = editId ?: return@Button
                    saveConfirm = false
                    saving = true
                    scope.launch {
                        try {
                            val socket = manager.webSocketRepository()
                            val user = socket.getCurrentUser()
                            require(user != null && (user.isAdmin || user.isOwner)) {
                                "Administrator permission required"
                            }
                            if (baseline != null) {
                                val current = socket.getStevoraAutomationConfig(
                                    "automation." + id
                                )?.get("config") as? JsonObject
                                require(current == baseline) {
                                    "Automation changed on server. Reopen before saving."
                                }
                            }
                            val parsed = Json.parseToJsonElement(editText).jsonObject
                            val clean = JsonObject(parsed.filterKeys { it != "id" })
                            val success = manager.integrationRepository().saveStevoraAutomation(id, clean)
                            require(success) { "Home Assistant rejected the configuration" }
                            status = tr("Автоматизацията е записана", "Automation saved")
                            editId = null
                            inspecting = null
                            config = null
                            refresh++
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            editError = e.message ?: tr("Грешка при запис", "Save failed")
                        } finally {
                            saving = false
                        }
                    }
                }) { Text(tr("Запази", "Save")) }
            },
            dismissButton = {
                OutlinedButton(enabled = !saving, onClick = { saveConfirm = false }) {
                    Text(tr("Отказ", "Cancel"))
                }
            },
        )
    }
    inspecting?.let { id ->
        AlertDialog(
            onDismissRequest = { if (!configLoading) { inspecting = null; config = null } },
            title = { Text(tr("Конфигурация", "Configuration")) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(id)
                    Text(if (configLoading) tr("Зареждане…", "Loading…") else config?.toString()?.take(4000)
                        ?: tr("Конфигурацията не е достъпна през HA API.",
                            "Configuration unavailable through HA API."))
                }
            },
            confirmButton = {
                Button(enabled = !configLoading, onClick = { inspecting = null; config = null }) {
                    Text(tr("Затвори", "Close"))
                }
            },
        )
    }
    pendingAction?.let { (id, previousState) ->
        AlertDialog(
            onDismissRequest = { if (busy == null) pendingAction = null },
            title = { Text(tr("Потвърди промяната", "Confirm change")) },
            text = { Text(tr("Промяна на автоматизация: ", "Change automation: ") + id) },
            confirmButton = {
                OutlinedButton(enabled = busy == null, onClick = {
                    pendingAction = null
                    busy = id
                    scope.launch {
                        try {
                            val socket = manager.webSocketRepository()
                            val user = socket.getCurrentUser()
                            val currentState = socket.getStates()?.firstOrNull { it.entityId == id }?.state
                            if (user == null || (!user.isAdmin && !user.isOwner)) {
                                status = tr("Отказан достъп", "Permission denied")
                            } else if (currentState != previousState) {
                                status = tr("Състоянието е променено. Обновете списъка.", "State changed. Refresh the list.")
                                refresh++
                            } else {
                                manager.integrationRepository().callAction(
                                    "automation", if (previousState == "on") "turn_off" else "turn_on",
                                    mapOf("entity_id" to id),
                                )
                                refresh++
                            }
                        } catch (e: CancellationException) {
                            throw e
                        } catch (_: Exception) {
                            status = tr("Командата е неуспешна", "Command failed")
                        } finally {
                            busy = null
                        }
                    }
                }) { Text(tr("Потвърди", "Confirm")) }
            },
            dismissButton = {
                OutlinedButton(onClick = { pendingAction = null }) { Text(tr("Отказ", "Cancel")) }
            },
        )
    }
    Column(
        Modifier.fillMaxSize().background(Color(0xFF08131F)).verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("STEVORA • AUTOMATION STUDIO", color = Color(0xFF27B9F5))
        Text(status, color = Color.White)
        Text(
            tr("Управление на състоянието на съществуващи автоматизации. " +
                "Създаването и редактирането на правила ще бъдат добавени след защитена API интеграция.",
                "Manage existing automation enabled states. Creating and editing rules requires a future protected API integration."),
            color = Color(0xFFA6BCCD),
        )
        if (loading) Text(tr("Зареждане…", "Loading…"), color = Color.White)
        if (!loading && automations.isEmpty()) {
            Text(tr("Няма достъпни автоматизации.", "No available automations."),
                color = Color(0xFFA6BCCD))
        }
        OutlinedTextField(
            value = filter,
            onValueChange = { filter = it.take(100) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text(tr("Търси автоматизация", "Search automations")) },
        )
        FilterChip(
            selected = onlyEnabled,
            onClick = { onlyEnabled = !onlyEnabled },
            label = { Text(tr("Само активни", "Enabled only")) },
        )
        val visibleAutomations = automations.filter { (id, state) ->
            (!onlyEnabled || state == "on") && id.contains(filter.trim(), ignoreCase = true)
        }
        Text(
            tr("Показани: ", "Showing: ") + visibleAutomations.size + " / " + automations.size,
            color = Color(0xFFA6BCCD),
        )
        visibleAutomations.forEach { (id, state) ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF122437)),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(id.removePrefix("automation.").replace('_', ' '), color = Color.White)
                    Text(
                        when (state) {
                            "on" -> tr("Активна", "Enabled")
                            "off" -> tr("Изключена", "Disabled")
                            "unavailable" -> tr("Недостъпна", "Unavailable")
                            else -> state
                        },
                        color = Color(0xFFA6BCCD),
                    )
                    OutlinedButton(
            enabled = busy == null && !configLoading,
            onClick = {
                busy = id
                config = null
                configLoading = true
                inspecting = id
                scope.launch {
                    try {
                        config = manager.webSocketRepository().getStevoraAutomationConfig(id)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        config = null
                        inspecting = id
                    } finally {
                        busy = null
                        configLoading = false
                    }
                }
            },
                    ) { Text(tr("Преглед на правилото", "Inspect rule")) }
                    if (state == "on" || state == "off") {
                        OutlinedButton(
                            enabled = busy == null,
                            onClick = { pendingAction = id to state },
                        ) { Text(if (state == "on") tr("Изключи", "Disable") else tr("Включи", "Enable")) }
                    }
                }
            }
        }
        OutlinedButton(onClick = { refresh++ }) { Text(tr("Обнови", "Refresh")) }
        OutlinedButton(onClick = onBack) { Text(tr("Назад", "Back")) }
        Spacer(Modifier.height(88.dp))
    }
}
