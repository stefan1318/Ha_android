package io.homeassistant.companion.android.stevora

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** STEVORA UI 1: shared tokens for native home, rooms and climate screens. */
internal object StevoraDesign {
    val background = Color(0xFF08131F)
    val surface = Color(0xFF122437)
    val surfaceRaised = Color(0xFF193147)
    val accent = Color(0xFF27B9F5)
    val foreground = Color(0xFFF4FAFF)
    val muted = Color(0xFFA6BCCD)
    val border = Color(0xFF29455A)
    val success = Color(0xFF4CE0B3)
    val gradient = Brush.verticalGradient(listOf(Color(0xFF132945), background, Color(0xFF050C19)))
    val cardShape = RoundedCornerShape(20.dp)
}

@Composable
internal fun StevoraSectionHeading(title: String, subtitle: String? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, color = StevoraDesign.foreground, style = MaterialTheme.typography.titleMedium)
        if (subtitle != null) Text(subtitle, color = StevoraDesign.muted, fontSize = 12.sp)
    }
}

@Composable
internal fun StevoraMetricCard(
    label: String,
    value: String,
    detail: String,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        shape = StevoraDesign.cardShape,
        border = BorderStroke(1.dp, StevoraDesign.border),
        colors = CardDefaults.cardColors(containerColor = StevoraDesign.surface),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(label.uppercase(), color = StevoraDesign.muted, fontSize = 11.sp, letterSpacing = 1.sp)
            Text(value, color = StevoraDesign.foreground, fontSize = 25.sp)
            Text(detail, color = StevoraDesign.accent, fontSize = 11.sp)
        }
    }
}
