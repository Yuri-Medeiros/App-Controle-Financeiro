package br.com.yurimedeiros.meufinanceiro.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.platform.LocalAccessibilityManager
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

private val CloseNotificationIcon = ImageVector.Builder(
    "Fechar", 24.dp, 24.dp, 24f, 24f,
).addPath(
    PathParser().parsePathString("M6,4L12,10L18,4L20,6L14,12L20,18L18,20L12,14L6,20L4,18L10,12L4,6Z").toNodes(),
    fill = SolidColor(Color.Black),
).build()

@Composable
internal fun ImportNotificationPopup(
    notification: ImportNotification,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accessibilityManager = LocalAccessibilityManager.current
    LaunchedEffect(notification.id) {
        val duration = accessibilityManager?.calculateRecommendedTimeoutMillis(
            originalTimeoutMillis = if (notification.failed || notification.hasWarnings) 10000L else 6000L,
            containsIcons = true,
            containsText = true,
            containsControls = true,
        ) ?: if (notification.failed || notification.hasWarnings) 10000L else 6000L
        delay(duration)
        onDismiss()
    }
    val accent = when {
        notification.failed -> Color(0xFFFF809D)
        notification.hasWarnings -> Color(0xFFFFD27A)
        else -> NeonPalette[0]
    }
    Surface(
        modifier = modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
        shape = RoundedCornerShape(22.dp),
        color = Color(0xFF080F24).copy(alpha = 0.96f),
        border = BorderStroke(1.dp, GlassBorder),
        tonalElevation = 0.dp,
        shadowElevation = 8.dp,
    ) {
        Row(
            Modifier.background(neonGradient(0.18f))
                .background(Brush.verticalGradient(listOf(
                    Color.White.copy(alpha = 0.10f), Color.White.copy(alpha = 0.02f),
                )))
                .padding(start = 16.dp, top = 12.dp, end = 4.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Box(
                Modifier.padding(top = 4.dp).size(36.dp)
                    .background(accent.copy(alpha = 0.16f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(if (notification.failed || notification.hasWarnings) "!" else "✓",
                    color = accent, style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold)
            }
            Column(
                Modifier.weight(1f).padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(notification.title, color = accent, style = MaterialTheme.typography.titleSmall)
                Text(notification.message, style = MaterialTheme.typography.bodySmall)
            }
            IconButton(onClick = onDismiss, modifier = Modifier.size(48.dp)) {
                Icon(CloseNotificationIcon, contentDescription = "Fechar notificação", modifier = Modifier.size(18.dp))
            }
        }
    }
}
