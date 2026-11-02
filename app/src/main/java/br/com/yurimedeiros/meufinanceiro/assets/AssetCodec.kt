package br.com.yurimedeiros.meufinanceiro.assets

import org.json.JSONObject
import org.json.JSONArray
import java.time.LocalDate

internal object AssetCodec {
    fun encode(a: AssetChange): JSONObject = JSONObject().apply {
        when (a) {
            is AssetChange.Register -> { put("kind", "register"); put("asset", JSONObject().apply {
                val p = a.asset
                put("id", p.id); put("name", p.name); put("assetKind", p.kind); put("cents", p.cents); put("satoshis", p.satoshis); put("date", p.confirmedDate)
                put("disposed", p.disposed); put("price", p.btcPrice); put("quotedAt", p.quotedAt); put("quoteSource", p.quoteSource)
                put("vehiclePath", p.vehiclePath); put("fgtsMonthly", p.monthlyFgts); put("fgtsActive", p.fgtsDepositsActive)
            }) }
            is AssetChange.Confirm -> { put("kind", "confirm"); put("id", a.id); put("cents", a.cents); put("satoshis", a.satoshis); put("date", a.date) }
            is AssetChange.Configure -> { put("kind", "configure"); put("id", a.id); put("vehiclePath", a.vehiclePath); put("fgtsMonthly", a.monthlyFgts); put("fgtsActive", a.depositsActive) }
            is AssetChange.Transfer -> { put("kind", "transfer"); put("id", a.id); put("movement", a.movementId); put("transferKind", a.kind); put("bank", a.bankCents); put("fees", a.feesCents); put("satoshis", a.satoshis); put("date", a.date); put("included", a.includedInBalance) }
            is AssetChange.Quote -> { put("kind", "quote"); put("id", a.id); put("cents", a.cents); put("price", a.btcPrice); put("timestamp", a.timestamp); put("source", a.source) }
            is AssetChange.Goal -> { put("kind", "goal"); put("target", a.settings.targetCents); put("assets", JSONArray(a.settings.selectedAssets.toList())); put("bank", a.settings.includeBank); put("flash", a.settings.includeFlash); put("receivables", a.settings.includeReceivables) }
        }
    }
    fun decode(o: JSONObject): AssetChange {
        fun nullable(j: JSONObject, key: String) = if (j.isNull(key) || !j.has(key)) null else j.getString(key)
        return when (o.getString("kind")) {
            "register" -> o.getJSONObject("asset").let { a -> AssetChange.Register(AssetPosition(a.getString("id"), a.getString("name"), AssetKind.valueOf(a.getString("assetKind")), a.getLong("cents"), a.getLong("satoshis"), LocalDate.parse(a.getString("date")), a.getBoolean("disposed"), nullable(a, "price"), nullable(a, "quotedAt"), nullable(a, "quoteSource"), a.getString("vehiclePath"), a.getLong("fgtsMonthly"), a.getBoolean("fgtsActive"))) }
            "confirm" -> AssetChange.Confirm(o.getString("id"), o.getLong("cents"), o.getLong("satoshis"), LocalDate.parse(o.getString("date")))
            "configure" -> AssetChange.Configure(o.getString("id"), o.getString("vehiclePath"), o.getLong("fgtsMonthly"), o.getBoolean("fgtsActive"))
            "transfer" -> AssetChange.Transfer(o.getString("id"), o.getLong("movement"), AssetTransferKind.valueOf(o.getString("transferKind")), o.getLong("bank"), o.getLong("fees"), o.getLong("satoshis"), LocalDate.parse(o.getString("date")), o.getBoolean("included"))
            "quote" -> AssetChange.Quote(o.getString("id"), o.getLong("cents"), nullable(o, "price"), o.getString("timestamp"), o.getString("source"))
            "goal" -> AssetChange.Goal(GoalSettings(o.getLong("target"), o.getJSONArray("assets").let { ids -> (0 until ids.length()).map { ids.getString(it) }.toSet() }, o.getBoolean("bank"), o.getBoolean("flash"), o.getBoolean("receivables")))
            else -> error("Histórico de patrimônio não reconhecido.")
        }
    }
}
