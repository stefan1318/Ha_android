package io.homeassistant.companion.android.stevora

import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dagger.hilt.android.EntryPointAccessors
import io.homeassistant.companion.android.common.data.websocket.impl.entities.AssistPipelineEventType
import io.homeassistant.companion.android.common.data.websocket.impl.entities.AssistPipelineIntentEnd
import io.homeassistant.companion.android.common.data.websocket.impl.entities.AssistPipelineError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

private val aiBg = StevoraDesign.background
private val aiTile = StevoraDesign.surface
private val aiCyan = StevoraDesign.accent

@Composable
internal fun StevoraAiHome(
    onStartConversation: (Int, String) -> Unit,
    onConfigurePipeline: () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current.applicationContext
    val services = remember(context) {
        EntryPointAccessors.fromApplication(context, StevoraServerEntryPoint::class.java)
    }
    val manager = services.serverManager()
    val preferences = remember(context) { StevoraVoicePreferences(context) }
    val scope = rememberCoroutineScope()
    var input by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val bg = androidx.compose.ui.platform.LocalConfiguration.current.locales[0].language == "bg"
    val voiceFolder = remember(context) { java.io.File(context.filesDir, "stevora/voice-samples") }
    var listening by remember { mutableStateOf(false) }
    var listenStatus by remember { mutableStateOf("") }
    var awake by remember { mutableStateOf(false) }
    androidx.lifecycle.compose.LifecycleResumeEffect(services) {
        awake = true
        onPauseOrDispose { awake = false }
    }
    var routeServer by remember { mutableStateOf<Int?>(null) }
    var pipelineId by remember { mutableStateOf<String?>(null) }
    var pipelineHasStt by remember { mutableStateOf(false) }
    var refreshPipelines by remember { mutableStateOf(0) }
    var accountRole by remember { mutableStateOf("UNVERIFIED") }
    var pipelineStatus by remember { mutableStateOf("Checking account and Assist pipeline…") }
    var conversationId by remember { mutableStateOf<String?>(null) }
    var messages by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }

    LaunchedEffect(manager, refreshPipelines) {
        try {
            routeServer = null
            pipelineId = null
            pipelineHasStt = false
            val server = manager.getServer()
            val socket = server?.let { manager.webSocketRepository(it.id) }
            val user = socket?.getCurrentUser()
            if (user == null) {
                accountRole = "UNVERIFIED"
                pipelineStatus = "Unable to verify Home Assistant account"
            } else {
                val admin = user.isAdmin || user.isOwner
                accountRole = if (admin) "ADMIN" else "USER / PANEL"
                val selected = socket.resolveStevoraPipeline()
                pipelineId = selected?.id
                pipelineHasStt = selected?.sttEngine != null
                routeServer = server?.id
                pipelineStatus = selected?.name ?: if (bg) "Няма избран асистент" else "No assistant selected"
                conversationId = null
                messages = emptyList()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            pipelineId = null
            pipelineStatus = "Unable to load Assist pipelines"
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().background(StevoraDesign.gradient).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("STEVORA AI", color = Color.White, fontSize = 28.sp)
        Text("NATIVE CONVERSATION • HOME HUB", color = aiCyan, fontSize = 11.sp)
        Text(pipelineStatus, color = Color(0xFFA6BCCD), fontSize = 12.sp)
        OutlinedButton(onClick = { refreshPipelines++ }, enabled = !busy) { Text("Refresh AI connection") }
        Column(
            modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            messages.forEach { (sender, message) ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = aiTile),
                ) {
                    Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(sender, color = aiCyan, fontSize = 11.sp)
                        Text(message, color = Color.White, fontSize = 15.sp)
                    }
                }
            }
            if (messages.isEmpty()) {
                Spacer(Modifier.height(18.dp))
                Text("Your home. Your intelligence.", color = Color(0xFFA6BCCD))
            }
        }
        OutlinedTextField(
            value = input,
            onValueChange = { input = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Message to STEVORA") },
            enabled = !busy && pipelineId != null,
            maxLines = 4,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                enabled = !busy && pipelineId != null && input.isNotBlank(),
                onClick = {
                    val message = input.trim()
                    val selected = pipelineId ?: return@Button
                    input = ""
                    messages = messages + ("YOU" to message)
                    busy = true
                    scope.launch {
                        try {
                            val id = routeServer ?: return@launch
                            val socket = manager.webSocketRepository(id)
                            if (manager.getServer()?.id != id || socket.resolveStevoraPipeline()?.id != selected) {
                                refreshPipelines++
                                return@launch
                            }
                            val events = socket.runAssistPipelineForText(
                                text = message, pipelineId = selected, conversationId = conversationId,
                            )
                            if (events == null) {
                                messages = messages + ("STEVORA" to "Home Hub did not start the conversation.")
                            } else {
                                var answered = false
                                events.collect { event ->
                                    when (event.type) {
                                        AssistPipelineEventType.INTENT_END -> {
                                            val response = (event.data as? AssistPipelineIntentEnd)?.intentOutput
                                            conversationId = response?.conversationId ?: conversationId
                                            val reply = response?.response?.speech?.plain?.get("speech")
                                                ?: "Command processed."
                                            messages = messages + ("STEVORA" to reply)
                                            answered = true
                                        }
                                        AssistPipelineEventType.ERROR -> {
                                            val error = event.data as? AssistPipelineError
                                            messages = messages + ("STEVORA" to (error?.message ?: "Assist error"))
                                            answered = true
                                        }
                                    }
                                }
                                if (!answered) messages = messages + ("STEVORA" to "No response from Assist.")
                            }
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            messages = messages + ("STEVORA" to "Connection failed. Try again.")
                        } finally {
                            busy = false
                        }
                    }
                },
            ) { Text(if (busy) "Sending…" else "Send") }
            OutlinedButton(
                enabled = pipelineId != null && pipelineHasStt && !busy && !listening,
                onClick = {
                    val selected = pipelineId ?: return@OutlinedButton
                    scope.launch {
                        try {
                            val id = routeServer ?: return@launch
                            if (manager.getServer()?.id == id && manager.webSocketRepository(id).resolveStevoraPipeline()?.id == selected) {
                                onStartConversation(id, selected)
                            } else {
                                refreshPipelines++
                            }
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            messages = messages + ("STEVORA" to "Unable to select voice pipeline.")
                        }
                    }
                },
            ) { Text("Voice Assist") }
        }
        if (listenStatus.isNotEmpty()) Text(listenStatus, color = Color.White)
        LaunchedEffect(awake, routeServer, pipelineId, pipelineHasStt, busy) {
            if (!awake || busy || !preferences.automaticListening) return@LaunchedEffect
            val id = routeServer
            val selected = pipelineId
            val assistant = services.defaultAssistantManager()
            if (id == null || selected == null || !pipelineHasStt) {
                listenStatus = if (bg) "Няма достъпен Assist pipeline с разпознаване на реч."
                    else "No Assist pipeline with speech recognition is available."
                return@LaunchedEffect
            }
            if (!assistant.isDefaultAssistant() ||
                context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED
            ) {
                listenStatus = if (bg) "Провери системния асистент и разрешението за микрофона."
                    else "Check the system assistant and microphone permission."
                return@LaunchedEffect
            }
            try {
                val systemAssist = services.assistConfigManager()
                if (preferences.backgroundServerId != null) {
                    listenStatus = if (bg) "Фоновият режим управлява микрофона. Провери известието за слушане."
                        else "Background mode controls the microphone. Check the listening notification."
                    return@LaunchedEffect
                }
                if (systemAssist.isWakeWordEnabled()) {
                    listenStatus = if (bg) "Друг системен детектор за фраза е включен."
                        else "Another system wake-word detector is enabled."
                    return@LaunchedEffect
                }
                val ready = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { StevoraPhraseModel.ready(voiceFolder) }
                if (!ready) {
                    listenStatus = if (bg) "Няма валиден гласов профил. Обучи го в Настройки → Hey Stevora."
                        else "No valid voice profile. Train it in Settings → Hey Stevora."
                    return@LaunchedEffect
                }
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    StevoraPhraseModel.listen(voiceFolder) {
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                            listening = true
                            listenStatus = if (bg) "Микрофонът слуша локално за „Hey Stevora“."
                                else "Microphone listening locally for “Hey Stevora”."
                        }
                    }
                }
                val sameRoute = manager.getServer()?.id == id &&
                    manager.webSocketRepository(id).resolveStevoraPipeline()?.id == selected
                if (sameRoute && awake && preferences.automaticListening && assistant.isDefaultAssistant() &&
                    manager.getServer()?.id == id
                ) {
                    listenStatus = ""
                    onStartConversation(id, selected)
                }
            } catch (e: CancellationException) {
                listenStatus = ""
                throw e
            } catch (_: Exception) {
                listenStatus = if (bg) "Провери достъпа до микрофона и образците."
                    else "Check microphone access and samples."
            } finally {
                listening = false
            }
        }
        OutlinedButton(onClick = onBack) { Text("Home") }
    }
}
