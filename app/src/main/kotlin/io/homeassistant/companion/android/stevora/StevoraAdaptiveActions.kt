package io.homeassistant.companion.android.stevora

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import org.json.JSONException
import org.json.JSONObject

internal data class StevoraAdaptiveParameter(
    val type: String,
    val options: List<String>,
    val min: Double,
    val max: Double,
) {
    fun parse(value: String?): Any? = when (type) {
        "enum" -> value?.takeIf { it in options }
        "integer" -> value?.toIntOrNull()?.takeIf { it.toDouble() in min..max }
        "number" -> value?.replace(',', '.')?.toDoubleOrNull()?.takeIf { it.isFinite() && it in min..max }
        else -> null
    }
}

internal data class StevoraAdaptiveAction(
    val capabilityId: String,
    val id: String,
    val label: String,
    val confirmationRequired: Boolean,
    val parameters: Map<String, StevoraAdaptiveParameter>,
)

internal fun parseStevoraAdaptiveActions(
    payload: String?,
    installationId: String,
    language: String,
): Map<String, List<StevoraAdaptiveAction>>? {
    if (payload == null) return null
    return try {
        val catalog = JSONObject(payload)
        if (catalog.getInt("schema_version") != 1 || catalog.getString("installation_id") != installationId) return null
        val capabilities = catalog.getJSONArray("capabilities")
        buildMap {
            for (index in 0 until capabilities.length()) {
                val capability = capabilities.getJSONObject(index)
                val actions = capability.getJSONArray("actions")
                val capabilityId = capability.getString("id")
                put(capability.getString("entity_id"), List(actions.length()) { i ->
                    val action = actions.getJSONObject(i)
                    val id = action.getString("id")
                    val parameters = action.getJSONObject("parameters")
                    StevoraAdaptiveAction(
                        capabilityId,
                        id,
                        action.optJSONObject("labels")?.optString(language)?.takeIf(String::isNotBlank) ?: id,
                        action.getBoolean("confirmation_required"),
                        parameters.keys().asSequence().associateWith { name ->
                            val parameter = parameters.getJSONObject(name)
                            val options = parameter.optJSONArray("options")
                            StevoraAdaptiveParameter(
                                parameter.getString("type"),
                                options?.let { List(it.length(), it::getString) }.orEmpty(),
                                parameter.optDouble("min"),
                                parameter.optDouble("max"),
                            )
                        },
                    )
                })
            }
        }
    } catch (_: JSONException) {
        emptyMap()
    }
}

@Composable
internal fun StevoraAdaptiveActionControl(
    action: StevoraAdaptiveAction,
    bg: Boolean,
    enabled: Boolean,
    submit: (Map<String, Any>, Boolean) -> Unit,
) {
    var values by remember(action) { mutableStateOf(emptyMap<String, String>()) }
    var confirming by remember(action) { mutableStateOf(false) }
    val parameters = action.parameters.mapNotNull { (key, field) ->
        field.parse(values[key])?.let { key to it }
    }.toMap()
    val ready = enabled && parameters.size == action.parameters.size
    Column {
        action.parameters.forEach { (key, field) ->
            if (field.type == "enum") {
                field.options.forEach { option ->
                    FilterChip(
                        selected = values[key] == option,
                        onClick = { values = values + (key to option) },
                        label = { Text(option) },
                    )
                }
            } else if (field.type == "number" || field.type == "integer") {
                OutlinedTextField(
                    value = values[key].orEmpty(),
                    onValueChange = { values = values + (key to it) },
                    label = { Text(key.replace('_', ' ')) },
                    singleLine = true,
                )
            }
        }
        OutlinedButton(
            enabled = ready,
            onClick = { if (action.confirmationRequired) confirming = true else submit(parameters, false) },
        ) { Text(action.label) }
    }
    if (confirming) {
        AlertDialog(
            onDismissRequest = { confirming = false },
            title = { Text(action.label) },
            confirmButton = {
                TextButton(enabled = ready, onClick = {
                    confirming = false
                    submit(parameters, true)
                }) { Text(if (bg) "Потвърди" else "Confirm") }
            },
            dismissButton = {
                TextButton(onClick = { confirming = false }) { Text(if (bg) "Отказ" else "Cancel") }
            },
        )
    }
}
