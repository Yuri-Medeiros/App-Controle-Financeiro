package br.com.yurimedeiros.meufinanceiro.ui

import androidx.compose.runtime.AbstractApplier
import androidx.compose.runtime.Composition
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.LocalSaveableStateRegistry
import androidx.compose.runtime.saveable.SaveableStateHolder
import androidx.compose.runtime.saveable.SaveableStateRegistry
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import kotlin.coroutines.EmptyCoroutineContext

internal object SessionDraftsCheck {
    // Runs on the Android main thread: exercises actual Compose disposal/restoration.
    // No Activity, saved-instance-state Bundle, disk or user database is involved.
    private class NoUiApplier : AbstractApplier<Unit>(Unit) {
        override fun insertTopDown(index: Int, instance: Unit) = Unit
        override fun insertBottomUp(index: Int, instance: Unit) = Unit
        override fun remove(index: Int, count: Int) = Unit
        override fun move(from: Int, to: Int, count: Int) = Unit
        override fun onClear() = Unit
    }

    fun verify() {
        val recomposer = Recomposer(EmptyCoroutineContext)
        val composition = Composition(NoUiApplier(), recomposer)
        val registry = SaveableStateRegistry(null) { true }
        // Android Dialogs override the ambient registry. Drafts must ignore that registry.
        val dialogRegistry = SaveableStateRegistry(null) { false }
        var holder: SaveableStateHolder? = null
        var field: MutableState<String>? = null
        var allocations: MutableMap<Long, String>? = null
        fun render(visible: Boolean) {
            composition.setContent {
                CompositionLocalProvider(LocalSaveableStateRegistry provides registry) {
                    val fresh = rememberSaveableStateHolder()
                    if (holder == null) holder = fresh
                    if (visible) holder!!.SaveableStateProvider(FINANCE_DRAFT_KEY) {
                        SessionDraftScope {
                            CompositionLocalProvider(LocalSaveableStateRegistry provides dialogRegistry) {
                                field = rememberDraft { mutableStateOf("") }
                                allocations = rememberDraft { mutableStateMapOf<Long, String>() }
                            }
                        }
                    }
                }
            }
        }
        try {
            render(true)
            check(dialogRegistry.performSave().isEmpty())
            field!!.value = "Compra compartilhada"
            allocations!![7L] = "123,45"
            render(false)
            render(true)
            check(field!!.value == "Compra compartilhada")
            check(allocations!![7L] == "123,45")
            render(false)
            SessionDrafts(holder!!).clear()
            render(true)
            check(field!!.value.isEmpty())
            check(allocations!!.isEmpty())
            // Manual lock/expiration can happen before Compose removes the visible provider.
            field!!.value = "Rascunho que deve ser descartado"
            SessionDrafts(holder!!).clear()
            render(false)
            render(true)
            check(field!!.value.isEmpty())
        } finally {
            composition.dispose()
            recomposer.cancel()
        }
    }
}
