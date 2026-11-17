package io.homeassistant.companion.android.stevora

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

private val climateBg = Color(0xFF08131F)
private val climateTile = Color(0xFF122437)
private val climateCyan = Color(0xFF27B9F5)
private val climateMuted = Color(0xFFA6BCCD)

@Composable
internal fun StevoraClimateAnalytics(onBack: () -> Unit) {
    val context = LocalContext.current.applicationContext
    val bg = LocalConfiguration.current.locales[0].language == "bg"
    fun tr(bgText: String, enText: String) = if (bg) bgText else enText
    val manager = remember(context) {
        EntryPointAccessors.fromApplication(context, StevoraServerEntryPoint::class.java).serverManager()
    }
    var online by remember { mutableStateOf(false) }
    var temperature by remember { mutableStateOf<Double?>(null) }
    var humidity by remember { mutableStateOf<Double?>(null) }
    var samples by remember { mutableStateOf<List<Pair<Double, Double>>>(emptyList()) }

    LaunchedEffect(manager) {
        while (isActive) {
            try {
                val states = if (manager.getServer() != null) manager.webSocketRepository().getStates() else null
                online = states != null
                if (states != null) {
                    temperature = states.firstOrNull { it.entityId == "sensor.realna_temperatura_homepod" }?.state?.toDoubleOrNull()
                    humidity = states.firstOrNull { it.entityId == "sensor.realna_vlazhnost_homepod" }?.state?.toDoubleOrNull()
                    val t = temperature
                    val h = humidity
                    if (t != null && h != null) samples = (samples + (t to h)).takeLast(360)
                } else {
                    temperature = null
                    humidity = null
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                online = false
                temperature = null
                humidity = null
            }
            delay(10_000L)
        }
    }
    Column(
        Modifier.fillMaxSize().background(climateBg).verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("STEVORA", color = Color.White, fontSize = 28.sp)
        Text("CLIMATE ANALYTICS", color = climateCyan, fontSize = 12.sp)
        Text(
            if (online) tr("● Свързано с Home Hub", "● Connected to Home Hub")
            else tr("● Няма връзка", "● Offline"),
            color = climateMuted,
        )
        listOf(
            tr("Температура • HomePod", "Temperature • HomePod") to (temperature?.let { "$it °C" } ?: "—"),
            tr("Влажност • HomePod", "Humidity • HomePod") to (humidity?.let { "$it %" } ?: "—"),
            tr("Измервания в сесията", "Samples this session") to samples.size.toString(),
            tr("Средна температура в сесията", "Session average temperature") to
                (if (samples.isEmpty()) "—" else "%.1f °C".format(samples.map { it.first }.average())),
            tr("Средна влажност в сесията", "Session average humidity") to
                (if (samples.isEmpty()) "—" else "%.1f %%".format(samples.map { it.second }.average())),
        ).forEach { (label, value) ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = climateTile),
            ) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(label, color = climateMuted, fontSize = 13.sp)
                    Text(value, color = Color.White, fontSize = 24.sp)
                }
            }
        }
        Text(
            tr(
                "Данните са реални и се събират само докато този екран е отворен. " +
                    "Историческите графики от Home Assistant Recorder още не са свързани.",
                "Live values are real and samples are collected only while this screen is open. " +
                    "Home Assistant Recorder history charts are not connected yet.",
            ),
            color = climateMuted,
            fontSize = 12.sp,
        )
        OutlinedButton(onClick = onBack) { Text(tr("Назад", "Back")) }
    }
}
