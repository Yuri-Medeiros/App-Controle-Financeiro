package br.com.yurimedeiros.meufinanceiro.ui

import android.view.WindowManager
import androidx.compose.material3.AlertDialog as MaterialAlertDialog
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog as ComposeDialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider

/** Blur only the windows behind this modal; its own content stays sharp. */
@Composable
private fun DialogBackgroundBlur() {
    val view = LocalView.current
    val radius = with(LocalDensity.current) { 8.dp.roundToPx() }
    DisposableEffect(view, radius) {
        val window = (view.parent as? DialogWindowProvider)?.window
        val previousRadius = window?.attributes?.blurBehindRadius ?: 0
        val previouslyEnabled = ((window?.attributes?.flags ?: 0) and WindowManager.LayoutParams.FLAG_BLUR_BEHIND) != 0
        if (window != null) {
            window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
            window.attributes = window.attributes.apply { blurBehindRadius = radius }
        }
        onDispose {
            if (window != null) {
                window.attributes = window.attributes.apply { blurBehindRadius = previousRadius }
                if (!previouslyEnabled) window.clearFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
            }
        }
    }
}

@Composable
internal fun AppDialog(
    onDismissRequest: () -> Unit,
    properties: DialogProperties = DialogProperties(),
    content: @Composable () -> Unit,
) {
    ComposeDialog(onDismissRequest = onDismissRequest, properties = properties) {
        DialogBackgroundBlur()
        content()
    }
}

@Composable
internal fun AppAlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null,
    shape: Shape = AlertDialogDefaults.shape,
    containerColor: Color = AlertDialogDefaults.containerColor,
    tonalElevation: Dp = AlertDialogDefaults.TonalElevation,
    properties: DialogProperties = DialogProperties(),
) {
    MaterialAlertDialog(
        onDismissRequest = onDismissRequest, confirmButton = confirmButton, modifier = modifier,
        dismissButton = dismissButton, title = title, text = {
            DialogBackgroundBlur()
            text?.invoke()
        }, shape = shape, containerColor = containerColor, tonalElevation = tonalElevation, properties = properties,
    )
}
