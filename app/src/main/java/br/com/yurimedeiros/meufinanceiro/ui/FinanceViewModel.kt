package br.com.yurimedeiros.meufinanceiro.ui

import br.com.yurimedeiros.meufinanceiro.formatting.AppDates

import br.com.yurimedeiros.meufinanceiro.assets.*
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
    val resumeAvailable: Boolean = false,
    val custody: List<CustodyStatement> = emptyList(),
    val quoting: Boolean = false,
    val quotingVehicles: Boolean = false,
    val vehicleError: String? = null,
    val vehicleNotice: String? = null,
    val bitcoinError: String? = null,
    val bitcoinNotice: String? = null,
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
    private var cacheJob: Job? = null
    private val resumeWindow = br.com.yurimedeiros.meufinanceiro.security.ResumeWindow()
    private var quoteAttemptAt: Long? = null
    private var vehicleAttemptAt: Long? = null
    private var vehicleRateLimitedUntil = 0L
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
                    resumeAvailable = false,
                    opening = false,
                    createdAtMillis = session!!.createdAtMillis,
                    error = null,
                )
                cacheJob?.cancel(); resumeWindow.clear()
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

    fun syncGmail(token: String, investmentsOnly: Boolean = false) {
        if (!state.value.unlocked || state.value.syncing || state.value.statements.isNotEmpty()) return
        val attempt = generation
        mutableState.value = mutableState.value.copy(syncing = true, error = null, importNotification = null)
        viewModelScope.launch {
            val reviews = mutableListOf<StatementReview>()
            val custody = state.value.custody.toMutableList()
            var importedCount = 0
            var skipped = 0
            var failed = false
            var cancelled = false
            try {
                skipped = withContext(Dispatchers.IO) {
                    GmailReader(token) { attempt == generation }.read(investmentsOnly = investmentsOnly, shouldRead = { source ->
                        synchronized(databaseGuard) {
                            check(attempt == generation)
                            checkNotNull(session).database.movements().document(source) == null
                        }
                    }) { document ->
                        check(attempt == generation) { "Sessão encerrada. Atualize após desbloquear." }
                        synchronized(databaseGuard) {
                            check(attempt == generation)
                            check(reviews.size < 20) { "Confirme as referências das faturas e atualize novamente para continuar." }
                            if (document.custody) {
                                val parsed = CustodyPdf.read(document.bytes, getApplication<Application>().cacheDir)
                                val book = LedgerRepository(checkNotNull(session).database).snapshot().assets
                                val actions = try { CustodyStatements.actions(parsed, book) }
                                    catch (_: IllegalArgumentException) { return@read } // An older email must not overwrite newer holdings.
                                if (actions.isNotEmpty() && custody.none { it.date >= parsed.date }) {
                                    // Custody is a complete snapshot: do not offer obsolete snapshots after the newest one.
                                    custody.clear()
                                    custody += parsed
                                }
                                return@read
                            }
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
                        custody = custody.sortedByDescending { it.date },
                        importNotification = if (cancelled) null else ImportNotification(
                            id = ++notificationId,
                            failed = failed,
                            importedCount = importedCount,
                            skippedDocuments = skipped,
                            pendingStatements = reviews.size,
                            investmentsOnly = investmentsOnly,
                            pendingCustody = custody.size,
                        ),
                    )
                    refresh()
                }
            }
        }
    }

    fun dismissCustody() {
        if (state.value.unlocked && !state.value.syncing) mutableState.value = state.value.copy(custody = state.value.custody.drop(1))
    }

    fun confirmCustody() {
        if (!state.value.unlocked || state.value.syncing) return
        val statement = state.value.custody.firstOrNull() ?: return
        val attempt = generation
        mutableState.value = state.value.copy(syncing = true, error = null)
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { synchronized(databaseGuard) {
                    check(attempt == generation)
                    val db = checkNotNull(session).database
                    db.runInTransaction {
                        val repo = LedgerRepository(db)
                        CustodyStatements.actions(statement, repo.snapshot().assets).forEach(repo::append)
                    }
                } }
                if (attempt == generation) mutableState.value = state.value.copy(custody = state.value.custody.drop(1))
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { if (attempt == generation) showError("Não foi possível importar o PDF. Verifique se o extrato é mais recente. Nenhum saldo foi alterado.") }
            finally { if (attempt == generation) { mutableState.value = state.value.copy(syncing = false); refresh() } }
        }
    }

    fun refreshBitcoin(manual: Boolean = false) {
        if (!state.value.unlocked || !state.value.visible || state.value.quoting) return
        val asset = state.value.ledger.assets.assets.firstOrNull { it.kind == AssetKind.BITCOIN && !it.disposed } ?: return
        val now = SystemClock.elapsedRealtime()
        if (!manual && quoteAttemptAt?.let { now - it in 0..59_999L } == true) return
        quoteAttemptAt = now
        val attempt = generation
        mutableState.value = state.value.copy(quoting = true, bitcoinError = null, bitcoinNotice = null)
        viewModelScope.launch {
            try {
                val quote = withContext(Dispatchers.IO) { BitcoinQuotes.fetch() }
                val keptNewer = withContext(Dispatchers.IO) { synchronized(databaseGuard) {
                    check(attempt == generation)
                    val repository = LedgerRepository(checkNotNull(session).database)
                    val current = repository.snapshot().assets.assets.single { it.id == asset.id }
                    val savedAt = current.quotedAt?.let(java.time.Instant::parse)
                    if (savedAt != null && java.time.Instant.parse(quote.timestamp).isBefore(savedAt)) true else {
                        repository.append(LedgerAction.Asset(
                            AssetChange.Quote(asset.id, 0, quote.price, quote.timestamp, quote.source + if (quote.stale) " · referência anterior" else "")))
                        false
                    }
                } }
                if (attempt == generation) {
                    mutableState.value = state.value.copy(bitcoinNotice = when {
                        keptNewer -> "Mantida a avaliação salva: ela é mais recente que a resposta do serviço."
                        quote.stale -> "Saldo estimado pela última cotação disponível, de ${AppDates.timestamp(quote.timestamp)}. A fonte ainda não disponibilizou uma cotação recente."
                        else -> null
                    })
                    refresh()
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) {
                if (attempt == generation) {
                    quoteAttemptAt = null
                    val details = (error as? BitcoinQuoteUnavailable)?.failures?.joinToString(". ")
                    val preserved = if (asset.btcPrice != null) "A última avaliação foi mantida." else "Ainda não foi possível calcular seu saldo em reais."
                    mutableState.value = state.value.copy(bitcoinError = "Não foi possível atualizar o Bitcoin. $preserved${details?.let { " $it." }.orEmpty()} Toque em atualizar para tentar novamente.")
                }
            }
            finally { if (attempt == generation) mutableState.value = state.value.copy(quoting = false) }
        }
    }

    fun configureVehicle(id: String, path: String, onSaved: () -> Unit) {
        if (!state.value.unlocked || state.value.syncing || state.value.quotingVehicles) return
        val attempt = generation
        mutableState.value = state.value.copy(syncing = true, error = null)
        viewModelScope.launch {
            try {
                val snapshot = withContext(Dispatchers.IO) { synchronized(databaseGuard) {
                    check(attempt == generation)
                    val repository = LedgerRepository(checkNotNull(session).database)
                    val current = repository.snapshot().assets.assets.single { it.id == id }
                    require(current.kind == AssetKind.VEHICLE && !current.disposed)
                    repository.append(LedgerAction.Asset(AssetChange.Configure(id, path, current.monthlyFgts, current.fgtsDepositsActive)))
                    repository.snapshot()
                } }
                if (attempt == generation) {
                    mutableState.value = state.value.copy(ledger = snapshot, syncing = false, vehicleError = null, vehicleNotice = null)
                    onSaved()
                    if (path.isNotBlank()) refreshVehicles(manual = true)
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { if (attempt == generation) showError("Não foi possível salvar o veículo. Seus dados foram preservados.") }
            finally { if (attempt == generation) { mutableState.value = state.value.copy(syncing = false); refresh() } }
        }
    }

    fun refreshVehicles(manual: Boolean = false) {
        if (!state.value.unlocked || !state.value.visible || state.value.syncing || state.value.quotingVehicles) return
        val today = LocalDate.now()
        val configured = state.value.ledger.assets.assets.filter { it.kind == AssetKind.VEHICLE && !it.disposed && it.vehiclePath.isNotBlank() }
        if (configured.isEmpty()) return
        val pending = configured.filter { FipeQuotes.due(it, today) }
        if (pending.isEmpty()) {
            if (manual) mutableState.value = state.value.copy(vehicleError = null, vehicleNotice = "FIPE já atualizada para o mês atual.")
            return
        }
        val now = SystemClock.elapsedRealtime()
        if (now < vehicleRateLimitedUntil) {
            if (manual) mutableState.value = state.value.copy(vehicleError = "Limite gratuito da FIPE atingido. Tente novamente amanhã.")
            return
        }
        if (!manual && vehicleAttemptAt?.let { now - it in 0..3_599_999L } == true) return
        vehicleAttemptAt = now
        val attempt = generation
        mutableState.value = state.value.copy(quotingVehicles = true, vehicleError = null, vehicleNotice = null)
        viewModelScope.launch {
            var updated = 0
            val failures = mutableListOf<String>()
            try {
                for (asset in pending) {
                    if (attempt != generation || !state.value.unlocked) return@launch
                    try {
                        val quote = withContext(Dispatchers.IO) { FipeQuotes.fetch(asset.vehiclePath, today) }
                        withContext(Dispatchers.IO) { synchronized(databaseGuard) {
                            check(attempt == generation)
                            val repository = LedgerRepository(checkNotNull(session).database)
                            val current = repository.snapshot().assets.assets.single { it.id == asset.id }
                            repository.append(LedgerAction.Asset(FipeQuotes.action(asset, current, quote, LocalDate.now())))
                        } }
                        updated++
                    } catch (cancelled: CancellationException) { throw cancelled }
                    catch (error: Exception) {
                        if (attempt != generation) return@launch
                        failures += "${asset.name}: ${FipeQuotes.failure(error)}"
                        if (error is QuoteHttpException && error.status == 429) {
                            vehicleRateLimitedUntil = SystemClock.elapsedRealtime() + 86_400_000L
                            break
                        }
                    }
                }
                if (attempt == generation) mutableState.value = state.value.copy(
                    vehicleNotice = if (updated > 0) "FIPE: $updated ${if (updated == 1) "veículo atualizado" else "veículos atualizados"}." else null,
                    vehicleError = failures.takeIf { it.isNotEmpty() }?.joinToString("\n")?.plus(" O último valor dos veículos não atualizados foi mantido."),
                )
            } finally {
                if (attempt == generation) {
                    mutableState.value = state.value.copy(quotingVehicles = false)
                    if (updated > 0) refresh()
                }
            }
        }
    }

    fun confirmStatement(reference: String) {
        val review = state.value.statements.firstOrNull() ?: return
        val date = runCatching { AppDates.parse(reference) }.getOrNull()
        if (date == null) { showError("Informe a referência como DD/MM/AAAA."); return }
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
        if (state.value.unlocked) resumeWindow.start(now)
        timeout.background(now)
        backupJob?.cancel()
        expirationJob?.cancel()
        expirationJob = viewModelScope.launch {
            delay(timeout.remaining(now))
            lock(preserveAction = true)
        }
    }

    fun onForeground(now: Long = SystemClock.elapsedRealtime()) {
        expirationJob?.cancel()
        if (state.value.unlocked && timeout.expired(now)) lock(preserveAction = true)
        if (state.value.resumeAvailable && resumeWindow.remaining(now) == 0L) clearResume()
        timeout.foreground()
        if (state.value.unlocked) resumeWindow.clear()
        mutableState.value = mutableState.value.copy(visible = true)
        scheduleBackup()
    }

    private fun clearResume() {
        cacheJob?.cancel(); cacheJob = null; resumeWindow.clear()
        if (!state.value.unlocked) mutableState.value = FinanceUiState(visible = state.value.visible)
    }

    fun lock(preserveAction: Boolean = false) {
        generation++
        vehicleAttemptAt = null
        clearBackupSecrets()
        expirationJob?.cancel()
        val previous = session
        session = null
        val remaining = resumeWindow.remaining(SystemClock.elapsedRealtime())
        val old = mutableState.value
        mutableState.value = if (preserveAction && remaining in 1..300_000L) FinanceUiState(
            visible = old.visible, resumeAvailable = true, createdAtMillis = old.createdAtMillis,
            movements = old.movements, people = old.people, ledger = old.ledger, statements = old.statements, custody = old.custody,
        ) else FinanceUiState(visible = old.visible)
        cacheJob?.cancel()
        if (state.value.resumeAvailable) cacheJob = viewModelScope.launch { delay(remaining); clearResume() }
        else resumeWindow.clear()
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
