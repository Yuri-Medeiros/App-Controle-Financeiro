package br.com.yurimedeiros.meufinanceiro.backup

import br.com.yurimedeiros.meufinanceiro.BuildConfig
import android.database.Cursor
import br.com.yurimedeiros.meufinanceiro.data.FinanceDatabase
import br.com.yurimedeiros.meufinanceiro.importing.*
import br.com.yurimedeiros.meufinanceiro.ledger.*
import br.com.yurimedeiros.meufinanceiro.people.PersonNames
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import kotlin.math.abs

internal data class BackupPreview(val date: String, val movements: Int, val people: Int, val financialActions: Int)
internal object BackupPayload {
    private val columns = linkedMapOf(
        "local_state" to listOf("id", "createdAtMillis"),
        "people" to listOf("id", "name", "normalizedName"),
        "movements" to listOf("id", "confirmedAt", "pairedMovementId", "payload", "origin", "status", "category", "friendlyName"),
        "import_documents" to listOf("source", "reference"),
        "financial_events" to listOf("id", "createdAt", "payload"),
    )
    fun export(db: FinanceDatabase): ByteArray {
        val json = JSONObject().put("format", "meu-financeiro").put("version", 1).put("schema", 4).put("applicationId", BuildConfig.APPLICATION_ID).put("created", Instant.now().toString())
        val tables = JSONObject()
        db.runInTransaction {
            columns.forEach { (table, names) ->
                val rows = JSONArray()
                db.openHelper.writableDatabase.query("SELECT ${names.joinToString(",")} FROM $table ORDER BY ${names.first()}").use { cursor ->
                    while (cursor.moveToNext()) {
                        val row = JSONArray()
                        names.indices.forEach { i -> row.put(when (cursor.getType(i)) {
                            Cursor.FIELD_TYPE_INTEGER -> cursor.getLong(i)
                            Cursor.FIELD_TYPE_STRING -> cursor.getString(i)
                            else -> error("Tipo de dado não suportado no backup.")
                        }) }
                        rows.put(row)
                    }
                }
                tables.put(table, rows)
            }
        }
        json.put("tables", tables)
        return json.toString().toByteArray(Charsets.UTF_8).also { require(it.size <= BackupCipher.MAX_BYTES) }
    }
    private fun parse(bytes: ByteArray): JSONObject {
        require(bytes.size <= BackupCipher.MAX_BYTES)
        val json = JSONObject(bytes.toString(Charsets.UTF_8))
        require(json.getString("format") == "meu-financeiro" && json.getInt("version") == 1 && json.getInt("schema") == 4 && json.getString("applicationId") == BuildConfig.APPLICATION_ID)
        Instant.parse(json.getString("created"))
        val tables = json.getJSONObject("tables")
        columns.forEach { (table, names) ->
            val rows = tables.getJSONArray(table)
            require(rows.length() <= 200000)
            val keys = mutableSetOf<String>()
            for (i in 0 until rows.length()) {
                val row = rows.getJSONArray(i); require(row.length() == names.size)
                val numeric = when (table) {
                    "local_state" -> setOf(0, 1)
                    "people" -> setOf(0)
                    "movements" -> setOf(0, 1, 2)
                    "financial_events" -> setOf(0, 1)
                    else -> emptySet()
                }
                names.indices.forEach { j ->
                    val value = row.get(j)
                    if (j in numeric) {
                        require(value is Number)
                        java.math.BigDecimal(value.toString()).longValueExact()
                    } else require(value is String)
                }
                require(keys.add(row.get(0).toString()))
            }
        }
        val local = tables.getJSONArray("local_state")
        require(local.length() == 1 && local.getJSONArray(0).getLong(0) == 1L && local.getJSONArray(0).getLong(1) > 0)
        val people = tables.getJSONArray("people")
        val personIds = mutableSetOf<Long>(); val personNames = mutableSetOf<String>()
        for (i in 0 until people.length()) {
            val row = people.getJSONArray(i)
            require(row.getLong(0) > 0 && personIds.add(row.getLong(0)))
            val name = row.getString(1); require(name.isNotBlank() && name.length <= 120)
            require(row.getString(2) == PersonNames.key(name) && personNames.add(row.getString(2)))
        }
        val movements = tables.getJSONArray("movements")
        val sourceRows = linkedMapOf<Long, ImportedRow>(); val pairs = mutableMapOf<Long, Long>()
        for (i in 0 until movements.length()) {
            val row = movements.getJSONArray(i); val id = row.getLong(0)
            require(id > 0 && id !in sourceRows)
            sourceRows[id] = MovementRepository.decode(row.getString(3)); pairs[id] = row.getLong(2)
            require(row.getString(5) in setOf("PENDING", "CONFIRMED", "CONFLICT", "PAYMENT", "PAYMENT_PENDING", "REFUND", "FAMILY_PAYMENT", "IGNORED", "ASSET_TRANSFER", "CARD_CREDIT", "FLASH_CREDIT"))
        }
        pairs.forEach { (id, pair) -> if (pair > 0) require(pairs[pair] == id) }
        val events = tables.getJSONArray("financial_events")
        val actions = (0 until events.length()).map { i ->
            val row = events.getJSONArray(i)
            require(row.getLong(0) > 0 && (i == 0 || row.getLong(0) > events.getJSONArray(i - 1).getLong(0)))
            LedgerCodec.decode(row.getString(2))
        }
        val snapshot = FinanceLedger.replay(actions)
        require(snapshot.usedMovementIds.all { it in sourceRows })
        require(snapshot.debts.all { it.personId in personIds })
        snapshot.purchases.forEach { p ->
            require(p.shares.keys.all { it == 0L || it in personIds })
            require(p.shares.keys.none { it > 0 } || p.familyStart != null)
            p.parts.filter { it.movementId > 0 }.forEach { part ->
                val source = sourceRows.getValue(part.movementId)
                require(source.kind == DocumentKind.NUBANK_CARD && source.cents == part.cents)
            }
        }
        actions.forEach { a -> when (a) {
            is LedgerAction.CardCredit -> { val source = sourceRows.getValue(a.movementId); require(source.kind == DocumentKind.NUBANK_CARD && source.cents < 0 && !PaymentMatching.isPayment(source)) }
            is LedgerAction.FlashCredit -> { val source = sourceRows.getValue(a.movementId); require(source.kind == DocumentKind.FLASH && source.cents == 15000L && source.date == a.sunday) }
            is LedgerAction.Asset -> (a.change as? br.com.yurimedeiros.meufinanceiro.assets.AssetChange.Transfer)?.let { transfer ->
                val source = sourceRows.getValue(transfer.movementId)
                require(source.kind == DocumentKind.NUBANK_ACCOUNT && !PaymentMatching.isPayment(source) && abs(source.cents) == transfer.bankCents && source.date == transfer.date)
                require(if (transfer.kind == br.com.yurimedeiros.meufinanceiro.assets.AssetTransferKind.CONTRIBUTION) source.cents < 0 else source.cents > 0)
            }
            is LedgerAction.ForecastMatch -> { val source = sourceRows.getValue(a.movementId); require(source.kind == DocumentKind.NUBANK_ACCOUNT && source.cents < 0 && !PaymentMatching.isPayment(source)) }
            is LedgerAction.Payment -> {
                val source = sourceRows.getValue(a.movementId)
                require(source.kind == DocumentKind.NUBANK_ACCOUNT && !PaymentMatching.isPayment(source) && a.date == source.date)
                require(if (a.direction == DebtDirection.RECEIVE) source.cents > 0 else source.cents < 0)
                require(a.allocations.values.fold(0L, Math::addExact) == abs(source.cents))
            }
            is LedgerAction.Refund -> {
                val source = sourceRows.getValue(a.movementId)
                require(source.kind == DocumentKind.NUBANK_CARD && source.cents < 0 && !PaymentMatching.isPayment(source) && a.date == source.date)
                require(a.credits.values.fold(0L, Math::addExact) == abs(source.cents))
            }
            is LedgerAction.InvoicePaid -> {
                val account = sourceRows.getValue(a.accountMovementId); val card = sourceRows.getValue(a.cardMovementId)
                require(account.kind == DocumentKind.NUBANK_ACCOUNT && card.kind == DocumentKind.NUBANK_CARD)
                require(PaymentMatching.isPayment(account) && PaymentMatching.isPayment(card) && account.cents < 0 && account.cents == card.cents)
            }
            else -> Unit
        } }
        return json
    }
    fun preview(bytes: ByteArray): BackupPreview {
        val json = parse(bytes); val tables = json.getJSONObject("tables")
        return BackupPreview(json.getString("created"), tables.getJSONArray("movements").length(), tables.getJSONArray("people").length(), tables.getJSONArray("financial_events").length())
    }
    fun restore(db: FinanceDatabase, bytes: ByteArray) {
        val tables = parse(bytes).getJSONObject("tables") // Validate everything before replacing anything.
        db.runInTransaction {
            columns.keys.reversed().forEach { db.openHelper.writableDatabase.execSQL("DELETE FROM $it") }
            columns.forEach { (table, names) ->
                val rows = tables.getJSONArray(table)
                val sql = "INSERT INTO $table (${names.joinToString(",")}) VALUES (${names.joinToString(",") { "?" }})"
                for (i in 0 until rows.length()) {
                    val row = rows.getJSONArray(i)
                    val values = Array<Any>(names.size) { j -> row.get(j) }
                    db.openHelper.writableDatabase.execSQL(sql, values)
                }
            }
            check(db.localState().read() != null)
            LedgerRepository(db).snapshot()
        }
    }
}
