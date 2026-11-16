package io.homeassistant.companion.android.stevora

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

private val aiBg = Color(0xFF08131F)
private val aiTile = Color(0xFF122437)
private val aiCyan = Color(0xFF27B9F5)

@Composable
internal fun StevoraAiHome(
    onStartConversation: () -> Unit,
    onConfigurePipeline: () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current.applicationContext
    val manager = remember(context) {
        EntryPointAccessors.fromApplication(context, StevoraServerEntryPoint::class.java).serverManager()
    }
    val scope = rememberCoroutineScope()
    var input by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var pipelineId by remember { mutableStateOf<String?>(null) }
    var pipelineStatus by remember { mutableStateOf("Checking account and Assist pipeline…") }
    var conversationId by remember { mutableStateOf<String?>(null) }
    var messages by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }

    LaunchedEffect(manager) {
        try {
            val socket = if (manager.getServer() != null) manager.webSocketRepository() else null
            val user = socket?.getCurrentUser()
            // Do not guess a role when authentication fails.
            if (user == null) {
                pipelineId = null
                pipelineStatus = "Unable to verify Home Assistant account"
            } else {
                val admin = user.isAdmin || user.isOwner
                val requiredName = if (admin) "OpenAI Conversion" else "OpenAI Conversion for Panels"
                val pipelines = socket.getAssistPipelines()
                // Exact name matching prevents accidental routing to the wrong agent.
                val selected = pipelines?.pipelines?.firstOrNull {
                    it.name.trim().equals(requiredName, ignoreCase = true)
                }
                pipelineId = selected?.id
                pipelineStatus = if (selected == null) {
                    "$requiredName not found in Assist pipelines"
                } else {
                    "● " + selected.name + if (admin) " • ADMIN" else " • USER / PANEL"
                }
                conversationId = null
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            pipelineStatus = "Unable to load Assist pipelines"
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().background(aiBg).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("STEVORA AI", color = Color.White, fontSize = 28.sp)
        Text("NATIVE CONVERSATION • HOME HUB", color = aiCyan, fontSize = 11.sp)
        Text(pipelineStatus, color = Color(0xFFA6BCCD), fontSize = 12.sp)
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
                            val events = manager.webSocketRepository().runAssistPipelineForText(
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
            OutlinedButton(onClick = onStartConversation) { Text("Voice Assist") }
        }
        OutlinedButton(onClick = onBack) { Text("Home") }
    }
}
