package br.com.yurimedeiros.meufinanceiro.ledger

import br.com.yurimedeiros.meufinanceiro.importing.ReviewMovement
import java.time.YearMonth

internal data class ImportedInstallment(val number: Int, val count: Int)
internal object ImportedInstallments {
    private val pattern = Regex("(?<![0-9/])([0-9]{1,3})\\s*/\\s*([0-9]{1,3})(?![0-9/])")
    fun parse(description: String): ImportedInstallment? = pattern.find(description)?.let {
        val number = it.groupValues[1].toInt(); val count = it.groupValues[2].toInt()
        require(count in 1..120 && number in 1..count) { "A numeração de parcelas da descrição original é inválida." }
        ImportedInstallment(number, count)
    }
    fun schedule(description: String) = parse(description) ?: ImportedInstallment(1, 1)
    fun options(snapshot: LedgerSnapshot, source: ReviewMovement, movements: List<ReviewMovement>): List<Pair<PurchasePlan, SchedulePart>> {
        if (source.row.kind != br.com.yurimedeiros.meufinanceiro.importing.DocumentKind.NUBANK_CARD || source.row.cents <= 0) return emptyList()
        val imported = parse(source.row.description) ?: return emptyList()
        val reference = source.row.statement?.let(YearMonth::from) ?: return emptyList()
        val byId = movements.associateBy { it.id }
        return snapshot.purchases.filter { plan ->
            val linked = plan.parts.filter { it.movementId > 0 }
            plan.parts.size == imported.count && linked.isNotEmpty() && linked.all { part ->
                byId[part.movementId]?.row?.description?.let { description -> runCatching {
                    val original = parse(description)
                    original?.count == imported.count && original.number == part.number
                }.getOrDefault(false) } == true
            }
        }.flatMap { plan -> plan.parts.filter { it.movementId == 0L && it.number == imported.number && YearMonth.from(it.due) == reference }.map { plan to it } }
    }
}
