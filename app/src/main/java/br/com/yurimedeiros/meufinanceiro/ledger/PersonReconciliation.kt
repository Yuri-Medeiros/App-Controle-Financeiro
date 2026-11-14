package br.com.yurimedeiros.meufinanceiro.ledger

import java.time.LocalDate

internal object PersonReconciliation {
    fun start(snapshot: LedgerSnapshot, personId: Long): LocalDate? = snapshot.reconciliationStarts[personId]
        ?: snapshot.purchases.lastOrNull { personId in it.shares && it.familyStart != null }?.familyStart

    fun purchase(snapshot: LedgerSnapshot, id: String, title: String, category: String,
                 parts: List<SchedulePart>, shares: Map<Long, List<Long>>): PurchasePlan {
        val starts = shares.keys.filter { it > 0 }.associateWith { person ->
            requireNotNull(start(snapshot, person)) { "Defina a data de conciliação dessa pessoa no módulo Pessoas." }
        }
        val opening = parts.filter { part -> starts.values.any { part.due >= it } }.map { it.number }.toSet()
        val excluded = buildMap<String, Long> {
            parts.filter { it.number in opening }.forEach { part ->
                starts.forEach { (person, date) -> if (part.due < date) put("${part.number}:$person", 0L) }
            }
        }
        return PurchasePlan(id, title, category, parts, shares, opening, starts.values.minOrNull(), excluded)
    }
}
