package io.homeassistant.companion.android.stevora

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull

private val climateBg = StevoraDesign.background
private val climateTile = StevoraDesign.surface
private val climateCyan = StevoraDesign.accent
private val climateMuted = StevoraDesign.muted

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
    var historyHours by remember { mutableStateOf(24) }
    var historyRefresh by remember { mutableStateOf(0) }
    var historyLoading by remember { mutableStateOf(false) }
    var historyValues by remember { mutableStateOf<List<Double>>(emptyList()) }
    var humidityHistory by remember { mutableStateOf<List<Double>>(emptyList()) }
    var historyStatus by remember { mutableStateOf("") }
    LaunchedEffect(manager, historyHours, historyRefresh) {
        historyLoading = true
        historyValues = emptyList()
        humidityHistory = emptyList()
        historyStatus = tr("Зареждане на историята…", "Loading history…")
        try {
            val end = Instant.now()
            val start = end.minus(historyHours.toLong(), ChronoUnit.HOURS)
            val result = if (manager.getServer() == null) null else
                manager.webSocketRepository().getStevoraClimateHistory(
                    start.toString(), end.toString(),
                    listOf("sensor.realna_temperatura_homepod", "sensor.realna_vlazhnost_homepod"),
                )
            val series = result?.mapNotNull { group ->
                val entries = group as? kotlinx.serialization.json.JsonArray
                    ?: return@mapNotNull null
                val id = entries.firstOrNull()?.jsonObject?.get("entity_id")
                    ?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                id to entries.mapNotNull { item ->
                    item.jsonObject["state"]?.jsonPrimitive?.contentOrNull?.toDoubleOrNull()
                        ?.takeIf { it.isFinite() }
                }.takeLast(600)
            }?.toMap().orEmpty()
            historyValues = series["sensor.realna_temperatura_homepod"].orEmpty()
            humidityHistory = series["sensor.realna_vlazhnost_homepod"].orEmpty()
            historyStatus = if (historyValues.isEmpty() && humidityHistory.isEmpty()) {
                tr("Няма записани измервания или Recorder не е достъпен",
                    "No recorded samples or Recorder unavailable")
            } else tr("История от Home Assistant Recorder", "Home Assistant Recorder history")
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            historyStatus = tr("Неуспешно зареждане", "History loading failed")
        } finally {
            historyLoading = false
        }
    }

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
        Text(tr("Температура • исторически данни", "Temperature • historical data"), color = climateCyan)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(24, 72, 168).forEach { hours ->
                FilterChip(
                    selected = historyHours == hours,
                    onClick = { historyHours = hours },
                    label = { Text(if (hours == 24) "24h" else if (hours == 72) "3d" else "7d") },
                )
            }
        }
        Text(historyStatus, color = climateMuted, fontSize = 12.sp)
        OutlinedButton(
            enabled = !historyLoading,
            onClick = { historyRefresh++ },
        ) { Text(tr("Обнови историята", "Refresh history")) }
        if (historyValues.size >= 2) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = climateTile),
                shape = RoundedCornerShape(18.dp),
            ) {
                val low = historyValues.minOrNull() ?: 0.0
                val high = historyValues.maxOrNull() ?: 0.0
                val span = (high - low).coerceAtLeast(0.5)
                Canvas(Modifier.fillMaxWidth().height(140.dp).padding(16.dp)) {
                    val trace = Path()
                    historyValues.forEachIndexed { index, value ->
                        val x = size.width * index / (historyValues.size - 1)
                        val y = size.height * (1f - ((value - low) / span).toFloat())
                        if (index == 0) trace.moveTo(x, y) else trace.lineTo(x, y)
                    }
                    drawPath(trace, climateCyan, style = Stroke(width = 3.dp.toPx()))
                }
                Text("%.1f – %.1f °C".format(low, high),
                    modifier = Modifier.padding(16.dp), color = climateMuted)
            }
        }
        if (humidityHistory.size >= 2) {
            Text(tr("Влажност • исторически данни", "Humidity • historical data"), color = climateCyan)
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = climateTile),
                shape = RoundedCornerShape(18.dp),
            ) {
                val low = humidityHistory.minOrNull() ?: 0.0
                val high = humidityHistory.maxOrNull() ?: 0.0
                val span = (high - low).coerceAtLeast(0.5)
                Canvas(Modifier.fillMaxWidth().height(140.dp).padding(16.dp)) {
                    val trace = Path()
                    humidityHistory.forEachIndexed { index, value ->
                        val x = size.width * index / (humidityHistory.size - 1)
                        val y = size.height * (1f - ((value - low) / span).toFloat())
                        if (index == 0) trace.moveTo(x, y) else trace.lineTo(x, y)
                    }
                    drawPath(trace, climateCyan, style = Stroke(width = 3.dp.toPx()))
                }
                Text("%.1f – %.1f %%".format(low, high),
                    modifier = Modifier.padding(16.dp), color = climateMuted)
            }
        }
        if (samples.size >= 2) {
            Text(tr("Тенденция на температурата • текуща сесия", "Temperature trend • current session"),
                color = climateCyan)
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = climateTile),
            ) {
                val values = samples.map { it.first }
                val low = values.minOrNull() ?: 0.0
                val high = values.maxOrNull() ?: 0.0
                val span = (high - low).coerceAtLeast(0.5)
                Canvas(Modifier.fillMaxWidth().height(140.dp).padding(16.dp)) {
                    val trace = Path()
                    values.forEachIndexed { index, value ->
                        val x = size.width * index / (values.size - 1)
                        val y = size.height * (1f - ((value - low) / span).toFloat())
                        if (index == 0) trace.moveTo(x, y) else trace.lineTo(x, y)
                    }
                    drawPath(trace, climateCyan, style = Stroke(width = 3.dp.toPx()))
                }
                Text(
                    tr("Минимум", "Minimum") + ": %.1f °C".format(low) +
                        "  •  " + tr("Максимум", "Maximum") + ": %.1f °C".format(high),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    color = climateMuted,
                )
            }
        }
        Text(
            tr(
                "Данните са реални и се събират само докато този екран е отворен. " +
                    "Историческите графики използват Home Assistant Recorder.",
                "Live values are real and samples are collected only while this screen is open. " +
                    "Historical charts use Home Assistant Recorder.",
            ),
            color = climateMuted,
            fontSize = 12.sp,
        )
        OutlinedButton(onClick = onBack) { Text(tr("Назад", "Back")) }
        Spacer(Modifier.height(88.dp))
    }
}
