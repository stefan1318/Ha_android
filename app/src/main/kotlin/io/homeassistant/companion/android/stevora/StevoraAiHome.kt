package io.homeassistant.companion.android.stevora

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.homeassistant.companion.android.R

/**
 * Native AI entry screen. Actual microphone permissions, pipeline choice, text input,
 * and server conversation are handled by the existing authenticated AssistActivity.
 * This screen deliberately does not create a second unauthenticated AI client.
 */
@Composable
internal fun StevoraAiHome(
    onStartConversation: () -> Unit,
    onConfigurePipeline: () -> Unit,
    onBack: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().background(Color(0xFF08131F)).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Spacer(Modifier.height(20.dp))
        Text("STEVORA AI", color = Color(0xFFF4FAFF), fontSize = 32.sp)
        Text(stringResource(R.string.stevora_ai_intro), color = Color(0xFFA6BCCD))
        Button(
            onClick = onStartConversation,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF27B9F5), contentColor = Color(0xFF08131F)),
        ) {
            Text(stringResource(R.string.stevora_ai_start))
        }
        OutlinedButton(onClick = onConfigurePipeline) {
            Text(stringResource(R.string.stevora_ai_pipeline))
        }
        OutlinedButton(onClick = onBack) {
            Text(stringResource(R.string.stevora_nav_home))
        }
        Text(stringResource(R.string.stevora_ai_note), color = Color(0xFFA6BCCD), fontSize = 12.sp)
    }
}
