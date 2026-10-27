package br.com.yurimedeiros.meufinanceiro.ui

import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import br.com.yurimedeiros.meufinanceiro.data.SecureDatabaseSession
import br.com.yurimedeiros.meufinanceiro.security.SessionTimeout
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal data class FinanceUiState(
    val unlocked: Boolean = false,
    val opening: Boolean = false,
    val visible: Boolean = true,
    val createdAtMillis: Long? = null,
    val error: String? = null,
)

internal class FinanceViewModel(application: Application) : AndroidViewModel(application) {
    private val mutableState = MutableStateFlow(FinanceUiState())
    val state = mutableState.asStateFlow()
    private val timeout = SessionTimeout()
    private var expirationJob: Job? = null
    private var session: SecureDatabaseSession? = null
    private var generation = 0L

    fun openWith(password: ByteArray) {
        val attempt = ++generation
        mutableState.value = mutableState.value.copy(opening = true, error = null)
        viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            var opened: SecureDatabaseSession? = null
            var transferred = false
            try {
                // Assign inside IO so cancellation on the return boundary cannot leak an open DB.
                withContext(Dispatchers.IO) {
                    opened = SecureDatabaseSession.open(getApplication(), password)
                }
                if (attempt != generation) return@launch
                session?.close()
                session = opened
                opened = null
                transferred = true
                mutableState.value = mutableState.value.copy(
                    unlocked = true,
                    opening = false,
                    createdAtMillis = session!!.createdAtMillis,
                    error = null,
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                if (attempt == generation) {
                    mutableState.value = mutableState.value.copy(
                        opening = false,
                        error = "Não foi possível abrir seus dados com segurança. Seus arquivos foram preservados.",
                    )
                }
            } finally {
                opened?.close()
                if (!transferred) password.fill(0)
            }
        }
    }

    fun showError(message: String) {
        mutableState.value = mutableState.value.copy(error = message)
    }

    fun onBackground(now: Long = SystemClock.elapsedRealtime()) {
        mutableState.value = mutableState.value.copy(visible = false)
        timeout.background(now)
        expirationJob?.cancel()
        expirationJob = viewModelScope.launch {
            delay(timeout.remaining(now))
            lock()
        }
    }

    fun onForeground(now: Long = SystemClock.elapsedRealtime()) {
        expirationJob?.cancel()
        if (timeout.expired(now)) lock()
        timeout.foreground()
        mutableState.value = mutableState.value.copy(visible = true)
    }

    fun lock() {
        generation++
        expirationJob?.cancel()
        val previous = session
        session = null
        mutableState.value = FinanceUiState(visible = mutableState.value.visible)
        // Close now: a canceled ViewModel scope must not drop a queued cleanup.
        // This first version performs no ongoing queries after initialization.
        previous?.close()
    }

    override fun onCleared() {
        // No independent coroutine survives the ViewModel to retain the database secret.
        session?.close()
        session = null
        super.onCleared()
    }
}
