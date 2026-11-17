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
import androidx.compose.ui.unit.sp
import dagger.hilt.android.EntryPointAccessors
import io.homeassistant.companion.android.common.data.servers.ServerManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

@Composable
internal fun StevoraSystemHome(onBack: () -> Unit) {
    val context = LocalContext.current.applicationContext
    val bgLanguage = LocalConfiguration.current.locales[0].language == "bg"
    fun localized(bg: String, en: String) = if (bgLanguage) bg else en
    val manager = remember(context) {
        EntryPointAccessors.fromApplication(context, StevoraServerEntryPoint::class.java).serverManager()
    }
    var connection by remember { mutableStateOf(localized("Проверка на Home Hub…", "Checking Home Hub…")) }
    var total by remember { mutableStateOf<Int?>(null) }
    var unavailable by remember { mutableStateOf<Int?>(null) }
    var aiRouting by remember { mutableStateOf(localized("Проверка…", "Checking…")) }

    LaunchedEffect(manager) {
        while (isActive) {
            try {
                val socket = if (manager.getServer() != null) manager.webSocketRepository() else null
                val snapshot = socket?.getStates()
                val user = socket?.getCurrentUser()
                val engine = when {
                    user == null -> null
                    user.isAdmin || user.isOwner -> "conversation.living_room_openai_conversation"
                    else -> "conversation.openai_conversation_for_panels"
                }
                val matches = if (engine == null) null else socket?.getAssistPipelines()
                    ?.pipelines?.count { it.conversationEngine.equals(engine, ignoreCase = true) }
                aiRouting = when (matches) {
                    1 -> "● READY"
                    0 -> localized("Липсва AI pipeline", "Pipeline missing")
                    null -> localized("Недостъпно", "Unavailable")
                    else -> localized("Нееднозначно ($matches)", "Ambiguous ($matches)")
                }
                connection = if (snapshot != null) "● ONLINE" else "● OFFLINE"
                total = snapshot?.size
                unavailable = snapshot?.count { it.state == "unavailable" || it.state == "unknown" }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                connection = "● OFFLINE"
                total = null
                unavailable = null
                aiRouting = "Unavailable"
            }
            delay(10_000L)
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().background(Color(0xFF08131F))
            .verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("STEVORA", fontSize = 28.sp, color = Color(0xFFF4FAFF), letterSpacing = 3.sp)
        Text(localized("СИСТЕМА • HOME HUB", "SYSTEM • HOME HUB"), fontSize = 12.sp, color = Color(0xFF27B9F5))
        listOf(
            localized("Връзка с Home Hub", "Home Hub connection") to connection,
            localized("Регистрирани устройства и състояния", "Registered entity states") to (total?.toString() ?: "—"),
            localized("Недостъпни или неизвестни", "Unavailable or unknown") to (unavailable?.toString() ?: "—"),
            localized("Маршрутизиране на AI", "AI pipeline routing") to aiRouting,
        ).forEach { (title, value) ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF122437)),
            ) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(title, fontSize = 13.sp, color = Color(0xFFA6BCCD))
                    Text(value, fontSize = 23.sp, color = Color(0xFFF4FAFF))
                }
            }
        }
        Text(
            localized("Диагностика от Home Assistant в реално време. Обновяване на всеки 10 секунди.", "Live Home Assistant connection diagnostics. Values refresh every 10 seconds."),
            fontSize = 12.sp, color = Color(0xFFA6BCCD),
        )
        OutlinedButton(onClick = onBack) { Text(localized("Начало", "Home")) }
    }
}
