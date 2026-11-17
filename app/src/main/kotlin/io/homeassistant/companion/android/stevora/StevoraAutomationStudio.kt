package io.homeassistant.companion.android.stevora

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedButton
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
        automations.forEach { (id, state) ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF122437)),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(id.removePrefix("automation.").replace('_', ' '), color = Color.White)
                    Text(state, color = Color(0xFFA6BCCD))
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
    }
}
