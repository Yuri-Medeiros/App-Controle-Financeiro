package br.com.yurimedeiros.meufinanceiro.ledger

import br.com.yurimedeiros.meufinanceiro.importing.*
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class ImportedInstallmentsTest {
    private val day = LocalDate.of(2026, 12, 8)
    private fun source(id: Long, description: String, date: LocalDate = day) = ReviewMovement(id,
        ImportedRow(DocumentKind.NUBANK_CARD, day.minusDays(1), description, 10000, statement = date), "a · arquivo", "PENDING", "Casa", "Renomeado", 0, 0)
    private fun snapshot() = FinanceLedger.apply(LedgerSnapshot(), LedgerAction.Purchase(PurchasePlan("p", "Título sem numeração", "Casa",
        listOf(SchedulePart(1, day, 10000, 1), SchedulePart(2, day.plusMonths(1), 10000), SchedulePart(3, day.plusMonths(2), 10000)), mapOf(0L to listOf(10000L, 10000L, 10000L)))))
    @Test fun numberingComesOnlyFromOriginalDescription() {
        assertEquals(ImportedInstallment(1, 3), ImportedInstallments.parse("Loja - Parcela 1/3"))
        assertEquals(ImportedInstallment(2, 3), ImportedInstallments.parse("Loja 02 / 03"))
        assertNull(ImportedInstallments.parse("Spotify - NuPay"))
        assertNull(ImportedInstallments.parse("Compra 2026/12/08"))
        assertEquals(ImportedInstallment(1, 1), ImportedInstallments.schedule("Compra à vista"))
        assertTrue(runCatching { ImportedInstallments.parse("Loja 4/3") }.isFailure)
    }
    @Test fun onlyCompatibleNumberedForecastsAreOfferedRegardlessOfEditedTitle() {
        val first = source(1, "Loja - Parcela 1/3")
        val next = source(2, "Loja - Parcela 2/3", day.plusMonths(1))
        val options = ImportedInstallments.options(snapshot(), next, listOf(first, next))
        assertEquals(listOf(2), options.map { it.second.number })
        assertEquals("Título sem numeração", options.single().first.title)
        assertTrue(ImportedInstallments.options(snapshot(), next.copy(row = next.row.copy(description = "À vista"), friendlyName = "Loja 2/3"), listOf(first, next)).isEmpty())
        assertTrue(ImportedInstallments.options(snapshot(), next.copy(row = next.row.copy(description = "Loja 2/4")), listOf(first, next)).isEmpty())
    }
    @Test fun oldManuallyExpandedCashPurchasesAndWrongReferencesCannotBeLinked() {
        val next = source(2, "Loja 2/3", day.plusMonths(1))
        assertTrue(ImportedInstallments.options(snapshot(), next, listOf(source(1, "Loja à vista"), next)).isEmpty())
        val numbered = source(1, "Loja 1/3")
        val wrongMonth = next.copy(row = next.row.copy(statement = day.plusMonths(2)))
        assertTrue(ImportedInstallments.options(snapshot(), wrongMonth, listOf(numbered, wrongMonth)).isEmpty())
    }
}
