package br.com.yurimedeiros.meufinanceiro.ledger

import br.com.yurimedeiros.meufinanceiro.importing.*
import java.time.YearMonth

/** Forecasts are projections of real sources, not fabricated imported movements. */
internal object AutomaticInstallments {
    private data class Series(val account: String, val original: String, val count: Int, val firstMonth: YearMonth)
    private fun eligible(row: ReviewMovement) = row.row.kind == DocumentKind.NUBANK_CARD && row.row.cents > 0 && row.status !in setOf("CONFLICT", "IGNORED", "ARCHIVED")
    private fun series(row: ReviewMovement): Series? {
        if (!eligible(row)) return null
        val number = runCatching { ImportedInstallments.parse(row.row.description) }.getOrNull() ?: return null
        val reference = row.row.statement?.let(YearMonth::from) ?: return null
        return Series(row.origin.substringBefore(" · "), ImportedInstallments.identity(row.row.description), number.count, reference.minusMonths(number.number - 1L))
    }
    private fun planSeries(plan: PurchasePlan, byId: Map<Long, ReviewMovement>): Series? = plan.parts.filter { it.movementId > 0 }
        .mapNotNull { byId[it.movementId]?.let(::series) }.distinct().singleOrNull()?.takeIf { it.count == plan.parts.size }

    fun forecasts(snapshot: LedgerSnapshot, rows: List<ReviewMovement>): Map<YearMonth, List<InvoiceItem>> {
        val byId = rows.associateBy { it.id }
        val used = snapshot.usedMovementIds
        val plans = snapshot.purchases.groupBy { planSeries(it, byId) }
        val groups = rows.mapNotNull { row -> series(row)?.let { it to row } }.groupBy({ it.first }, { it.second })
        val result = linkedMapOf<YearMonth, MutableList<InvoiceItem>>()
        groups.forEach { (key, sources) ->
            val byNumber = sources.groupBy { ImportedInstallments.parse(it.row.description)!!.number }
            val modeled = plans[key].orEmpty().size
            val count = maxOf(byNumber.values.maxOf { it.size }, modeled)
            val templates = byNumber.entries.maxWith(compareBy<Map.Entry<Int, List<ReviewMovement>>> { it.value.size }.thenBy { it.key }).value.sortedBy { it.id }
            for (number in (byNumber.keys.min() + 1)..key.count) {
                val imported = byNumber[number].orEmpty().count { it.id !in used }
                val missing = (count - modeled - imported).coerceAtLeast(0)
                repeat(missing) { index ->
                    val source = templates[(modeled + imported + index) % templates.size]
                    val month = key.firstMonth.plusMonths(number - 1L)
                    result.getOrPut(month) { mutableListOf() } += InvoiceItem(source.friendlyName.ifBlank { ImportedInstallments.forecastDescription(source.row.description, number) }, source.row.cents, null, "$number/${key.count}")
                }
            }
        }
        return result
    }

    /** Match only a unique source to a unique identical, existing forecast. */
    fun links(snapshot: LedgerSnapshot, rows: List<ReviewMovement>): List<LedgerAction.Link> {
        val byId = rows.associateBy { it.id }
        val used = snapshot.usedMovementIds
        val planKeys = snapshot.purchases.associate { it.id to planSeries(it, byId) }
        val candidates = rows.filter { eligible(it) && it.id !in used }.map { source ->
            val key = series(source)
            val options = if (key == null) emptyList() else ImportedInstallments.options(snapshot, source, rows).filter { (plan, part) ->
                part.cents == source.row.cents && planKeys[plan.id] == key
            }
            source to options
        }
        val counts = candidates.flatMap { (_, choices) -> choices.map { it.first.id to it.second.number } }.groupingBy { it }.eachCount()
        return candidates.mapNotNull { (source, choices) -> choices.singleOrNull()?.takeIf { counts[it.first.id to it.second.number] == 1 }
            ?.let { LedgerAction.Link(it.first.id, it.second.number, source.id) } }
    }
}
