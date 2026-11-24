package io.homeassistant.companion.android.stevora

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
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
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
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
    var selectedAutomation by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf<String?>(null) }
    var onlyEnabled by remember { mutableStateOf(false) }
    var inspecting by remember { mutableStateOf<String?>(null) }
    var config by remember { mutableStateOf<JsonObject?>(null) }
    var configLoading by remember { mutableStateOf(false) }
    var editId by remember { mutableStateOf<String?>(null) }
    var editText by remember { mutableStateOf("") }
    var advancedEditor by remember { mutableStateOf(false) }
    var devices by remember { mutableStateOf(emptyList<StevoraChoice>()) }
    var targets by remember { mutableStateOf(emptyList<StevoraChoice>()) }
    var ruleAlias by remember { mutableStateOf("") }
    var triggerEntity by remember { mutableStateOf("") }
    var triggerTo by remember { mutableStateOf("on") }
    var actionEntity by remember { mutableStateOf("") }
    var actionService by remember { mutableStateOf("turn_on") }
    var baseline by remember { mutableStateOf<JsonObject?>(null) }
    var sourceEntityId by remember { mutableStateOf<String?>(null) }
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
                val server = manager.getServer()
                val raw = socket.getStevoraManifestJson(if (bg) "bg" else "en")
                val allowed = if (server != null && raw != null && StevoraHomeProjection.apply(server.id, raw)) {
                    StevoraHomeProjection.home.value[server.id]?.controllableEntityIds.orEmpty()
                } else emptySet()
                val home = server?.let { StevoraHomeProjection.home.value[it.id] }
                fun choices(ids: Collection<String>) = ids.map { id ->
                    val label = home?.localizedEntityNames?.get(id)?.takeUnless { it == id } ?: id.substringAfter('.').replace('_', ' ')
                    val room = home?.rooms?.find { id in home.entityMappings[it.id].orEmpty() }?.name.orEmpty()
                    StevoraChoice(id, label, room)
                }
                devices = choices(home?.entityMappings?.values?.flatten().orEmpty().distinct().filter { it.substringBefore('.') in listOf("light","switch","fan","binary_sensor","input_boolean") })
                targets = choices(allowed.filter { "toggle" in home?.controlsByEntity?.get(it).orEmpty() })
                val states = socket.getStates()?.filter { it.entityId in allowed }
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
                    if (advancedEditor) {
                        Text(tr("Това правило има допълнителни стъпки и може да бъде преглеждано.", "This rule has additional steps and is available to view."))
                    } else {
                        Text(tr("Когато → Тогава", "When → Then"), color = StevoraDesign.accent)
                        OutlinedTextField(ruleAlias, { ruleAlias = it }, label = {
                            Text(tr("Име на автоматизацията", "Automation name"))
                        })
                        StevoraSettingsPicker(tr("Когато устройството", "When this device"), devices, triggerEntity) { triggerEntity = it }
                        StevoraSettingsPicker(tr("Премине в състояние", "Changes to"), listOf(
                            StevoraChoice("on", tr("Включено", "On"), ""), StevoraChoice("off", tr("Изключено", "Off"), "")
                        ), triggerTo) { triggerTo = it }
                        StevoraSettingsPicker(tr("Управлявай устройство", "Control device"), targets, actionEntity) { actionEntity = it }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = actionService == "turn_on",
                                onClick = { actionService = "turn_on" },
                                label = { Text(tr("Включи", "Turn on")) },
                            )
                            FilterChip(
                                selected = actionService == "turn_off",
                                onClick = { actionService = "turn_off" },
                                label = { Text(tr("Изключи", "Turn off")) },
                            )
                        }
                    }
                    if (editError.isNotEmpty()) Text(editError, color = Color(0xFFFF9D9D))
                }
            },
            confirmButton = {
                Button(enabled = !saving && !advancedEditor && ruleAlias.isNotBlank() && devices.any { it.id == triggerEntity } && targets.any { it.id == actionEntity }, onClick = {
                    try {
                        if (!advancedEditor) {
                            require(ruleAlias.isNotBlank()) { "Automation name required" }
                            require(triggerEntity.contains(".")) { "Invalid trigger entity ID" }
                            require(actionEntity.contains(".")) { "Invalid target entity ID" }
                            val original = Json.parseToJsonElement(editText).jsonObject
                            require(((original["conditions"] ?: original["condition"]) as? JsonArray)?.isNotEmpty() != true) {
                                "Complex conditions require JSON mode"
                            }
                            val trigger = buildJsonObject {
                                put("platform", "state")
                                put("entity_id", triggerEntity.trim())
                                put("to", triggerTo)
                            }
                            val action = buildJsonObject {
                                put("service", actionEntity.substringBefore(".") + "." + actionService)
                                put("target", buildJsonObject { put("entity_id", actionEntity.trim()) })
                            }
                            val updated = original.toMutableMap()
                            updated["alias"] = JsonPrimitive(ruleAlias.trim())
                            updated["triggers"] = JsonArray(listOf(trigger))
                            updated["actions"] = JsonArray(listOf(action))
                            updated.remove("trigger")
                            updated.remove("action")
                            editText = JsonObject(updated).toString()
                        }
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
                        editError = tr("Провери избраните устройства и състояние.", "Check the selected devices and state.")
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
                                    sourceEntityId ?: error("Missing original entity")
                                )?.get("config") as? JsonObject
                                require(current == baseline) {
                                    "Automation changed on server. Reopen before saving."
                                }
                            }
                            val parsed = Json.parseToJsonElement(editText).jsonObject
                            val validation = socket.validateStevoraAutomation(parsed)
                                ?: error("Home Assistant validation unavailable")
                            val invalid = validation.entries.filter { (_, value) ->
                                (value as? JsonObject)?.get("valid")?.toString() != "true"
                            }
                            require(invalid.isEmpty()) {
                                "Invalid automation configuration: " +
                                    invalid.joinToString { it.key + ": " + it.value.toString() }
                            }
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
                            editError = tr("Записът е неуспешен. Обнови правилото и опитай отново.", "Could not save. Refresh the rule and try again.")
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
                    Text(if (configLoading) tr("Зареждане…", "Loading…") else (config?.get("config") as? JsonObject)?.get("alias")?.jsonPrimitive?.contentOrNull ?: tr("Правилото е недостъпно", "Rule unavailable"))
                }
            },
            confirmButton = {
                Button(enabled = !configLoading && config?.get("config") is JsonObject, onClick = {
                    val raw = config?.get("config") as? JsonObject ?: return@Button
                    val id = raw["id"]?.jsonPrimitive?.contentOrNull
                    if (id.isNullOrBlank()) {
                        status = tr("Автоматизацията няма ID", "Automation has no ID")
                    } else {
                        editId = id
                        editText = raw.toString()
                        ruleAlias = raw["alias"]?.jsonPrimitive?.contentOrNull.orEmpty()
                        val firstTrigger = ((raw["triggers"] ?: raw["trigger"]) as? JsonArray)?.firstOrNull() as? JsonObject
                        val firstAction = ((raw["actions"] ?: raw["action"]) as? JsonArray)?.firstOrNull() as? JsonObject
                        triggerEntity = firstTrigger?.get("entity_id")?.jsonPrimitive?.contentOrNull.orEmpty()
                        triggerTo = firstTrigger?.get("to")?.jsonPrimitive?.contentOrNull ?: "on"
                        actionEntity = (firstAction?.get("target") as? JsonObject)?.get("entity_id")?.jsonPrimitive?.contentOrNull.orEmpty()
                        actionService = firstAction?.get("service")?.jsonPrimitive?.contentOrNull?.substringAfter(".") ?: "turn_on"
                        advancedEditor = firstTrigger?.get("platform")?.jsonPrimitive?.contentOrNull != "state" ||
                            firstTrigger?.keys?.any { it !in setOf("platform","entity_id","to") } == true ||
                            firstAction?.keys?.any { it !in setOf("service","target") } == true ||
                            (firstAction?.get("target") as? JsonObject)?.keys != setOf("entity_id") ||
                            triggerEntity.isBlank() || actionEntity.isBlank() ||
                            ((raw["triggers"] ?: raw["trigger"]) as? JsonArray)?.size != 1 ||
                            ((raw["actions"] ?: raw["action"]) as? JsonArray)?.size != 1 ||
                            actionService !in listOf("turn_on", "turn_off") ||
                            ((raw["conditions"] ?: raw["condition"]) as? JsonArray)?.isNotEmpty() == true
                        baseline = raw
                        sourceEntityId = inspecting
                        editError = ""
                        inspecting = null
                    }
                }) { Text(tr("Редактирай", "Edit")) }
            },
            dismissButton = {
                OutlinedButton(enabled = !configLoading, onClick = { inspecting = null; config = null }) {
                    Text(tr("Затвори", "Close"))
                }
            },
        )
    }
    pendingAction?.let { (id, previousState) ->
        AlertDialog(
            onDismissRequest = { if (busy == null) pendingAction = null },
            title = { Text(tr("Потвърди промяната", "Confirm change")) },
            text = { Text(tr("Промяна на автоматизация: ", "Change automation: ") + id.substringAfter('.').replace('_', ' ')) },
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
        if (loading) Text(tr("Зареждане…", "Loading…"), color = Color.White)
        if (!loading && automations.isEmpty()) {
            Text(tr("Няма достъпни автоматизации.", "No available automations."),
                color = Color(0xFFA6BCCD))
        }
        Button(enabled = busy == null && !loading, onClick = {
            advancedEditor = false
            ruleAlias = tr("Нова STEVORA автоматизация", "New STEVORA automation")
            triggerEntity = ""
            triggerTo = "on"
            actionEntity = ""
            actionService = "turn_on"
            editId = UUID.randomUUID().toString().replace("-", "")
            editText = """{"alias":"New STEVORA automation","triggers":[],"conditions":[],"actions":[],"mode":"single"}"""
            baseline = null
            sourceEntityId = null
            editError = ""
        }) { Text(tr("Нова автоматизация", "New automation")) }
        FilterChip(
            selected = onlyEnabled,
            onClick = { onlyEnabled = !onlyEnabled },
            label = { Text(tr("Само активни", "Enabled only")) },
        )
        val available = remember(automations, onlyEnabled) { automations.filter { !onlyEnabled || it.second == "on" } }
        val choices = remember(available) { available.map { (id, _) ->
            StevoraChoice(id, id.removePrefix("automation.").replace('_', ' '), "")
        } }
        StevoraSettingsPicker(androidx.compose.ui.res.stringResource(io.homeassistant.companion.android.common.R.string.stevora_choose_routine),
            choices, selectedAutomation) { selectedAutomation = it }
        available.firstOrNull { it.first == selectedAutomation }?.let { (id, state) ->
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
