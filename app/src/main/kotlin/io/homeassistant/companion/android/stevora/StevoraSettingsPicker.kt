package io.homeassistant.companion.android.stevora

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import io.homeassistant.companion.android.common.R

@Composable
internal fun StevoraSettingsPicker(
    title: String,
    options: List<StevoraChoice>,
    selected: String?,
    multiple: Set<String>? = null,
    onSelect: (String) -> Unit,
) {
    var open by rememberSaveable { mutableStateOf(false) }
    val current = options.firstOrNull { it.id == selected }
    OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) {
        Text(when {
            multiple != null -> "$title · ${multiple.size}"
            current != null -> "$title · ${current.name}"
            else -> title
        })
    }
    if (open) {
        var query by rememberSaveable { mutableStateOf("") }
        val matches = remember(options, query) { StevoraChoice.matching(options, query) }
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text(title) },
            text = {
                Column {
                    OutlinedTextField(query, { query = it }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.stevora_catalog_search)) })
                    if (matches.isEmpty()) Text(stringResource(R.string.stevora_catalog_empty), Modifier.padding(12.dp))
                    LazyColumn(Modifier.heightIn(max = 330.dp)) {
                        items(matches, key = { it.id }) { option ->
                            Column(Modifier.fillMaxWidth().clickable(role = Role.Button) {
                                if (multiple == null) open = false
                                onSelect(option.id)
                            }.padding(vertical = 12.dp, horizontal = 4.dp)) {
                                Text(if (multiple?.contains(option.id) == true || option.id == selected) "✓ ${option.name}" else option.name)
                                if (option.context.isNotBlank()) Text(option.context)
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { open = false }) { Text(stringResource(if (multiple == null) android.R.string.cancel else android.R.string.ok)) } },
        )
    }
}
