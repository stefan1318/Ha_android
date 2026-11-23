package io.homeassistant.companion.android.stevora

import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import io.homeassistant.companion.android.common.data.integration.Entity
import kotlin.math.roundToInt

private data class DeviceOption(val action: String, val service: String, val field: String, val en: String, val bg: String)

private val numbers = listOf(
    DeviceOption("color_temp", "turn_on", "color_temp_kelvin", "Color temperature (K)", "Цветна температура (K)"),
    DeviceOption("percentage", "set_percentage", "percentage", "Speed (%)", "Скорост (%)"),
    DeviceOption("position", "set_cover_position", "position", "Position (%)", "Позиция (%)"),
    DeviceOption("tilt", "set_cover_tilt_position", "tilt_position", "Tilt (%)", "Наклон (%)"),
)

private val choices = listOf(
    DeviceOption("effect", "turn_on", "effect", "Effect", "Ефект"),
    DeviceOption("preset_mode", "set_preset_mode", "preset_mode", "Preset", "Профил"),
    DeviceOption("fan_mode", "set_fan_mode", "fan_mode", "Fan mode", "Режим на вентилатора"),
    DeviceOption("swing_mode", "set_swing_mode", "swing_mode", "Swing", "Жалузи"),
    DeviceOption("source", "select_source", "source", "Source", "Източник"),
    DeviceOption("direction", "set_direction", "direction", "Direction", "Посока"),
    DeviceOption("oscillate", "oscillate", "oscillating", "Oscillation", "Осцилация"),
)

/** Render only controls explicitly advertised for this entity by the active home. */
@Composable
internal fun StevoraDeviceOptions(entity: Entity, actions: Set<String>, bg: Boolean, send: (String, Map<String, Any?>) -> Unit) {
    val attributes = entity.attributes
    for (option in numbers.filter { it.action in actions }) {
        val kelvin = option.action == "color_temp"
        val low = if (kelvin) (attributes["min_color_temp_kelvin"] as? Number)?.toInt() ?: continue else 0
        val high = if (kelvin) (attributes["max_color_temp_kelvin"] as? Number)?.toInt() ?: continue else 100
        if (high <= low) continue
        val attr = when (option.action) {
            "position" -> "current_position"
            "tilt" -> "current_tilt_position"
            else -> option.field
        }
        val reported = (attributes[attr] as? Number)?.toInt()
        var selection by remember(entity.entityId, option.action, reported) { mutableStateOf<Int?>(null) }
        Text((if (bg) option.bg else option.en) + " · " + (selection ?: reported ?: "—"))
        Slider(
            value = (selection ?: reported ?: low).coerceIn(low, high).toFloat(),
            valueRange = low.toFloat()..high.toFloat(),
            onValueChange = { selection = it.roundToInt() },
            onValueChangeFinished = { selection?.let { send(option.service, mapOf(option.field to it)) } },
        )
    }
    for (option in choices.filter { it.action in actions }) {
        val values = when (option.action) {
            "direction" -> listOf("forward", "reverse")
            "oscillate" -> listOf("on", "off")
            else -> {
                val attr = when (option.action) {
                    "effect" -> "effect_list"
                    "source" -> "source_list"
                    else -> option.field + "s"
                }
                (attributes[attr] as? List<*>)?.filterIsInstance<String>().orEmpty()
            }
        }
        if (values.isEmpty()) continue
        var show by remember(entity.entityId, option.action) { mutableStateOf(false) }
        androidx.compose.foundation.layout.Box {
            OutlinedButton(onClick = { show = true }) { Text(if (bg) option.bg else option.en) }
            DropdownMenu(expanded = show, onDismissRequest = { show = false }) {
                for (value in values) {
                    DropdownMenuItem(text = { Text(value) }, onClick = {
                        show = false
                        send(option.service, mapOf(option.field to if (option.action == "oscillate") (value == "on") else value))
                    })
                }
            }
        }
    }
}
