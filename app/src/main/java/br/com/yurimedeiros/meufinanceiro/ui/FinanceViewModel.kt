package br.com.yurimedeiros.meufinanceiro.ui

import br.com.yurimedeiros.meufinanceiro.ledger.*
import br.com.yurimedeiros.meufinanceiro.backup.*
import android.app.Application
import br.com.yurimedeiros.meufinanceiro.gmail.GmailReader
import br.com.yurimedeiros.meufinanceiro.importing.*
import java.time.LocalDate
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import br.com.yurimedeiros.meufinanceiro.data.SecureDatabaseSession
import br.com.yurimedeiros.meufinanceiro.data.Person
import br.com.yurimedeiros.meufinanceiro.people.PeopleRepository
import br.com.yurimedeiros.meufinanceiro.people.PersonNames
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
    val people: List<Person> = emptyList(),
    val ledger: LedgerSnapshot = LedgerSnapshot(),
    val backupFiles: List<DriveBackupFile> = emptyList(),
    val backupWorking: Boolean = false,
    val backupEnabled: Boolean = false,
    val backupNeedsConfirmation: Boolean = false,
    val backupMessage: String? = null,
    val backupPreview: BackupPreview? = null,
    val statements: List<StatementReview> = emptyList(),
    val syncing: Boolean = false,
    val importNotification: ImportNotification? = null,
)

internal class FinanceViewModel(application: Application) : AndroidViewModel(application) {
    private val mutableState = MutableStateFlow(FinanceUiState())
    val state = mutableState.asStateFlow()
    private val timeout = SessionTimeout()
    private var expirationJob: Job? = null
    private var session: SecureDatabaseSession? = null
    @Volatile private var generation = 0L
    private val databaseGuard = Any()
    private var notificationId = 0L
    private var recoveryPassword: CharArray? = null
    private var driveToken: String? = null
    private var autoBackup = false
    private var backupJob: Job? = null
    private var pendingRestore: ByteArray? = null
    private var pendingRestoreFile: String? = null
    private var revision = 0L
    private var backedUpRevision = -1L

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

    private fun refresh() {
        val attempt = generation
        viewModelScope.launch {
            try {
                val snapshot = withContext(Dispatchers.IO) { synchronized(databaseGuard) {
                    session?.let {
                        check(attempt == generation)
                        val repo = LedgerRepository(it.database)
                        val ledger = repo.snapshot()
                        val additions = br.com.yurimedeiros.meufinanceiro.planning.PlanningRules.pendingReserve(ledger.planning, LocalDate.now())
                        if (additions.isNotEmpty()) repo.append(LedgerAction.Planning(ledger.planning.copy(reserves = ledger.planning.reserves + additions)))
                        Triple(MovementRepository(it.database).all(), PeopleRepository(it.database).all(), repo.snapshot())
                    } ?: Triple(emptyList<ReviewMovement>(), emptyList<Person>(), LedgerSnapshot())
                } }
                if (attempt == generation) {
                    mutableState.value = mutableState.value.copy(movements = snapshot.first, people = snapshot.second, ledger = snapshot.third)
                    revision++
                    scheduleBackup()
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { if (attempt == generation) showError("Não foi possível ler as movimentações. Seus arquivos foram preservados.") }
        }
    }

    fun syncGmail(token: String) {
        if (!state.value.unlocked || state.value.syncing || state.value.statements.isNotEmpty()) return
        val attempt = generation
        mutableState.value = mutableState.value.copy(syncing = true, error = null, importNotification = null)
        viewModelScope.launch {
            val reviews = mutableListOf<StatementReview>()
            var importedCount = 0
            var skipped = 0
            var failed = false
            var cancelled = false
            try {
                skipped = withContext(Dispatchers.IO) {
                    GmailReader(token) { attempt == generation }.read(shouldRead = { source ->
                        synchronized(databaseGuard) {
                            check(attempt == generation)
                            checkNotNull(session).database.movements().document(source) == null
                        }
                    }) { document ->
                        check(attempt == generation) { "Sessão encerrada. Atualize após desbloquear." }
                        synchronized(databaseGuard) {
                            check(attempt == generation)
                            check(reviews.size < 20) { "Confirme as referências das faturas e atualize novamente para continuar." }
                            val result = MovementRepository(checkNotNull(session).database).importWithCount(document)
                            importedCount += result.importedCount
                            result.review?.let(reviews::add)
                        }
                    }
                }

            } catch (error: CancellationException) { cancelled = true; throw error }
            catch (_: Exception) {
                failed = true
            } finally {
                if (attempt == generation) {
                    mutableState.value = mutableState.value.copy(
                        syncing = false,
                        statements = reviews.toList(),
                        importNotification = if (cancelled) null else ImportNotification(
                            id = ++notificationId,
                            failed = failed,
                            importedCount = importedCount,
                            skippedDocuments = skipped,
                            pendingStatements = reviews.size,
                        ),
                    )
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
        mutableState.value = mutableState.value.copy(syncing = true, error = null, importNotification = null)
        viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.IO) { synchronized(databaseGuard) {
                    check(attempt == generation)
                    MovementRepository(checkNotNull(session).database).importWithCount(review.source, date)
                } }
                if (attempt == generation) mutableState.value = mutableState.value.copy(
                    statements = state.value.statements.drop(1),
                    importNotification = ImportNotification(
                        id = ++notificationId,
                        failed = false,
                        importedCount = result.importedCount,
                        pendingStatements = state.value.statements.size - 1,
                    ),
                )
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) {
                if (attempt == generation) mutableState.value = mutableState.value.copy(
                    importNotification = ImportNotification(
                        id = ++notificationId, failed = true, importedCount = 0,
                        pendingStatements = state.value.statements.size,
                    ),
                )
            }
            finally { if (attempt == generation) { mutableState.value = mutableState.value.copy(syncing = false); refresh() } }
        }
    }

    fun addPerson(name: String, onAdded: () -> Unit) {
        if (!state.value.unlocked || state.value.syncing) return
        val display = PersonNames.display(name)
        if (display.isBlank() || display.length > 120) {
            showError("Informe um nome de até 120 caracteres.")
            return
        }
        val attempt = generation
        mutableState.value = mutableState.value.copy(syncing = true, error = null)
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { synchronized(databaseGuard) {
                    check(attempt == generation)
                    PeopleRepository(checkNotNull(session).database).add(display)
                } }
                if (attempt == generation) onAdded()
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: IllegalArgumentException) {
                if (attempt == generation) showError("Uma pessoa com esse nome já está cadastrada.")
            } catch (_: Exception) {
                if (attempt == generation) showError("Não foi possível cadastrar a pessoa. Tente novamente.")
            } finally {
                if (attempt == generation) {
                    mutableState.value = mutableState.value.copy(syncing = false)
                    refresh()
                }
            }
        }
    }

    fun saveFriendlyName(id: Long, description: String) {
        if (!state.value.unlocked || state.value.syncing) return
        if (description.isBlank() || description.trim().length > 120) {
            showError("Informe uma descrição de até 120 caracteres.")
            return
        }
        val attempt = generation
        mutableState.value = mutableState.value.copy(syncing = true, error = null)
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { synchronized(databaseGuard) {
                    check(attempt == generation)
                    MovementRepository(checkNotNull(session).database).saveFriendlyName(id, description)
                } }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) {
                if (attempt == generation) showError("Não foi possível salvar a descrição amigável. Tente novamente.")
            } finally {
                if (attempt == generation) {
                    mutableState.value = mutableState.value.copy(syncing = false)
                    refresh()
                }
            }
        }
    }

    fun confirmMovement(ids: Set<Long>, category: String, friendlyName: String) =
        confirmBatch(ids.associateWith { category to friendlyName })

    fun confirmBatch(choices: Map<Long, Pair<String, String>>, onConfirmed: () -> Unit = {}) {
        if (!state.value.unlocked || state.value.syncing) return
        val attempt = generation
        mutableState.value = mutableState.value.copy(syncing = true, error = null)
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { synchronized(databaseGuard) {
                    check(attempt == generation)
                    MovementRepository(checkNotNull(session).database).confirmBatch(choices)
                } }
                if (attempt == generation) onConfirmed()
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { if (attempt == generation) showError("Escolha uma categoria. Conflitos e pagamentos/estornos ainda não podem ser confirmados nesta etapa.") }
            finally { if (attempt == generation) { mutableState.value = mutableState.value.copy(syncing = false); refresh() } }
        }
    }

    fun saveLedger(action: LedgerAction, onSaved: () -> Unit) {
        if (!state.value.unlocked || state.value.syncing) return
        val attempt = generation
        mutableState.value = mutableState.value.copy(syncing = true, error = null)
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { synchronized(databaseGuard) {
                    check(attempt == generation)
                    LedgerRepository(checkNotNull(session).database).append(action)
                } }
                if (attempt == generation) onSaved()
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: IllegalArgumentException) {
                if (attempt == generation) showError(error.message ?: "Revise os valores e vínculos antes de confirmar.")
            } catch (_: Exception) {
                if (attempt == generation) showError("Não foi possível salvar. O histórico anterior foi preservado.")
            } finally {
                if (attempt == generation) { mutableState.value = mutableState.value.copy(syncing = false); refresh() }
            }
        }
    }

    fun prepareBackup(password: String, confirmation: String, automatic: Boolean): Boolean {
        if (!state.value.unlocked || state.value.backupWorking) return false
        if (password.length < 12 || password != confirmation) {
            backupAuthorizationFailed("Use uma senha de recuperação de pelo menos 12 caracteres e confirme-a.")
            return false
        }
        recoveryPassword?.fill('\u0000')
        recoveryPassword = password.toCharArray()
        autoBackup = automatic
        mutableState.value = mutableState.value.copy(backupEnabled = false, backupNeedsConfirmation = false, backupMessage = null)
        return true
    }

    fun backupAuthorizationFailed(message: String) {
        mutableState.value = mutableState.value.copy(backupMessage = message)
    }

    fun connectDrive(token: String) {
        if (!state.value.unlocked) return
        driveToken = token
        mutableState.value = mutableState.value.copy(backupEnabled = false)
        loadBackups()
    }

    fun loadBackups() {
        val token = driveToken ?: return
        if (state.value.backupWorking || !state.value.unlocked) return
        val password = recoveryPassword?.copyOf()
        val attempt = generation
        mutableState.value = mutableState.value.copy(backupWorking = true, backupMessage = "Consultando backups…")
        viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                val files = withContext(Dispatchers.IO) {
                    val drive = DriveBackups(token) { attempt == generation && state.value.visible }
                    val files = drive.list()
                    if (autoBackup && files.isNotEmpty()) {
                        val plain = BackupCipher.decrypt(drive.read(files.first()), checkNotNull(password))
                        try { BackupPayload.preview(plain) } finally { plain.fill(0) }
                    }
                    files
                }
                if (attempt == generation) {
                    val emptyLocal = state.value.movements.isEmpty() && state.value.ledger.actions.isEmpty() && state.value.people.size <= 11
                    val needsConfirmation = autoBackup && files.isNotEmpty() && emptyLocal
                    mutableState.value = mutableState.value.copy(backupFiles = files,
                        backupEnabled = autoBackup && !needsConfirmation,
                        backupNeedsConfirmation = needsConfirmation,
                        backupMessage = if (needsConfirmation) "Há backups anteriores. Restaure-os ou confirme explicitamente o início com os dados locais." else "${files.size} backup(s) disponível(is).")
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { if (attempt == generation) {
                driveToken = null
                mutableState.value = mutableState.value.copy(backupEnabled = false)
                backupAuthorizationFailed("Não foi possível verificar os backups. Confira a senha e a conexão e autorize o Drive novamente. Nenhum backup anterior foi alterado.")
            } }
            finally { password?.fill('\u0000'); if (attempt == generation) { mutableState.value = mutableState.value.copy(backupWorking = false); scheduleBackup() } }
        }
    }

    fun confirmNewBackup() {
        if (!state.value.backupNeedsConfirmation || driveToken == null || recoveryPassword == null) return
        mutableState.value = mutableState.value.copy(backupEnabled = true, backupNeedsConfirmation = false)
        backupNow()
    }

    private fun scheduleBackup() {
        backupJob?.cancel()
        if (!autoBackup || !state.value.backupEnabled || state.value.backupNeedsConfirmation || driveToken == null || recoveryPassword == null || !state.value.visible || !state.value.unlocked || revision <= backedUpRevision) return
        backupJob = viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) { delay(30_000); backupNow() }
    }

    fun backupNow() {
        val token = driveToken ?: run { backupAuthorizationFailed("Autorize o Drive para salvar o backup."); return }
        if (!state.value.backupEnabled || state.value.backupNeedsConfirmation) return
        if (state.value.backupWorking || !state.value.unlocked || !state.value.visible) { scheduleBackup(); return }
        val password = recoveryPassword?.copyOf() ?: return
        val attempt = generation; val savingRevision = revision
        val protectedFile = pendingRestoreFile
        mutableState.value = mutableState.value.copy(backupWorking = true, backupMessage = "Salvando backup cifrado…")
        viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                val files = withContext(Dispatchers.IO) {
                    val plain = synchronized(databaseGuard) { check(attempt == generation); BackupPayload.export(checkNotNull(session).database) }
                    val encrypted = try { BackupCipher.encrypt(plain, password) } finally { plain.fill(0) }
                    DriveBackups(token) { attempt == generation && state.value.visible }.save(encrypted, setOfNotNull(protectedFile))
                }
                if (attempt == generation) {
                    backedUpRevision = savingRevision
                    mutableState.value = mutableState.value.copy(backupFiles = files, backupMessage = "Backup salvo e verificado no Drive.")
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { if (attempt == generation) { driveToken = null; mutableState.value = mutableState.value.copy(backupEnabled = false); backupAuthorizationFailed("Backup pendente. Os anteriores foram preservados; confira a conexão e autorize novamente.") } }
            finally { password.fill('\u0000'); if (attempt == generation) { mutableState.value = mutableState.value.copy(backupWorking = false); scheduleBackup() } }
        }
    }

    fun previewBackup(file: DriveBackupFile) {
        val token = driveToken ?: return
        if (state.value.backupWorking || state.value.syncing || !state.value.unlocked) return
        val password = recoveryPassword?.copyOf() ?: return
        cancelRestore()
        val attempt = generation
        mutableState.value = mutableState.value.copy(backupWorking = true, backupMessage = "Verificando backup…")
        viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            var plain: ByteArray? = null
            try {
                val preview = withContext(Dispatchers.IO) {
                    val encrypted = DriveBackups(token) { attempt == generation && state.value.visible }.read(file)
                    plain = BackupCipher.decrypt(encrypted, password)
                    BackupPayload.preview(checkNotNull(plain))
                }
                if (attempt == generation) {
                    pendingRestore = plain; plain = null; pendingRestoreFile = file.id
                    mutableState.value = mutableState.value.copy(backupPreview = preview, backupMessage = null)
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { if (attempt == generation) backupAuthorizationFailed("Não foi possível verificar. Confira a senha de recuperação e a conexão; seus dados locais foram preservados.") }
            finally { plain?.fill(0); password.fill('\u0000'); if (attempt == generation) mutableState.value = mutableState.value.copy(backupWorking = false) }
        }
    }

    fun cancelRestore() {
        pendingRestore?.fill(0); pendingRestore = null; pendingRestoreFile = null
        mutableState.value = mutableState.value.copy(backupPreview = null)
    }

    fun confirmRestore() {
        val token = driveToken ?: return
        if (state.value.syncing || state.value.backupWorking || !state.value.unlocked) return
        val plain = pendingRestore?.copyOf() ?: return
        val password = recoveryPassword?.copyOf() ?: run { plain.fill(0); return }
        val protectedFile = pendingRestoreFile
        val attempt = generation
        mutableState.value = mutableState.value.copy(syncing = true, backupWorking = true, backupMessage = "Preservando dados atuais e restaurando…")
        viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                val createdAt = withContext(Dispatchers.IO) {
                    val current = synchronized(databaseGuard) { check(attempt == generation); BackupPayload.export(checkNotNull(session).database) }
                    val encrypted = try { BackupCipher.encrypt(current, password) } finally { current.fill(0) }
                    // A verified backup of the current state is required before replacement.
                    DriveBackups(token) { attempt == generation && state.value.visible }.save(encrypted, setOfNotNull(protectedFile))
                    synchronized(databaseGuard) {
                        check(attempt == generation && state.value.visible)
                        BackupPayload.restore(checkNotNull(session).database, plain)
                        checkNotNull(session).database.localState().read().createdAtMillis
                    }
                }
                if (attempt == generation) {
                    cancelRestore()
                    mutableState.value = mutableState.value.copy(createdAtMillis = createdAt, statements = emptyList(), error = null, backupMessage = "Dados restaurados. O estado anterior também foi salvo no Drive.")
                    refresh()
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { if (attempt == generation) backupAuthorizationFailed("Não foi possível restaurar. Seus dados locais anteriores foram preservados.") }
            finally { plain.fill(0); password.fill('\u0000'); if (attempt == generation) mutableState.value = mutableState.value.copy(syncing = false, backupWorking = false) }
        }
    }

    private fun clearBackupSecrets() {
        backupJob?.cancel(); backupJob = null
        recoveryPassword?.fill('\u0000'); recoveryPassword = null; driveToken = null; autoBackup = false
        pendingRestore?.fill(0); pendingRestore = null; pendingRestoreFile = null
    }

    fun showImportError(message: String) {
        if (!state.value.unlocked) return
        mutableState.value = mutableState.value.copy(
            error = null,
            importNotification = ImportNotification(
                id = ++notificationId, failed = true, importedCount = 0,
                failureMessage = message,
            ),
        )
    }

    fun dismissImportNotification(id: Long) {
        if (mutableState.value.importNotification?.id == id) {
            mutableState.value = mutableState.value.copy(importNotification = null)
        }
    }

    fun showError(message: String) {
        mutableState.value = mutableState.value.copy(error = message)
    }

    fun onBackground(now: Long = SystemClock.elapsedRealtime()) {
        mutableState.value = mutableState.value.copy(visible = false)
        timeout.background(now)
        backupJob?.cancel()
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
        scheduleBackup()
    }

    fun lock() {
        generation++
        clearBackupSecrets()
        expirationJob?.cancel()
        val previous = session
        session = null
        mutableState.value = FinanceUiState(visible = mutableState.value.visible)
        // Close now: a canceled ViewModel scope must not drop a queued cleanup.
        // Serialize closure with any document transaction already in progress.
        synchronized(databaseGuard) { previous?.close() }
    }

    override fun onCleared() {
        generation++
        clearBackupSecrets()
        // No independent coroutine survives the ViewModel to retain the database secret.
        synchronized(databaseGuard) { session?.close(); session = null }
        super.onCleared()
    }
}
