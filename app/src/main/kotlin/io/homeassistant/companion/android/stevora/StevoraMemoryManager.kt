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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CancellationException

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
    var refresh by remember { mutableStateOf(0) }
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
        error = null
        try {
            val socket = if (manager.getServer() != null) manager.webSocketRepository() else null
            val user = socket?.getCurrentUser()
            authorized = user?.isAdmin == true || user?.isOwner == true
            if (authorized && socket != null) {
                val states = socket.getStates()
                hubConnected = states != null
                if (states != null) {
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
                    "Прегледът на решения, предложенията за одобрение и търсенето " +
                        "изискват удостоверен мост към Memory MCP и още не са активирани.",
                    "Reviewing decisions, approval proposals and search require an authenticated " +
                        "Memory MCP bridge and are not enabled yet.",
                ),
                color = Color(0xFFA6BCCD),
            )
        }
        error?.let { Text(it, color = Color(0xFFFFA76B)) }
        OutlinedButton(onClick = { refresh++ }) { Text(tr("Обнови", "Refresh")) }
        OutlinedButton(onClick = onBack) { Text(tr("Назад", "Back")) }
    }
}
