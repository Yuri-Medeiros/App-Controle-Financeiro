package br.com.yurimedeiros.meufinanceiro.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.saveable.LocalSaveableStateRegistry
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.SaveableStateHolder
import androidx.compose.runtime.saveable.SaveableStateRegistry
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import br.com.yurimedeiros.meufinanceiro.importing.StatementReview

internal const val FINANCE_DRAFT_KEY = "finance-draft"
internal val LocalSessionDraftRegistry = staticCompositionLocalOf<SaveableStateRegistry?> { null }

@Composable
internal fun SessionDraftScope(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalSessionDraftRegistry provides LocalSaveableStateRegistry.current, content = content)
}

internal class SessionDrafts(val holder: SaveableStateHolder) {
    var statements: List<StatementReview> = emptyList()

    fun clear() {
        // Also suppress saving if the provider is still being removed from composition.
        holder.removeState(FINANCE_DRAFT_KEY)
        statements = emptyList()
    }
}

/** Identity saving is safe only inside FinanceApp's memory-only registry, never a Bundle. */
@Composable
internal fun <T : Any> rememberDraft(vararg inputs: Any?, init: () -> T): T {
    val registry = LocalSessionDraftRegistry.current ?: return remember(*inputs, calculation = init)
    var result: T? = null
    // Dialogs establish a new Android registry. Explicitly use the process-memory registry.
    CompositionLocalProvider(LocalSaveableStateRegistry provides registry) {
        result = rememberSaveable(*inputs, saver = Saver<T, T>(save = { it }, restore = { it }), init = init)
    }
    return checkNotNull(result)
}

/** Keep dialog fields, scroll position and their internal state out of Android Bundles too. */
@Composable
internal fun SessionAlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null,
    shape: Shape = AlertDialogDefaults.shape,
    containerColor: Color = AlertDialogDefaults.containerColor,
    tonalElevation: Dp = AlertDialogDefaults.TonalElevation,
) {
    val registry = LocalSessionDraftRegistry.current
    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = { DraftDialogContent(registry, confirmButton) },
        modifier = modifier,
        dismissButton = dismissButton?.let { slot -> { DraftDialogContent(registry, slot) } },
        title = title?.let { slot -> { DraftDialogContent(registry, slot) } },
        text = text?.let { slot -> { DraftDialogContent(registry, slot) } },
        shape = shape,
        containerColor = containerColor,
        tonalElevation = tonalElevation,
    )
}

@Composable
private fun DraftDialogContent(registry: SaveableStateRegistry?, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalSaveableStateRegistry provides registry, content = content)
}
