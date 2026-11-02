package br.com.yurimedeiros.meufinanceiro.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.SaveableStateHolder
import androidx.compose.runtime.saveable.rememberSaveable
import br.com.yurimedeiros.meufinanceiro.importing.StatementReview

internal const val FINANCE_DRAFT_KEY = "finance-draft"

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
internal fun <T : Any> rememberDraft(vararg inputs: Any?, init: () -> T): T =
    rememberSaveable(*inputs, saver = Saver<T, T>(save = { it }, restore = { it }), init = init)
