package br.com.yurimedeiros.meufinanceiro.ui

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalAccessibilityManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.dismiss
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
internal fun ImportNotificationPopup(
    notification: ImportNotification,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accessibilityManager = LocalAccessibilityManager.current
    val dismiss by rememberUpdatedState(onDismiss)
    val scope = rememberCoroutineScope()
    var offset by remember(notification.id) { mutableStateOf(Offset.Zero) }
    var width by remember(notification.id) { mutableFloatStateOf(0f) }
    var dragging by remember(notification.id) { mutableStateOf(false) }
    var returnAnimation by remember { mutableStateOf<Job?>(null) }
    val minimum = with(LocalDensity.current) { 48.dp.toPx() }
    LaunchedEffect(notification.id, dragging) {
        if (!dragging) {
            val duration = accessibilityManager?.calculateRecommendedTimeoutMillis(
                originalTimeoutMillis = 4000L, containsIcons = true, containsText = true, containsControls = false,
            ) ?: 4000L
            delay(duration)
            dismiss()
        }
    }
    fun returnToPosition() {
        returnAnimation?.cancel()
        returnAnimation = scope.launch {
            val start = offset
            animate(0f, 1f, animationSpec = tween(160)) { progress, _ -> offset = start * (1f - progress) }
        }
    }
    val accent = when {
        notification.failed -> Color(0xFFFF809D)
        notification.hasWarnings -> Color(0xFFFFD27A)
        else -> NeonPalette[0]
    }
    Surface(
        modifier = modifier.fillMaxWidth().offset { IntOffset(offset.x.roundToInt(), offset.y.roundToInt()) }
            .onSizeChanged { width = it.width.toFloat() }
            .semantics {
                liveRegion = LiveRegionMode.Polite
                dismiss { dismiss(); true }
            }
            .pointerInput(notification.id, minimum) {
                detectDragGestures(
                    onDragStart = { returnAnimation?.cancel(); dragging = true },
                    onDrag = { change, delta ->
                        change.consume()
                        offset = Offset(offset.x + delta.x, (offset.y + delta.y).coerceAtMost(0f))
                    },
                    onDragEnd = {
                        dragging = false
                        if (ImportNotificationSwipe.shouldDismiss(offset.x, offset.y, width, minimum)) dismiss()
                        else returnToPosition()
                    },
                    onDragCancel = { dragging = false; returnToPosition() },
                )
            },
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
                .padding(horizontal = 16.dp, vertical = 12.dp),
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
            Column(Modifier.weight(1f).padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(notification.title, color = accent, style = MaterialTheme.typography.titleSmall)
                Text(notification.message, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
