package br.com.yurimedeiros.meufinanceiro.ledger

import br.com.yurimedeiros.meufinanceiro.importing.DocumentKind
import br.com.yurimedeiros.meufinanceiro.importing.ImportedRow
import br.com.yurimedeiros.meufinanceiro.importing.ReviewMovement
import br.com.yurimedeiros.meufinanceiro.planning.InvoiceDueCalendar
import java.time.LocalDate
import java.time.YearMonth

internal object InvoiceSchedule {
    fun due(row: ImportedRow, overrides: Map<String, LocalDate> = emptyMap()): LocalDate {
        require(row.kind == DocumentKind.NUBANK_CARD) { "Selecione um lançamento de fatura." }
        val month = YearMonth.from(requireNotNull(row.statement) { "O lançamento precisa da referência da fatura." })
        return InvoiceDueCalendar.due(month, overrides[month.toString()])
    }

    fun parts(row: ImportedRow, current: Int, count: Int, movementId: Long, overrides: Map<String, LocalDate> = emptyMap()): List<SchedulePart> {
        require(row.kind == DocumentKind.NUBANK_CARD && row.cents > 0 && movementId > 0)
        require(count in 1..120 && current in 1..count) { "Revise a parcela atual e o total de parcelas." }
        val first = YearMonth.from(requireNotNull(row.statement) { "O lançamento precisa da referência da fatura." }).minusMonths(current - 1L)
        return (1..count).map { number ->
            val month = first.plusMonths(number - 1L)
            SchedulePart(number, InvoiceDueCalendar.due(month, overrides[month.toString()]), row.cents, if (number == current) movementId else 0)
        }
    }

    fun linkAction(snapshot: LedgerSnapshot, purchaseId: String, number: Int, source: ReviewMovement, shares: Map<Long, Long>): LedgerAction {
        val plan = snapshot.purchases.single { it.id == purchaseId }
        val part = plan.parts.single { it.number == number }
        val due = due(source.row, snapshot.planning.invoiceDates)
        val sameValues = source.row.cents == part.cents && shares.keys == plan.shares.keys && shares.all { (id, cents) -> plan.shares.getValue(id)[number - 1] == cents }
        val hasRecordedSettlement = snapshot.debts.any { it.purchaseId == purchaseId && it.number == number && (it.paidCents > 0 || it.creditedCents > 0) }
        // An imported source may be linked without rewriting a historical due date
        // once receipts/refunds exist. The effective invoice date is still calculated.
        return if (sameValues && (due == part.due || hasRecordedSettlement)) LedgerAction.Link(purchaseId, number, source.id)
            else LedgerAction.AdjustLink(purchaseId, number, source.id, source.row.cents, due, shares)
    }
}
