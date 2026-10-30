package br.com.yurimedeiros.meufinanceiro.ledger

import br.com.yurimedeiros.meufinanceiro.planning.PlanningCodec
import br.com.yurimedeiros.meufinanceiro.assets.AssetCodec
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

internal object LedgerCodec {
    private fun JSONObject.allocations() = getJSONObject("allocations").let { o -> o.keys().asSequence().associateWith { o.getLong(it) } }
    fun encode(a: LedgerAction): String = JSONObject().apply {
        put("version", 1)
        when (a) {
            is LedgerAction.CardCredit -> { put("type", "card-credit"); put("movement", a.movementId); put("reason", a.reason) }
            is LedgerAction.FlashCredit -> { put("type", "flash-credit"); put("movement", a.movementId); put("sunday", a.sunday) }
            is LedgerAction.Asset -> { put("type", "asset"); put("change", AssetCodec.encode(a.change)) }
            is LedgerAction.Planning -> { put("type", "planning"); put("settings", PlanningCodec.encode(a.settings)) }
            is LedgerAction.ForecastMatch -> { put("type", "forecast-match"); put("expense", a.expenseId); put("nominal", a.nominal); put("movement", a.movementId) }
            is LedgerAction.ResolveConflict -> { put("type", "resolve-conflict"); put("movement", a.movementId); put("distinct", a.keepDistinct) }
            is LedgerAction.Purchase -> {
                put("type", "purchase"); val p = a.plan
                put("id", p.id); put("title", p.title); put("category", p.category); put("familyOpening", JSONArray(p.familyOpening.sorted())); put("familyStart", p.familyStart); put("openingBalances", JSONObject(p.openingBalances))
                put("parts", JSONArray().apply { p.parts.forEach { part -> put(JSONObject().apply {
                    put("number", part.number); put("due", part.due); put("cents", part.cents); put("movement", part.movementId)
                }) } })
                put("shares", JSONArray().apply { p.shares.forEach { (id, values) -> put(JSONObject().apply { put("person", id); put("amounts", JSONArray(values)) }) } })
            }
            is LedgerAction.Link -> { put("type", "link"); put("purchase", a.purchaseId); put("number", a.number); put("movement", a.movementId) }
            is LedgerAction.AdjustLink -> { put("type", "adjust-link"); put("purchase", a.purchaseId); put("number", a.number); put("movement", a.movementId); put("cents", a.cents); put("due", a.due); put("allocations", JSONObject(a.shares.mapKeys { it.key.toString() })) }
            is LedgerAction.Debt -> { put("type", "debt"); put("id", a.id); put("person", a.personId); put("direction", a.direction); put("title", a.title); put("cents", a.cents); put("due", a.due) }
            is LedgerAction.Payment -> { put("type", "payment"); put("person", a.personId); put("direction", a.direction); put("movement", a.movementId); put("date", a.date); put("allocations", JSONObject(a.allocations)) }
            is LedgerAction.Refund -> { put("type", "refund"); put("purchase", a.purchaseId); put("movement", a.movementId); put("date", a.date); put("allocations", JSONObject(a.credits)) }
            is LedgerAction.Postpone -> { put("type", "postpone"); put("debt", a.debtId); put("due", a.due); put("reason", a.reason) }
            is LedgerAction.InvoicePaid -> { put("type", "invoice"); put("reference", a.reference); put("account", a.accountMovementId); put("card", a.cardMovementId) }
        }
    }.toString()
    fun decode(json: String): LedgerAction = JSONObject(json).let { o ->
        require(o.getInt("version") == 1)
        fun date(key: String) = LocalDate.parse(o.getString(key))
        when (o.getString("type")) {
            "card-credit" -> LedgerAction.CardCredit(o.getLong("movement"), o.getString("reason"))
            "flash-credit" -> LedgerAction.FlashCredit(o.getLong("movement"), date("sunday"))
            "asset" -> LedgerAction.Asset(AssetCodec.decode(o.getJSONObject("change")))
            "planning" -> LedgerAction.Planning(PlanningCodec.decode(o.getJSONObject("settings")))
            "forecast-match" -> LedgerAction.ForecastMatch(o.getString("expense"), date("nominal"), o.getLong("movement"))
            "resolve-conflict" -> LedgerAction.ResolveConflict(o.getLong("movement"), o.getBoolean("distinct"))
            "purchase" -> LedgerAction.Purchase(PurchasePlan(o.getString("id"), o.getString("title"), o.getString("category"),
                o.getJSONArray("parts").let { list -> (0 until list.length()).map { i -> list.getJSONObject(i).let {
                    SchedulePart(it.getInt("number"), LocalDate.parse(it.getString("due")), it.getLong("cents"), it.getLong("movement"))
                } } },
                o.getJSONArray("shares").let { shares -> (0 until shares.length()).associate { index ->
                    val share = shares.getJSONObject(index)
                    share.getLong("person") to share.getJSONArray("amounts").let { list -> (0 until list.length()).map(list::getLong) }
                } },
                o.getJSONArray("familyOpening").let { values -> (0 until values.length()).map { values.getInt(it) }.toSet() },
                if (o.isNull("familyStart") || !o.has("familyStart")) null else date("familyStart"),
                o.getJSONObject("openingBalances").let { values -> values.keys().asSequence().associateWith { values.getLong(it) } },
            ))
            "link" -> LedgerAction.Link(o.getString("purchase"), o.getInt("number"), o.getLong("movement"))
            "adjust-link" -> LedgerAction.AdjustLink(o.getString("purchase"), o.getInt("number"), o.getLong("movement"), o.getLong("cents"), date("due"), o.allocations().mapKeys { it.key.toLong() })
            "debt" -> LedgerAction.Debt(o.getString("id"), o.getLong("person"), DebtDirection.valueOf(o.getString("direction")), o.getString("title"), o.getLong("cents"), date("due"))
            "payment" -> LedgerAction.Payment(o.getLong("person"), DebtDirection.valueOf(o.getString("direction")), o.getLong("movement"), date("date"), o.allocations())
            "refund" -> LedgerAction.Refund(o.getString("purchase"), o.getLong("movement"), date("date"), o.allocations())
            "postpone" -> LedgerAction.Postpone(o.getString("debt"), date("due"), o.getString("reason"))
            "invoice" -> LedgerAction.InvoicePaid(date("reference"), o.getLong("account"), o.getLong("card"))
            else -> error("Histórico financeiro não reconhecido. Dados preservados.")
        }
    }
}
