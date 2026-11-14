package br.com.yurimedeiros.meufinanceiro

import android.app.Activity
import android.app.Instrumentation
import android.os.Bundle
import br.com.yurimedeiros.meufinanceiro.data.SecureDatabaseSession
import java.security.SecureRandom
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import br.com.yurimedeiros.meufinanceiro.gmail.MailDocument
import br.com.yurimedeiros.meufinanceiro.importing.DocumentKind
import br.com.yurimedeiros.meufinanceiro.importing.MovementRepository
import br.com.yurimedeiros.meufinanceiro.backup.*
import br.com.yurimedeiros.meufinanceiro.ledger.*
import java.time.LocalDate
import br.com.yurimedeiros.meufinanceiro.people.PeopleRepository

/** Integration check using platform Instrumentation, without a test service. */
class SecurityInstrumentation : Instrumentation() {
    override fun onCreate(arguments: Bundle?) {
        super.onCreate(arguments)
        start()
    }

    override fun onStart() {
        val result = Bundle()
        val name = "instrumentation-encryption-check.db"
        val context = targetContext
        val originalKey = ByteArray(32).also(SecureRandom()::nextBytes)
        var resultCode = Activity.RESULT_CANCELED
        try {
            context.deleteDatabase(name)
            System.loadLibrary("sqlcipher")
            // Create the real encrypted schema 1, then let Room validate and migrate it.
            val helper = SupportOpenHelperFactory(originalKey.copyOf()).create(
                SupportSQLiteOpenHelper.Configuration.builder(context).name(name)
                    .callback(object : SupportSQLiteOpenHelper.Callback(1) {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            db.execSQL("CREATE TABLE local_state (id INTEGER NOT NULL PRIMARY KEY, createdAtMillis INTEGER NOT NULL)")
                            db.execSQL("INSERT INTO local_state VALUES (1, 123456)")
                            db.execSQL("CREATE TABLE room_master_table (id INTEGER PRIMARY KEY, identity_hash TEXT)")
                            db.execSQL("INSERT INTO room_master_table VALUES (42, '20debd408eaf2559cfd93232acf2c6e2')")
                        }
                        override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = error("Unexpected upgrade")
                    }).build()
            )
            helper.writableDatabase
            helper.close()
            val first = SecureDatabaseSession.open(context, originalKey.copyOf(), name)
            val createdAt = first.createdAtMillis
            check(createdAt == 123456L)
            val people = PeopleRepository(first.database)
            check(people.all().size == 11 && people.all().none { it.name == "Eu" })
            people.add("Pessoa de teste")
            check(runCatching { people.add("  PESSOA   DE TESTE ") }.isFailure)
            check(people.all().size == 12)
            val repo = MovementRepository(first.database)
            val csv = "Data,Valor,Identificador,Descrição\n01/09/2026,-10.00,00000000-0000-4000-8000-000000000001,Compra no débito - Loja\n"
            fun source(key: String, text: String = csv) = MailDocument("example@test.invalid:$key", "sample.csv", DocumentKind.NUBANK_ACCOUNT, text.toByteArray())
            repo.import(source("one"))
            repo.import(source("one"))
            repo.import(source("two"))
            check(repo.all().size == 1)
            val item = repo.all().single()
            repo.confirm(setOf(item.id), "Alimentação", "Loja de teste")
            check(repo.all().single().category == "Alimentação")
            repo.import(source("conflict", csv.replace("-10.00", "-9.00")))
            check(repo.all().count { it.status == "CONFLICT" } == 1)
            val conflict = repo.all().single { it.status == "CONFLICT" }
            val failedBatch = runCatching { repo.confirmBatch(linkedMapOf(item.id to ("Transporte" to ""), conflict.id to ("Transporte" to ""))) }
            check(failedBatch.isFailure)
            check(repo.all().single { it.id == item.id }.category == "Alimentação")
            val before = repo.all().size
            runCatching { repo.import(source("invalid", csv + "linha inválida")) }
            check(repo.all().size == before)
            check(first.database.movements().document("example@test.invalid:invalid") == null)
            val personId = people.all().single { it.name == "Pessoa de teste" }.id
            val opening = MailDocument("example@test.invalid:opening", "Nubank_2026-12-08.csv", DocumentKind.NUBANK_CARD, "date,title,amount\n".toByteArray())
            repo.import(opening)
            val card = MailDocument("example@test.invalid:card", "Nubank_2027-01-08.csv", DocumentKind.NUBANK_CARD,
                "date,title,amount\n2027-01-01,Loja - 1/2,\"200,00\"\n2027-01-02,Estorno da loja,\"-40,00\"\n".toByteArray())
            check(repo.import(card) == null)
            val cardRow = repo.all().single { it.row.kind == DocumentKind.NUBANK_CARD && it.row.cents > 0 }
            check(cardRow.row.statement == LocalDate.of(2027, 1, 8))
            val credit = repo.all().single { it.row.kind == DocumentKind.NUBANK_CARD && it.row.cents < 0 }
            repo.import(source("transfer", "Data,Valor,Identificador,Descrição\n03/01/2027,100.00,00000000-0000-4000-8000-000000000002,Transferência recebida - Pessoa de teste\n"))
            val transfer = repo.all().single { it.row.cents == 10000L }
            val ledger = LedgerRepository(first.database)
            val due = LocalDate.of(2027, 1, 9)
            val plan = PurchasePlan("test-plan", "Loja", "Casa", listOf(SchedulePart(1, due, 20000, cardRow.id), SchedulePart(2, due.plusMonths(1), 20000)), linkedMapOf(personId to listOf(10000L, 10000L), 0L to listOf(10000L, 10000L)), familyStart = LocalDate.of(2027, 1, 1))
            ledger.append(LedgerAction.Purchase(plan))
            check(ledger.snapshot().purchases.single().shares.keys.toList() == listOf(personId, 0L))
            val historySize = first.database.financialEvents().all().size
            check(runCatching { ledger.append(LedgerAction.Payment(personId, DebtDirection.RECEIVE, transfer.id, transfer.row.date, mapOf("test-plan:1:$personId" to 9999))) }.isFailure)
            check(first.database.financialEvents().all().size == historySize)
            ledger.append(LedgerAction.Payment(personId, DebtDirection.RECEIVE, transfer.id, transfer.row.date, mapOf("test-plan:1:$personId" to 10000)))
            ledger.append(LedgerAction.Refund(plan.id, credit.id, credit.row.date, mapOf("test-plan:1:$personId" to 4000)))
            check(ledger.snapshot().debts.single { it.direction == DebtDirection.PAY }.remaining == 4000L)
            check(ledger.snapshot().debts.single { it.number == 2 }.remaining == 10000L)
            repo.import(source("bank-payment", "Data,Valor,Identificador,Descrição\n05/01/2027,-160.00,00000000-0000-4000-8000-000000000003,Pagamento de fatura\n"))
            repo.import(MailDocument("example@test.invalid:card-payment", "Nubank_2027-01-08.csv", DocumentKind.NUBANK_CARD,
                "date,title,amount\n2027-01-05,Pagamento recebido,\"-160,00\"\n".toByteArray()), LocalDate.of(2027, 1, 8))
            val cardPayment = repo.all().single { it.row.kind == DocumentKind.NUBANK_CARD && it.status == "PAYMENT" }
            val attribution = LedgerAction.InvoiceAllocation(LocalDate.of(2027, 1, 8), cardPayment.pairedMovementId, cardPayment.id, linkedMapOf(0L to 10000L, personId to 6000L))
            ledger.append(attribution)
            check(runCatching { ledger.append(attribution) }.isFailure)
            check(ledger.snapshot().debts.single { it.direction == DebtDirection.PAY }.remaining == 4000L)
            first.database.openHelper.writableDatabase.query("PRAGMA cipher_version").use {
                check(it.moveToFirst() && it.getString(0).isNotBlank())
            }
            val recoveryData = BackupPayload.export(first.database)
            check(BackupPayload.preview(recoveryData).movements == 7)
            first.close()
            val header = context.getDatabasePath(name).inputStream().use { it.readNBytes(16) }
            check(!header.contentEquals("SQLite format 3\u0000".toByteArray()))

            var wrongKeyRejected = false
            try {
                SecureDatabaseSession.open(context, ByteArray(32).also(SecureRandom()::nextBytes), name).use { }
            } catch (_: Exception) { wrongKeyRejected = true }
            check(wrongKeyRejected)
            SecureDatabaseSession.open(context, originalKey.copyOf(), name).use {
                check(it.createdAtMillis == createdAt)
                val savedPeople = PeopleRepository(it.database).all()
                check(savedPeople.size == 12 && savedPeople.any { person -> person.name == "Pessoa de teste" })
                val saved = MovementRepository(it.database).all()
                check(saved.size == 7 && saved.any { row -> row.category == "Alimentação" && row.status == "CONFIRMED" })
                val ledgerSaved = LedgerRepository(it.database).snapshot()
                check(ledgerSaved.actions.size == 4)
                check(ledgerSaved.actions.filterIsInstance<LedgerAction.InvoiceAllocation>().single() == attribution)
                check(ledgerSaved.debts.single { debt -> debt.direction == DebtDirection.PAY }.remaining == 4000L)
            }
            val freshName = "$name-fresh"
            try {
                context.deleteDatabase(freshName)
                SecureDatabaseSession.open(context, originalKey.copyOf(), freshName).use {
                    check(PeopleRepository(it.database).all().size == 11)
                    BackupPayload.restore(it.database, recoveryData)
                    check(MovementRepository(it.database).all().size == 7)
                    check(PeopleRepository(it.database).all().size == 12)
                    check(LedgerRepository(it.database).snapshot().actions.filterIsInstance<LedgerAction.InvoiceAllocation>().single() == attribution)
                    check(LedgerRepository(it.database).snapshot().debts.single { d -> d.direction == DebtDirection.PAY }.remaining == 4000L)
                    val invalidBackup = org.json.JSONObject(recoveryData.toString(Charsets.UTF_8)).put("version", 999).toString().toByteArray()
                    check(runCatching { BackupPayload.restore(it.database, invalidBackup) }.isFailure)
                    check(MovementRepository(it.database).all().size == 7)

                    // Reset keeps raw sources/journal, outside allocations and assets; the
                    // same Gmail card files can then be imported and reconciled again.
                    val restoredRepo = MovementRepository(it.database)
                    val restoredLedger = LedgerRepository(it.database)
                    restoredLedger.append(LedgerAction.Debt("outside-reset", personId, DebtDirection.RECEIVE, "Avulsa", 1000, due))
                    restoredLedger.append(LedgerAction.Asset(br.com.yurimedeiros.meufinanceiro.assets.AssetChange.Register(
                        br.com.yurimedeiros.meufinanceiro.assets.AssetPosition("reset-fgts", "FGTS", br.com.yurimedeiros.meufinanceiro.assets.AssetKind.FGTS, 12345, 0, due))))
                    restoredRepo.import(source("mixed-receipt", "Data,Valor,Identificador,Descrição\n06/01/2027,50.00,00000000-0000-4000-8000-000000000004,Transferência recebida - Pessoa de teste\n"))
                    val mixed = restoredRepo.all().single { row -> row.row.cents == 5000L }
                    restoredLedger.append(LedgerAction.Payment(personId, DebtDirection.RECEIVE, mixed.id, mixed.row.date,
                        linkedMapOf("test-plan:2:$personId" to 4000L, "outside-reset" to 1000L)))
                    val accountSources = restoredRepo.all().filter { row -> row.row.kind == DocumentKind.NUBANK_ACCOUNT }.associate { row -> row.id to row.row }
                    val reset = restoredLedger.invoiceResetPreview()
                    check(reset.mixedTransfers == 1 && reset.action.movementIds.size == 3)
                    val oldEventCount = it.database.financialEvents().all().size
                    restoredLedger.append(reset.action)
                    check(it.database.financialEvents().all().size == oldEventCount + 1)
                    check(restoredLedger.snapshot().purchases.isEmpty())
                    check(restoredLedger.snapshot().debts.single().id == "outside-reset")
                    check(restoredLedger.snapshot().debts.single().paidCents == 1000L)
                    check(restoredLedger.snapshot().assets.assets.single().cents == 12345L)
                    check(restoredRepo.all().filter { row -> row.row.kind == DocumentKind.NUBANK_ACCOUNT }.associate { row -> row.id to row.row } == accountSources)
                    check(restoredRepo.all().single { row -> row.id == mixed.id }.status == "FAMILY_PAYMENT")
                    check(restoredRepo.all().single { row -> row.id == transfer.id }.status == "PENDING")
                    check(it.database.movements().all().count { row -> row.status == "ARCHIVED" } == 3)
                    check(it.database.movements().document(card.source) == null)
                    check(PeopleRepository(it.database).all().size == 12)
                    restoredRepo.import(opening)
                    check(restoredRepo.import(card) == null)
                    restoredRepo.import(MailDocument("example@test.invalid:card-payment", "Nubank_2027-01-08.csv", DocumentKind.NUBANK_CARD,
                        "date,title,amount\n2027-01-05,Pagamento recebido,\"-160,00\"\n".toByteArray()), LocalDate.of(2027, 1, 8))
                    check(restoredRepo.all().count { row -> row.row.kind == DocumentKind.NUBANK_CARD } == 3)
                    check(restoredRepo.all().single { row -> row.row.kind == DocumentKind.NUBANK_CARD && row.status == "PAYMENT" }.pairedMovementId == cardPayment.pairedMovementId)
                    check(restoredRepo.import(card) == null)
                    check(restoredRepo.all().count { row -> row.row.kind == DocumentKind.NUBANK_CARD } == 3)
                    val eventsBeforeStale = it.database.financialEvents().all().size
                    check(runCatching { restoredLedger.append(reset.action) }.isFailure)
                    check(it.database.financialEvents().all().size == eventsBeforeStale)
                    val afterResetBackup = BackupPayload.export(it.database)
                    BackupPayload.preview(afterResetBackup)
                    BackupPayload.restore(it.database, afterResetBackup)
                    check(LedgerRepository(it.database).snapshot().assets.assets.single().cents == 12345L)
                    check(LedgerRepository(it.database).snapshot().debts.single().paidCents == 1000L)
                    check(it.database.movements().all().count { row -> row.status == "ARCHIVED" } == 3)
                    afterResetBackup.fill(0)
                }
            } finally { context.deleteDatabase(freshName) }
            val windowName = "$name-window"
            try {
                context.deleteDatabase(windowName)
                SecureDatabaseSession.open(context, originalKey.copyOf(), windowName).use { window ->
                    val repository = MovementRepository(window.database)
                    fun statement(key: String, month: String, content: String) = MailDocument("window@test.invalid:$key", "Nubank_${month}-08.csv", DocumentKind.NUBANK_CARD, ("date,title,amount\n" + content).toByteArray())
                    val rejected = statement("old", "2026-11", "2026-10-01,Compra,\"10,00\"\n")
                    check(repository.importWithCount(rejected).importedCount == 0)
                    check(window.database.movements().all().isEmpty())
                    val later = statement("jan", "2027-01", "2026-12-20,Pagamento recebido,\"-100,00\"\n2026-12-20,Estorno,\"-5,00\"\n2026-12-20,Reembolso,\"-2,00\"\n2026-12-20,Compra,\"120,00\"\n")
                    check(repository.importWithCount(later).importedCount == 3)
                    check(repository.all().none { it.row.description == "Pagamento recebido" })
                    val rawCount = window.database.movements().all().size
                    check(repository.importWithCount(later.copy(source = "window@test.invalid:jan-copy")).importedCount == 0)
                    check(window.database.movements().all().size == rawCount)
                    val firstInvoice = statement("dec", "2026-12", "2026-11-20,Pagamento recebido,\"-90,00\"\n2026-11-20,Compra,\"110,00\"\n")
                    check(repository.importWithCount(firstInvoice).importedCount == 1)
                    check(repository.all().count { it.row.description == "Pagamento recebido" } == 1)
                    check(repository.all().single { it.row.description == "Pagamento recebido" }.row.statement == LocalDate.of(2027, 1, 8))
                    val profileLedger = LedgerRepository(window.database)
                    val reconciliationPerson = PeopleRepository(window.database).all().first().id
                    profileLedger.append(LedgerAction.PersonReconciliation(reconciliationPerson, LocalDate.of(2026, 12, 8)))
                    val namedSource = repository.all().single { it.row.statement == LocalDate.of(2027, 1, 8) && it.row.cents > 0 }
                    val wrongNumbering = PurchasePlan("bad-count", "Compra", "Casa", listOf(SchedulePart(1, LocalDate.of(2027, 1, 8), namedSource.row.cents, namedSource.id), SchedulePart(2, LocalDate.of(2027, 2, 10), namedSource.row.cents)), mapOf(0L to listOf(namedSource.row.cents, namedSource.row.cents)))
                    val eventsBeforeName = window.database.financialEvents().all().size
                    check(runCatching { profileLedger.appendNamedPurchase(LedgerAction.Purchase(wrongNumbering), namedSource.id, "Não salvar") }.isFailure)
                    check(window.database.financialEvents().all().size == eventsBeforeName)
                    check(repository.all().single { it.id == namedSource.id }.friendlyName.isBlank())
                    val own = PurchasePlan("named", "Compra de teste", "Casa", listOf(SchedulePart(1, LocalDate.of(2027, 1, 8), namedSource.row.cents, namedSource.id)), mapOf(0L to listOf(namedSource.row.cents)))
                    profileLedger.appendNamedPurchase(LedgerAction.Purchase(own), namedSource.id, "Compra de teste")
                    check(repository.all().single { it.id == namedSource.id }.friendlyName == "Compra de teste")
                    check(repository.all().single { it.id == namedSource.id }.row.description == namedSource.row.description)
                    val visible = repository.all()
                    val backup = BackupPayload.export(window.database)
                    BackupPayload.restore(window.database, backup)
                    check(repository.all() == visible)
                    check(profileLedger.snapshot().reconciliationStarts[reconciliationPerson] == LocalDate.of(2026, 12, 8))
                    backup.fill(0)
                    val windowLedger = LedgerRepository(window.database)
                    val reset = windowLedger.invoiceResetPreview().action
                    check(reset.movementIds.size == 6) // Includes the hidden opening payment.
                    windowLedger.append(reset)
                    check(repository.all().isEmpty())
                    check(profileLedger.snapshot().reconciliationStarts[reconciliationPerson] == LocalDate.of(2026, 12, 8))
                    repository.import(firstInvoice)
                    repository.import(later)
                    check(repository.all().count { it.row.description == "Pagamento recebido" } == 1)
                    val afterReset = BackupPayload.export(window.database)
                    BackupPayload.restore(window.database, afterReset)
                    check(repository.all().count { it.row.description == "Pagamento recebido" } == 1)
                    afterReset.fill(0)
                }
            } finally { context.deleteDatabase(windowName) }
            val automaticName = "$name-automatic"
            try {
                context.deleteDatabase(automaticName)
                SecureDatabaseSession.open(context, originalKey.copyOf(), automaticName).use { automatic ->
                    val repository = MovementRepository(automatic.database)
                    val financial = LedgerRepository(automatic.database)
                    fun installment(number: Int, month: String) = MailDocument("auto@test.invalid:$number", "Nubank_${month}-08.csv", DocumentKind.NUBANK_CARD,
                        "date,title,amount\n2027-02-01,Loja - Parcela $number/3,\"100,00\"\n".toByteArray())
                    repository.import(installment(1, "2027-02"))
                    check(repository.all().size == 1)
                    check(InvoiceBook.invoices(financial.snapshot(), repository.all()).size == 3)
                    check(financial.snapshot().purchases.isEmpty())
                    val owner = PeopleRepository(automatic.database).all().first().id
                    val imported = repository.all().single()
                    val parts = InvoiceSchedule.parts(imported.row, 1, 3, imported.id)
                    financial.appendNamedPurchase(LedgerAction.Purchase(PurchasePlan("automatic", "Loja", "Casa", parts,
                        linkedMapOf(0L to listOf(5000L, 5000L, 5000L), owner to listOf(5000L, 5000L, 5000L)), familyStart = parts.first().due)), imported.id, "Loja")
                    repository.import(installment(2, "2027-03"))
                    val saved = financial.snapshot()
                    check(saved.purchases.single().parts[1].movementId > 0)
                    check(saved.actions.filterIsInstance<LedgerAction.Link>().size == 1)
                    check(InvoiceBook.invoices(saved, repository.all()).map { it.total } == listOf(10000L, 10000L, 10000L))
                    check(InvoiceBook.invoices(saved, repository.all()).last().people.single { it.id == owner }.cents == 5000L)
                    val count = automatic.database.financialEvents().all().size
                    repository.import(installment(2, "2027-03"))
                    check(automatic.database.financialEvents().all().size == count)
                    val backup = BackupPayload.export(automatic.database)
                    BackupPayload.restore(automatic.database, backup)
                    check(LedgerRepository(automatic.database).snapshot().purchases.single().parts[1].movementId > 0)
                    check(MovementRepository(automatic.database).all().size == 2)
                    backup.fill(0)
                }
            } finally { context.deleteDatabase(automaticName) }
            recoveryData.fill(0)
            result.putString("result", "PASS: backup restore and malformed backup rejection; financial history and shares order; people seed, uniqueness and persistence; encrypted schema 1 migration, imports, deduplication, atomic rejection, review persistence, wrong-key rejection")
            resultCode = Activity.RESULT_OK
        } catch (_: Exception) {
            result.putString("result", "FAIL: encrypted storage integration check")
        } finally {
            originalKey.fill(0)
            context.deleteDatabase(name)
        }
        finish(resultCode, result)
    }
}
