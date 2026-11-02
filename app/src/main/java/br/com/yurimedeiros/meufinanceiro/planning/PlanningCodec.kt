package br.com.yurimedeiros.meufinanceiro.planning

import br.com.yurimedeiros.meufinanceiro.domain.ReserveLocation
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

internal object PlanningCodec {
    fun encode(p: PlanningState): JSONObject = JSONObject().apply {
        put("balance", p.balanceCents); put("balanceDate", p.balanceDate); put("salary", p.salaryCents)
        put("invoiceDates", JSONObject(p.invoiceDates.mapValues { it.value.toString() })); put("budgets", JSONObject(p.budgets)); put("salaryDates", JSONObject(p.salaryDates.mapValues { it.value.toString() }))
        put("fixedReserve", p.fixedReserve); put("reserveStart", p.reserveStart)
        put("reserves", JSONArray().apply { p.reserves.forEach { r -> put(JSONObject().apply {
            put("id", r.id); put("title", r.title); put("cents", r.cents); put("location", r.location); put("date", r.date); put("cycle", r.cycle)
        }) } })
        put("expenses", JSONArray().apply { p.expenses.forEach { e -> put(JSONObject().apply {
            put("id", e.id); put("title", e.title); put("cents", e.cents); put("firstDue", e.firstDue); put("frequency", e.frequency); put("category", e.category); put("active", e.active); put("endDate", e.endDate); put("confirmedDates", JSONObject(e.confirmedDates.mapValues { it.value.toString() }))
        }) } })
    }
    fun decode(o: JSONObject): PlanningState {
        fun date(key: String) = if (o.isNull(key) || !o.has(key)) null else LocalDate.parse(o.getString(key))
        val budgets = o.getJSONObject("budgets"); val dates = o.getJSONObject("salaryDates")
        val reserves = o.getJSONArray("reserves"); val expenses = o.getJSONArray("expenses")
        return PlanningState(
            if (o.isNull("balance") || !o.has("balance")) null else o.getLong("balance"), date("balanceDate"), o.getLong("salary"),
            budgets.keys().asSequence().associateWith { budgets.getLong(it) }, dates.keys().asSequence().associateWith { LocalDate.parse(dates.getString(it)) },
            o.getLong("fixedReserve"), if (o.isNull("reserveStart") || !o.has("reserveStart")) null else o.getString("reserveStart"),
            (0 until reserves.length()).map { i -> reserves.getJSONObject(i).let { ReserveChange(it.getString("id"), it.getString("title"), it.getLong("cents"), ReserveLocation.valueOf(it.getString("location")), LocalDate.parse(it.getString("date")), if (it.isNull("cycle") || !it.has("cycle")) null else it.getString("cycle")) } },
            (0 until expenses.length()).map { i -> expenses.getJSONObject(i).let { PlannedExpense(it.getString("id"), it.getString("title"), it.getLong("cents"), LocalDate.parse(it.getString("firstDue")), ExpenseFrequency.valueOf(it.getString("frequency")), it.getString("category"), it.getBoolean("active"), it.getJSONObject("confirmedDates").let { dates -> dates.keys().asSequence().associateWith { key -> LocalDate.parse(dates.getString(key)) } }, if (it.isNull("endDate") || !it.has("endDate")) null else LocalDate.parse(it.getString("endDate"))) } },
            o.getJSONObject("invoiceDates").let { values -> values.keys().asSequence().associateWith { LocalDate.parse(values.getString(it)) } },
        )
    }
}
