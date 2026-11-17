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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collect
import io.homeassistant.companion.android.common.data.websocket.impl.entities.AssistPipelineEventType
import io.homeassistant.companion.android.common.data.websocket.impl.entities.AssistPipelineIntentEnd
import io.homeassistant.companion.android.common.data.websocket.impl.entities.AssistPipelineError

/**
 * Secure first-stage memory manager: authenticated HA diagnostics only.
 * The separate MCP endpoint is NOT the HA WebSocket API. Never guess its URL,
 * forward HA bearer credentials to it, or claim that MCP memory is readable here.
 */
@Composable
internal fun StevoraMemoryManager(onBack: () -> Unit) {
    val context = LocalContext.current.applicationContext
    val bg = LocalConfiguration.current.locales[0].language == "bg"
    fun tr(a: String, b: String) = if (bg) a else b
    val manager = remember(context) {
        EntryPointAccessors.fromApplication(context, StevoraServerEntryPoint::class.java).serverManager()
    }
    val scope = rememberCoroutineScope()
    var refresh by remember { mutableStateOf(0) }
    var query by remember { mutableStateOf("") }
    var aiResult by remember { mutableStateOf<String?>(null) }
    var searching by remember { mutableStateOf(false) }
    var memoryPipeline by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    var authorized by remember { mutableStateOf(false) }
    var hubConnected by remember { mutableStateOf(false) }
    var memoryEntities by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(manager, refresh) {
        loading = true
        authorized = false
        hubConnected = false
        memoryEntities = emptyList()
        memoryPipeline = null
        aiResult = null
        error = null
        try {
            val socket = if (manager.getServer() != null) manager.webSocketRepository() else null
            val user = socket?.getCurrentUser()
            authorized = user?.isAdmin == true || user?.isOwner == true
            if (authorized && socket != null) {
                val states = socket.getStates()
                hubConnected = states != null
                if (states != null) {
                    val pipelines = socket.getAssistPipelines()
                    memoryPipeline = pipelines?.pipelines?.filter {
                        it.conversationEngine.equals("conversation.living_room_openai_conversation", ignoreCase = true)
                    }?.singleOrNull()?.id
                    val registry = try {
                        socket.getEntityRegistry()?.associateBy { it.entityId }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        null
                    }
                    // Show only explicitly named STEVORA memory diagnostics, never
                    // arbitrary entities matching a broad "memory" substring.
                    val allowed = setOf(
                        "sensor.stevora_memory_status",
                        "sensor.stevora_memory_pending_review",
                        "sensor.stevora_memory_last_sync",
                    )
                    memoryEntities = states.filter {
                        it.entityId in allowed &&
                            registry?.get(it.entityId)?.hiddenBy == null &&
                            registry?.get(it.entityId)?.entityCategory == null
                    }.map { it.entityId to it.state }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            error = tr("Неуспешна проверка на Home Hub", "Home Hub verification failed")
        } finally {
            loading = false
        }
    }
    Column(
        Modifier.fillMaxSize().background(Color(0xFF08131F))
            .verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("STEVORA • MEMORY MANAGER", color = Color(0xFF27B9F5))
        Text(
            if (loading) tr("Проверка на достъпа…", "Verifying access…")
            else if (!authorized) tr("Само за администратор", "Administrator access only")
            else if (!hubConnected) tr("Home Hub не е достъпен", "Home Hub unavailable")
            else tr("Защитена връзка с Home Assistant", "Authenticated Home Assistant connection"),
            color = Color.White,
        )
        if (authorized && hubConnected) {
            Text(
                tr(
                    "Паметта на STEVORA се управлява от отделния Memory MCP. " +
                        "Този екран не чете и не променя файлове през непроверен адрес.",
                    "STEVORA memory is managed by a separate Memory MCP service. " +
                        "This screen does not read or modify files through an unverified endpoint.",
                ),
                color = Color(0xFFA6BCCD),
            )
            if (memoryEntities.isEmpty()) {
                Text(
                    tr("Няма публикувани диагностични сензори за паметта в Home Assistant.",
                        "No memory diagnostic sensors are exposed by Home Assistant."),
                    color = Color(0xFFA6BCCD),
                )
            }
            memoryEntities.forEach { (id, state) ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF122437)),
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(id.removePrefix("sensor.").replace('_', ' '), color = Color(0xFFA6BCCD))
                        Text(state, color = Color.White)
                    }
                }
            }
            Text(
                tr(
                    "Търсене чрез STEVORA AI (не е директен достъп до Memory MCP). " +
                        "Резултатите са генерирани от AI и трябва да се проверяват.",
                    "Search via STEVORA AI (not direct Memory MCP access). " +
                        "AI-generated results must be verified.",
                ),
                color = Color(0xFFA6BCCD),
            )
            OutlinedTextField(
                value = query,
                onValueChange = { query = it.take(200) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(tr("Търси в знанията на STEVORA", "Ask STEVORA about project knowledge")) },
                enabled = !searching && memoryPipeline != null,
                maxLines = 3,
            )
            OutlinedButton(
                enabled = !searching && query.isNotBlank() && memoryPipeline != null,
                onClick = {
                    val selected = memoryPipeline ?: return@OutlinedButton
                    val question = query.trim()
                    searching = true
                    aiResult = null
                    scope.launch {
                        try {
                            val currentUser = manager.webSocketRepository().getCurrentUser()
                            if (currentUser == null || (!currentUser.isAdmin && !currentUser.isOwner)) {
                                aiResult = tr("Отказан достъп", "Permission denied")
                            } else {
                                val events = manager.webSocketRepository().runAssistPipelineForText(
                                    text = "STEVORA project memory question: " + question,
                                    pipelineId = selected,
                                )
                                if (events == null) {
                                    aiResult = tr("AI не отговори", "AI did not respond")
                                } else {
                                    events.collect { event ->
                                        when (event.type) {
                                            AssistPipelineEventType.INTENT_END -> {
                                                val output = (event.data as? AssistPipelineIntentEnd)?.intentOutput
                                                aiResult = output?.response?.speech?.plain?.get("speech")
                                                    ?: tr("Няма резултат", "No result")
                                            }
                                            AssistPipelineEventType.ERROR -> {
                                                aiResult = (event.data as? AssistPipelineError)?.message
                                                    ?: tr("Грешка в AI", "AI error")
                                            }
                                        }
                                    }
                                }
                            }
                        } catch (e: CancellationException) {
                            throw e
                        } catch (_: Exception) {
                            aiResult = tr("Грешка при заявката", "Request failed")
                        } finally {
                            searching = false
                        }
                    }
                },
            ) { Text(if (searching) tr("Търсене…", "Searching…") else tr("Попитай STEVORA", "Ask STEVORA")) }
            aiResult?.let { answer ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF122437)),
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(tr("Отговор от AI • непотвърден", "AI answer • unverified"),
                            color = Color(0xFF27B9F5))
                        Text(answer, color = Color.White)
                    }
                }
            }
            Text(
                tr(
                    "Директен преглед, одобряване и запис на каноничната памет " +
                        "ще бъдат активирани само след защитен Home Assistant мост.",
                    "Direct canonical memory reading, approvals and writes " +
                        "require a secured Home Assistant bridge and remain disabled.",
                ),
                color = Color(0xFFA6BCCD),
            )
        }
        error?.let { Text(it, color = Color(0xFFFFA76B)) }
        OutlinedButton(onClick = { refresh++ }) { Text(tr("Обнови", "Refresh")) }
        OutlinedButton(onClick = onBack) { Text(tr("Назад", "Back")) }
    }
}
