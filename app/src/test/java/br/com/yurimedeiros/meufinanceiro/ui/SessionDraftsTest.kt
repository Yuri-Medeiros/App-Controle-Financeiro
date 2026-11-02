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
import org.junit.Assert.*
import org.junit.Test
import kotlin.coroutines.EmptyCoroutineContext

class SessionDraftsTest {
    // Exercise actual Compose save/dispose/restore, without an Activity, Bundle or disk.
    private class NoUiApplier : AbstractApplier<Unit>(Unit) {
        override fun insertTopDown(index: Int, instance: Unit) = Unit
        override fun insertBottomUp(index: Int, instance: Unit) = Unit
        override fun remove(index: Int, count: Int) = Unit
        override fun move(from: Int, to: Int, count: Int) = Unit
        override fun onClear() = Unit
    }

    @Test fun filledFormAndAllocationsSurviveRemovalForAuthentication() {
        val recomposer = Recomposer(EmptyCoroutineContext)
        val composition = Composition(NoUiApplier(), recomposer)
        val registry = SaveableStateRegistry(null) { true }
        var holder: SaveableStateHolder? = null
        var field: MutableState<String>? = null
        var allocations: MutableMap<Long, String>? = null
        fun render(visible: Boolean) {
            composition.setContent {
                CompositionLocalProvider(LocalSaveableStateRegistry provides registry) {
                    val fresh = rememberSaveableStateHolder()
                    if (holder == null) holder = fresh
                    if (visible) holder!!.SaveableStateProvider(FINANCE_DRAFT_KEY) {
                        field = rememberDraft { mutableStateOf("") }
                        allocations = rememberDraft { mutableStateMapOf<Long, String>() }
                    }
                }
            }
        }
        try {
            render(true)
            field!!.value = "Compra compartilhada"
            allocations!![7L] = "123,45"
            render(false)
            render(true)
            assertEquals("Compra compartilhada", field!!.value)
            assertEquals("123,45", allocations!![7L])
            render(false)
            SessionDrafts(holder!!).clear()
            render(true)
            assertEquals("", field!!.value)
            assertTrue(allocations!!.isEmpty())
        } finally {
            composition.dispose()
            recomposer.cancel()
        }
    }
}
