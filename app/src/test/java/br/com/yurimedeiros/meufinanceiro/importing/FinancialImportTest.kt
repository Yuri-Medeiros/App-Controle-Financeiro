package br.com.yurimedeiros.meufinanceiro.importing

import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.*
import org.junit.Test

class FinancialImportTest {
    private val id = "00000000-0000-4000-8000-000000000001"
    private fun parse(text: String, kind: DocumentKind, filename: String = "sample.csv") =
        FinancialCsv.parse(text.toByteArray(Charsets.UTF_8), kind, filename)
    private fun account(amount: String = "10.00", description: String = "Compra no débito - Loja") =
        "Data,Valor,Identificador,Descrição\n01/09/2026,$amount,$id,$description\n"
    private fun row(cents: Long = 1000, kind: DocumentKind = DocumentKind.NUBANK_ACCOUNT) = ImportedRow(
        kind, LocalDate.of(2026, 9, 1), "Loja", cents,
        sourceId = if (kind == DocumentKind.NUBANK_ACCOUNT) id else null,
        statement = if (kind == DocumentKind.NUBANK_CARD) LocalDate.of(2026, 10, 8) else null,
    )

    @Test fun accountPreservesIdSignAndCentPrecision() {
        val item = parse("\uFEFF" + account("-0.01"), DocumentKind.NUBANK_ACCOUNT).rows.single()
        assertEquals(-1L, item.cents)
        assertEquals(id, item.sourceId)
        assertEquals(LocalDate.of(2026, 9, 1), item.date)
    }

    @Test fun flashParsesQuotedCurrencyAndKeepsZeroAndBalance() {
        val csv = "Data,Hora,Movimentação,Valor,Meio de Pagamento,Saldo\r\n" +
            "28/09/2026,12:16,Loja,\"-R$\u00a030,98\",Cartão,\"R$\u00a0300,88\"\r\n" +
            "28/09/2026,12:16,Outra loja,\"R$\u00a00,00\",Cartão,\"R$\u00a0331,86\"\r\n"
        val items = parse(csv, DocumentKind.FLASH).rows
        assertEquals(listOf(-3098L, 0L), items.map { it.cents })
        assertEquals(30088L, items.first().balanceCents)
        assertEquals(LocalTime.of(12, 16), items.first().time)
        assertEquals(2, ImportComparison.compare(items, emptyList()).count { it.disposition == ImportDisposition.NEW_FOR_REVIEW })
    }

    @Test fun cardPreservesNegativeCreditAndProposesButDoesNotConfirmStatement() {
        val doc = parse("date,title,amount\n2026-09-06,Pagamento recebido,\"- 2.980,26\"\n", DocumentKind.NUBANK_CARD, "Nubank_2026-10-08.csv")
        assertEquals(-298026L, doc.rows.single().cents)
        assertEquals(LocalDate.of(2026, 10, 8), doc.proposedStatement)
        // The repository/UI must still record a confirmation before using this proposal.
        assertEquals(LocalDate.of(2026, 11, 8), ImportComparison.withStatement(doc, LocalDate.of(2026, 11, 8)).single().statement)
    }

    @Test fun unknownCardFilenameDoesNotInventAReference() {
        val doc = parse("date,title,amount\n2026-09-01,Loja,\"10,00\"", DocumentKind.NUBANK_CARD)
        assertNull(doc.proposedStatement)
        assertThrows(IllegalArgumentException::class.java) { ImportComparison.compare(doc.rows, emptyList()) }
    }

    @Test fun csvSupportsEmbeddedCommasNewlinesQuotesAndTrailingEmptyCells() {
        assertEquals(listOf(listOf("a", "b"), listOf("uma, \"loja\"\nlocal", "")),
            CsvRecords.read("a,b\n\"uma, \"\"loja\"\"\nlocal\",\n"))
    }

    @Test fun invalidOrTruncatedCsvNeverReturnsAPartialDocument() {
        for (text in listOf(account() + "02/09/2026,1.00", account(description = "\"não fechado"),
            account().replace("01/09/2026", "31/02/2026"), account("1.234"),
            account().replace("Identificador", "Outro"), account().replace(id, "not-an-id"))) {
            assertThrows(IllegalArgumentException::class.java) { parse(text, DocumentKind.NUBANK_ACCOUNT) }
        }
        assertThrows(IllegalArgumentException::class.java) { CsvRecords.read("a\n\"b\"c") }
    }

    @Test fun invalidUtf8AndOverflowAreRejectedWithoutRounding() {
        assertThrows(IllegalArgumentException::class.java) { FinancialCsv.parse(byteArrayOf(0xC3.toByte(), 0x28), DocumentKind.FLASH, "x") }
        assertThrows(IllegalArgumentException::class.java) { parse(account("92233720368547758.08"), DocumentKind.NUBANK_ACCOUNT) }
    }

    @Test fun accountReimportIsIdempotentAndOppositeSignPreservesReversal() {
        val original = row()
        val refund = row(-1000)
        assertEquals(listOf(ImportDisposition.ALREADY_PRESENT, ImportDisposition.NEW_FOR_REVIEW),
            ImportComparison.compare(listOf(original, refund), listOf(original)).map { it.disposition })
        assertTrue(ImportComparison.compare(listOf(original, refund), listOf(original, refund))
            .all { it.disposition == ImportDisposition.ALREADY_PRESENT })
    }

    @Test fun changedValueWithSameIdAndSameSignIsAConflictNotAnOverwrite() {
        assertEquals(ImportDisposition.CONFLICT_FOR_REVIEW,
            ImportComparison.compare(listOf(row(999)), listOf(row())).single().disposition)
        assertEquals(ImportDisposition.CONFLICT_FOR_REVIEW,
            ImportComparison.compare(listOf(row().copy(description = "Outra")), listOf(row())).single().disposition)
    }

    @Test fun sameFileIdReuseIsDetected() {
        val compared = ImportComparison.compare(listOf(row(), row(), row(900)), emptyList())
        assertEquals(listOf(ImportDisposition.NEW_FOR_REVIEW, ImportDisposition.ALREADY_PRESENT, ImportDisposition.CONFLICT_FOR_REVIEW), compared.map { it.disposition })
    }

    @Test fun identicalLegitimateCardRowsPreserveMultiplicityAcrossExports() {
        val item = row(kind = DocumentKind.NUBANK_CARD)
        val incoming = listOf(item, item)
        val first = ImportComparison.compare(incoming, emptyList())
        assertEquals(2, first.count { it.disposition == ImportDisposition.NEW_FOR_REVIEW })
        assertEquals(listOf(ImportDisposition.ALREADY_PRESENT, ImportDisposition.NEW_FOR_REVIEW),
            ImportComparison.compare(incoming, listOf(item)).map { it.disposition })
        assertTrue(ImportComparison.compare(incoming, incoming).all { it.disposition == ImportDisposition.ALREADY_PRESENT })
    }

    @Test fun installmentsOnDifferentStatementsAreNotMerged() {
        val october = row(kind = DocumentKind.NUBANK_CARD).copy(description = "Loja - Parcela 1/3")
        val november = october.copy(statement = LocalDate.of(2026, 11, 8), description = "Loja - Parcela 2/3")
        assertEquals(ImportDisposition.NEW_FOR_REVIEW, ImportComparison.compare(listOf(november), listOf(october)).single().disposition)
    }

    @Test fun flashSameMinuteDifferentBalanceOrDescriptionIsNotDropped() {
        val item = row(kind = DocumentKind.FLASH).copy(time = LocalTime.NOON, paymentMethod = "Cartão", balanceCents = 2000)
        val next = item.copy(balanceCents = 1000)
        val result = ImportComparison.compare(listOf(item, next), listOf(item))
        assertEquals(listOf(ImportDisposition.ALREADY_PRESENT, ImportDisposition.NEW_FOR_REVIEW), result.map { it.disposition })
    }
}
