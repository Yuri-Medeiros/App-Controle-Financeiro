package br.com.yurimedeiros.meufinanceiro.importing

import java.time.LocalDate

internal enum class ImportDisposition { ALREADY_PRESENT, NEW_FOR_REVIEW, CONFLICT_FOR_REVIEW }
internal data class ComparedRow(val row: ImportedRow, val disposition: ImportDisposition)

/** A proposal, not an instruction to insert/overwrite. All new rows still need the approved review. */
internal object ImportComparison {
    fun compare(incoming: List<ImportedRow>, existing: List<ImportedRow>): List<ComparedRow> {
        val available = existing.groupingBy(::exactKey).eachCount().toMutableMap()
        val accountHistory = existing.filter { it.kind == DocumentKind.NUBANK_ACCOUNT }
            .groupBy { it.sourceId }.mapValues { it.value.toMutableList() }.toMutableMap()
        return incoming.map { row ->
            require(row.kind != DocumentKind.NUBANK_CARD || row.statement != null) { "Confirme a referência da fatura antes de comparar." }
            require(row.kind != DocumentKind.NUBANK_ACCOUNT || !row.sourceId.isNullOrBlank())
            val key = exactKey(row)
            val count = available.getOrDefault(key, 0)
            val prior = if (row.kind == DocumentKind.NUBANK_ACCOUNT) accountHistory.getOrPut(row.sourceId) { mutableListOf() } else emptyList()
            val disposition = when {
                count > 0 -> { available[key] = count - 1; ImportDisposition.ALREADY_PRESENT }
                row.kind != DocumentKind.NUBANK_ACCOUNT -> ImportDisposition.NEW_FOR_REVIEW
                prior.any { exactKey(it) == key } -> ImportDisposition.ALREADY_PRESENT
                prior.any { sign(it.cents) == sign(row.cents) || it.cents == 0L || row.cents == 0L } -> ImportDisposition.CONFLICT_FOR_REVIEW
                else -> ImportDisposition.NEW_FOR_REVIEW
            }
            if (row.kind == DocumentKind.NUBANK_ACCOUNT) accountHistory.getValue(row.sourceId).add(row)
            ComparedRow(row, disposition)
        }
    }

    /** Explicit user-confirmed reference, not inferred from transaction date or closing rules. */
    fun withStatement(document: ParsedDocument, confirmed: LocalDate): List<ImportedRow> {
        require(document.kind == DocumentKind.NUBANK_CARD)
        return document.rows.map { it.copy(statement = confirmed) }
    }

    private fun sign(value: Long) = value.compareTo(0)
    private fun exactKey(row: ImportedRow): List<Any?> = when (row.kind) {
        DocumentKind.NUBANK_ACCOUNT -> listOf(row.kind, row.sourceId, row.date, row.description, row.cents)
        DocumentKind.FLASH -> listOf(row.kind, row.date, row.time, row.description, row.cents, row.paymentMethod, row.balanceCents)
        DocumentKind.NUBANK_CARD -> listOf(row.kind, row.statement, row.date, row.description, row.cents)
    }
}
