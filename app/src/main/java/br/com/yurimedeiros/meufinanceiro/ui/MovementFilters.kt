package br.com.yurimedeiros.meufinanceiro.ui

import br.com.yurimedeiros.meufinanceiro.importing.DocumentKind
import br.com.yurimedeiros.meufinanceiro.importing.FriendlyDescriptions
import br.com.yurimedeiros.meufinanceiro.importing.ReviewMovement
import java.time.LocalTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter

internal object MovementFilters {
    fun friendlyNames(movements: List<ReviewMovement>): Map<Long, String> {
        fun identity(item: ReviewMovement) =
            item.origin.substringBefore(" · ") to FriendlyDescriptions.key(item.row)
        val linked = movements.filter { it.friendlyName.isNotBlank() }
            .sortedWith(compareBy<ReviewMovement> { it.confirmedAt }.thenBy { it.id })
            .associate { identity(it) to it.friendlyName }
        return movements.associate { it.id to linked[identity(it)].orEmpty().ifBlank { it.friendlyName } }
    }

    fun filterAndSort(
        movements: List<ReviewMovement>,
        account: DocumentKind?,
        period: YearMonth,
        query: String,
        descriptions: Map<Long, String> = friendlyNames(movements),
    ): List<ReviewMovement> {
        val term = query.trim()
        val dateFormat = DateTimeFormatter.ofPattern("dd/MM/uuuu")
        return movements.filter {
            YearMonth.from(it.row.date) == period &&
                (account == null || it.row.kind == account) &&
                (term.isBlank() || it.row.description.contains(term, ignoreCase = true) ||
                    it.friendlyName.contains(term, ignoreCase = true) ||
                    descriptions[it.id].orEmpty().contains(term, ignoreCase = true) ||
                    it.row.date.format(dateFormat).contains(term) || it.row.date.toString().contains(term))
        }.sortedWith(compareByDescending<ReviewMovement> { it.row.date }
            .thenByDescending { it.row.time ?: LocalTime.MIN }
            .thenByDescending { it.id })
    }
}
