package br.com.yurimedeiros.meufinanceiro.importing

import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class PaymentMatchingTest {
    private fun account(id: Long = 1) = PaymentCandidate(id, "test-account", ImportedRow(DocumentKind.NUBANK_ACCOUNT,
        LocalDate.of(2026, 9, 1), "Pagamento de fatura", -10000))
    private fun card() = PaymentCandidate(2, "test-account", account().row.copy(kind = DocumentKind.NUBANK_CARD, description = "Pagamento recebido"))

    @Test fun uniqueSameDateAndAmountCreatesBothLinks() {
        assertEquals(mapOf(1L to 2L, 2L to 1L), PaymentMatching.pairs(listOf(account(), card())))
    }
    @Test fun differentDatesValuesAndGoogleAccountsStayPending() {
        for (other in listOf(card().copy(row = card().row.copy(date = LocalDate.of(2026, 9, 2))),
            card().copy(row = card().row.copy(cents = -9999)), card().copy(account = "another-account"))) {
            assertTrue(PaymentMatching.pairs(listOf(account(), other)).isEmpty())
        }
    }
    @Test fun ambiguityOnEitherSideNeverChoosesAPayment() {
        assertTrue(PaymentMatching.pairs(listOf(account(), account(3), card())).isEmpty())
        assertTrue(PaymentMatching.pairs(listOf(account(), card(), card().copy(id = 3))).isEmpty())
    }
    @Test fun negativeCreditsAndReversalsAreNotAssumedToBePayments() {
        assertFalse(PaymentMatching.isPayment(card().row.copy(description = "Estorno Loja")))
        assertFalse(PaymentMatching.isPayment(account().row.copy(description = "Transferência enviada pelo Pix")))
        assertFalse(PaymentMatching.isPayment(card().row.copy(cents = 10000)))
    }
}
