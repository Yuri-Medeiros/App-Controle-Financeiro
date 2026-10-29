package br.com.yurimedeiros.meufinanceiro.importing

import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class CounterpartySuggestionTest {
    private fun row(description: String, cents: Long = -100) = ImportedRow(DocumentKind.NUBANK_ACCOUNT, LocalDate.of(2026, 9, 1), description, cents)
    @Test fun merchantCaseAndSpacesMatchButSimilarNamesRemainSeparate() {
        val first = CounterpartySuggestion.identify(row("Compra no débito - LOJA  DO BAIRRO"))
        val second = CounterpartySuggestion.identify(row("Compra no débito - loja do bairro"))
        assertEquals(first.learningKey, second.learningKey)
        assertNotEquals(first.learningKey, CounterpartySuggestion.identify(row("Compra no débito - Loja Bairro")).learningKey)
    }
    @Test fun personDoesNotDependOnBankButRequiresNameAndDocumentTogether() {
        val prefix = "Transferência recebida pelo Pix - Pessoa Exemplo - •••.123.456-•• - "
        val first = CounterpartySuggestion.identify(row(prefix + "BANCO A", 100))
        assertNotNull(first.learningKey)
        assertEquals(first.learningKey, CounterpartySuggestion.identify(row(prefix + "BANCO B", 100)).learningKey)
        assertNotEquals(first.learningKey, CounterpartySuggestion.identify(row(prefix.replace("Pessoa Exemplo", "Outra Pessoa") + "BANCO A", 100)).learningKey)
        assertTrue(CounterpartySuggestion.identify(row("Transferência enviada pelo Pix - Pessoa Exemplo")).needsConfirmation)
    }
    @Test fun incomingAndOutgoingSuggestionsStaySeparate() {
        val desc = "Transferência recebida pelo Pix - Pessoa Exemplo - •••.123.456-•• - BANCO"
        assertNotEquals(CounterpartySuggestion.identify(row(desc, 100)).learningKey, CounterpartySuggestion.identify(row(desc, -100)).learningKey)
    }
    @Test fun installmentsShareMerchantWithoutLosingRawDescription() {
        val first = row("Loja - Parcela 1/3", 100).copy(kind = DocumentKind.NUBANK_CARD)
        val second = first.copy(description = "Loja - Parcela 2/3")
        assertEquals(CounterpartySuggestion.identify(first).learningKey, CounterpartySuggestion.identify(second).learningKey)
        assertEquals("Loja - Parcela 1/3", first.description)
    }
    @Test fun cardChargeHasSameOutgoingDirectionAsDebitPurchase() {
        val card = row("Loja", 100).copy(kind = DocumentKind.NUBANK_CARD)
        assertEquals(CounterpartySuggestion.identify(card).learningKey, CounterpartySuggestion.identify(row("Compra no débito - Loja")).learningKey)
    }
    @Test fun genericPaymentDoesNotLearnAMerchantCategory() {
        assertNull(CounterpartySuggestion.identify(row("Pagamento de fatura")).learningKey)
        assertNull(CounterpartySuggestion.identify(row("Pagamento recebido").copy(kind = DocumentKind.NUBANK_CARD)).learningKey)
    }
}
