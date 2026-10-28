package br.com.yurimedeiros.meufinanceiro.ui

import android.app.Application
import br.com.yurimedeiros.meufinanceiro.gmail.GmailReader
import br.com.yurimedeiros.meufinanceiro.importing.*
import java.time.LocalDate
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
    val movements: List<ReviewMovement> = emptyList(),
    val statements: List<StatementReview> = emptyList(),
    val syncing: Boolean = false,
    val notice: String? = null,
)

internal class FinanceViewModel(application: Application) : AndroidViewModel(application) {
    private val mutableState = MutableStateFlow(FinanceUiState())
    val state = mutableState.asStateFlow()
    private val timeout = SessionTimeout()
    private var expirationJob: Job? = null
    private var session: SecureDatabaseSession? = null
    @Volatile private var generation = 0L
    private val databaseGuard = Any()

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
                refresh()
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

    private fun refresh(){
        val attempt = generation
        viewModelScope.launch {
            val rows = withContext(Dispatchers.IO) { synchronized(databaseGuard) {
                session?.let { MovementRepository(it.database).all() }.orEmpty()
            } }
            if (attempt == generation) mutableState.value = mutableState.value.copy(movements = rows)
        }
    }

    fun syncGmail(token: String) {
        if (!state.value.unlocked || state.value.syncing || state.value.statements.isNotEmpty()) return
        val attempt = generation
        mutableState.value = mutableState.value.copy(syncing = true, error = null, notice = null)
        viewModelScope.launch {
            val reviews = mutableListOf<StatementReview>()
            try {
                val skipped = withContext(Dispatchers.IO) {
                    GmailReader(token).read { document ->
                        check(attempt == generation) { "Sessão encerrada. Atualize após desbloquear." }
                        synchronized(databaseGuard) {
                            check(attempt == generation)
                            check(reviews.size < 20) { "Confirme as referências das faturas e atualize novamente para continuar." }
                            MovementRepository(checkNotNull(session).database).import(document)?.let(reviews::add)
                        }
                    }
                }
                if (attempt == generation) mutableState.value = mutableState.value.copy(notice = "Consulta concluída. $skipped mensagens/anexos fora dos formatos suportados. Revise os registros antes de confirmar.")
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) {
                if (attempt == generation) mutableState.value = mutableState.value.copy(error = "A consulta não foi concluída. Os documentos já processados foram preservados. Verifique a conexão e a autorização Google e tente Atualizar novamente.")
            } finally {
                if (attempt == generation) {
                    mutableState.value = mutableState.value.copy(syncing = false, statements = reviews.toList())
                    refresh()
                }
            }
        }
    }

    fun confirmStatement(reference: String) {
        val review = state.value.statements.firstOrNull() ?: return
        val date = runCatching { LocalDate.parse(reference) }.getOrNull()
        if (date == null) { showError("Informe a referência como AAAA-MM-DD."); return }
        val attempt = generation
        if (state.value.syncing) return
        mutableState.value = mutableState.value.copy(syncing = true, error = null)
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { synchronized(databaseGuard) {
                    check(attempt == generation)
                    MovementRepository(checkNotNull(session).database).import(review.source, date)
                } }
                if (attempt == generation) mutableState.value = mutableState.value.copy(statements = state.value.statements.drop(1))
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { if (attempt == generation) showError("Não foi possível confirmar esta fatura. Os dados foram preservados.") }
            finally { if (attempt == generation) { mutableState.value = mutableState.value.copy(syncing = false); refresh()} }
        }
    }

    fun confirmMovement(ids: Set<Long>, category: String, friendlyName: String) =
        confirmBatch(ids.associateWith { category to friendlyName })

    fun confirmBatch(choices: Map<Long, Pair<String, String>>) {
        if (!state.value.unlocked || state.value.syncing) return
        val attempt = generation
        mutableState.value = mutableState.value.copy(syncing = true, error = null)
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { synchronized(databaseGuard) {
                    check(attempt == generation)
                    MovementRepository(checkNotNull(session).database).confirmBatch(choices)
                } }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { if (attempt == generation) showError("Escolha uma categoria. Conflitos e pagamentos/estornos ainda não podem ser confirmados nesta etapa.") }
            finally { if (attempt == generation) { mutableState.value = mutableState.value.copy(syncing = false); refresh()} }
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
        synchronized(databaseGuard) { previous?.close() }
    }

    override fun onCleared() {
        // No independent coroutine survives the ViewModel to retain the database secret.
        synchronized(databaseGuard) { session?.close(); session = null }
        super.onCleared()
    }
}
