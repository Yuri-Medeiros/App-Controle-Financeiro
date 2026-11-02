package br.com.yurimedeiros.meufinanceiro.importing

import br.com.yurimedeiros.meufinanceiro.data.FinanceDatabase
import br.com.yurimedeiros.meufinanceiro.data.ImportDocument
import br.com.yurimedeiros.meufinanceiro.data.Movement
import br.com.yurimedeiros.meufinanceiro.gmail.MailDocument
import java.time.LocalDate
import java.time.LocalTime
import org.json.JSONObject

internal data class ReviewMovement(val id: Long, val row: ImportedRow, val origin: String, val status: String, val category: String, val friendlyName: String, val confirmedAt: Long, val pairedMovementId: Long)
internal data class MovementImportResult(val review: StatementReview?, val importedCount: Int)
internal data class StatementReview(val source: MailDocument, val document: ParsedDocument)

internal class MovementRepository(private val db: FinanceDatabase) {
    fun all(): List<ReviewMovement> = db.movements().all().map { ReviewMovement(it.id, decode(it.payload), it.origin, it.status, it.category, it.friendlyName, it.confirmedAt, it.pairedMovementId) }

    fun import(source: MailDocument, confirmed: LocalDate? = null): StatementReview? =
        importWithCount(source, confirmed).review

    fun importWithCount(source: MailDocument, confirmed: LocalDate? = null): MovementImportResult {
        var importedCount = 0
        var review: StatementReview? = null
        db.runInTransaction {
            val dao = db.movements()
            if (dao.document(source.source) != null) return@runInTransaction
            val document = FinancialCsv.parse(source.bytes, source.kind, source.filename)
            if (source.kind == DocumentKind.NUBANK_CARD && confirmed == null) {
                review = StatementReview(source, document)
                return@runInTransaction
            }
            val rows = if (confirmed != null) ImportComparison.withStatement(document, confirmed) else document.rows
            val account = source.source.substringBefore(':')
            val accountMovements = all().filter { it.origin.startsWith("$account · ") }
            val existing = accountMovements.map { it.row }
            val friendlyNames = accountMovements.filter { it.friendlyName.isNotBlank() }
                .sortedWith(compareBy<ReviewMovement> { it.confirmedAt }.thenBy { it.id })
                .associate { FriendlyDescriptions.key(it.row) to it.friendlyName }
            ImportComparison.compare(rows, existing).filter { it.disposition != ImportDisposition.ALREADY_PRESENT }.forEach { compared ->
                val movement = Movement().apply {
                    payload = encode(compared.row)
                    origin = "$account · ${source.filename}"
                    friendlyName = friendlyNames[FriendlyDescriptions.key(compared.row)].orEmpty()
                    status = if (compared.disposition == ImportDisposition.CONFLICT_FOR_REVIEW) "CONFLICT" else "PENDING"
                }
                dao.insert(movement)
                importedCount++
            }
            reconcilePayments()
            dao.insertDocument(ImportDocument().apply { this.source = source.source; reference = confirmed?.toString().orEmpty() })
        }
        return MovementImportResult(review, importedCount)
    }

    private fun reconcilePayments() {
        val dao = db.movements()
        val payments = dao.all().filter { it.status !in setOf("CONFLICT", "IGNORED") && !(it.status == "PAYMENT" && it.pairedMovementId > 0) }.map { it to decode(it.payload) }
            .filter { PaymentMatching.isPayment(it.second) }
        val pairs = PaymentMatching.pairs(payments.map { (item, row) -> PaymentCandidate(item.id, item.origin.substringBefore(" · "), row) })
        payments.forEach { (item, _) ->
            item.status = if (item.id in pairs) "PAYMENT" else "PAYMENT_PENDING"
            item.category = ""
            item.pairedMovementId = pairs[item.id] ?: 0
            dao.update(item)
        }
    }

    fun saveFriendlyName(id: Long, friendlyName: String) {
        val name = friendlyName.trim()
        require(name.isNotBlank() && name.length <= 120)
        db.runInTransaction {
            val dao = db.movements()
            val rows = dao.all()
            val target = rows.firstOrNull { it.id == id } ?: error("Movimentação não encontrada.")
            val account = target.origin.substringBefore(" · ")
            val identity = FriendlyDescriptions.key(decode(target.payload))
            rows.filter {
                it.origin.substringBefore(" · ") == account &&
                    FriendlyDescriptions.key(decode(it.payload)) == identity
            }.forEach {
                it.friendlyName = name
                dao.update(it)
            }
        }
    }

    fun confirm(ids: Set<Long>, category: String, friendlyName: String) {
        require(category.isNotBlank()) { "Escolha uma categoria antes de confirmar." }
        db.runInTransaction {
            val dao = db.movements()
            val chosen = dao.all().filter { it.id in ids }
            require(chosen.size == ids.size && chosen.isNotEmpty())
            chosen.forEach {
                val row = decode(it.payload)
                require(it.status !in setOf("CONFLICT", "IGNORED", "REFUND", "FAMILY_PAYMENT", "ASSET_TRANSFER", "CARD_CREDIT", "FLASH_CREDIT")) { "Este registro exige revisão financeira, não categorização de consumo." }
                require(!needsSpecialReview(row)) { "Pagamentos e créditos de fatura aguardam conciliação; não são despesas de consumo." }
                it.category = category.trim()
                it.friendlyName = friendlyName.trim()
                it.status = "CONFIRMED"
                it.confirmedAt = System.currentTimeMillis()
                dao.update(it)
            }
        }
    }

    fun confirmBatch(choices: Map<Long, Pair<String, String>>) {
        require(choices.isNotEmpty())
        db.runInTransaction {
            choices.forEach { (id, choice) -> confirm(setOf(id), choice.first, choice.second) }
        }
    }

    companion object {
        fun needsSpecialReview(row: ImportedRow) = (row.kind == DocumentKind.NUBANK_CARD && row.cents <= 0) ||
            (row.kind == DocumentKind.NUBANK_ACCOUNT && row.description.startsWith("Pagamento de fatura", true))
        private fun encode(row: ImportedRow): String = JSONObject().apply {
            put("kind", row.kind.name); put("date", row.date.toString()); put("description", row.description); put("cents", row.cents)
            put("sourceId", row.sourceId); put("time", row.time?.toString()); put("paymentMethod", row.paymentMethod)
            put("balance", row.balanceCents); put("statement", row.statement?.toString())
        }.toString()
        internal fun decode(json: String): ImportedRow = JSONObject(json).let { o ->
            fun nullable(key: String) = if (o.isNull(key) || !o.has(key)) null else o.getString(key)
            ImportedRow(DocumentKind.valueOf(o.getString("kind")), LocalDate.parse(o.getString("date")), o.getString("description"), o.getLong("cents"),
                nullable("sourceId"), nullable("time")?.let(LocalTime::parse), nullable("paymentMethod"),
                if (o.has("balance") && !o.isNull("balance")) o.getLong("balance") else null, nullable("statement")?.let(LocalDate::parse))
        }
    }
}
