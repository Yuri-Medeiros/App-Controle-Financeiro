package br.com.yurimedeiros.meufinanceiro.importing

import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class FriendlyDescriptionsTest {
    private fun row(description: String, cents: Long = -100) =
        ImportedRow(DocumentKind.NUBANK_ACCOUNT, LocalDate.of(2026, 10, 26), description, cents)

    @Test fun sameMerchantSharesDescriptionAcrossDebitAndInstallments() {
        val debit = row("Compra no débito - Loja do Bairro")
        val card = row("Loja do Bairro - Parcela 2/3", 100).copy(kind = DocumentKind.NUBANK_CARD)
        assertEquals(FriendlyDescriptions.key(debit), FriendlyDescriptions.key(card))
    }

    @Test fun personKeepsIdentityAcrossBanksButNotDifferentDocuments() {
        val first = row("Transferência enviada pelo Pix - Pessoa Exemplo - •••.123.456-•• - BANCO A")
        val anotherBank = first.copy(description = first.description.replace("BANCO A", "BANCO B"))
        val anotherDocument = first.copy(description = first.description.replace("123.456", "987.654"))
        assertEquals(FriendlyDescriptions.key(first), FriendlyDescriptions.key(anotherBank))
        assertNotEquals(FriendlyDescriptions.key(first), FriendlyDescriptions.key(anotherDocument))
    }

    @Test fun exactGenericDescriptionsNormalizeOnlyCaseAndWhitespace() {
        assertEquals(FriendlyDescriptions.key(row(" Tarifa  mensal ")), FriendlyDescriptions.key(row("tarifa mensal")))
        assertNotEquals(FriendlyDescriptions.key(row("Tarifa mensal")), FriendlyDescriptions.key(row("Tarifa anual")))
    }

    @Test fun genericDescriptionsDoNotCrossSourcesOrDirections() {
        val first = row("Descrição genérica")
        assertNotEquals(FriendlyDescriptions.key(first), FriendlyDescriptions.key(first.copy(kind = DocumentKind.FLASH)))
        assertNotEquals(FriendlyDescriptions.key(first), FriendlyDescriptions.key(first.copy(cents = 100)))
    }

    @Test fun similarNamesAndIncompletePeopleAreNotMerged() {
        assertNotEquals(FriendlyDescriptions.key(row("Compra no débito - Loja Bairro")),
            FriendlyDescriptions.key(row("Compra no débito - Loja do Bairro")))
        assertNotEquals(FriendlyDescriptions.key(row("Transferência enviada pelo Pix - Ana")),
            FriendlyDescriptions.key(row("Transferência enviada pelo Pix - Ana Maria")))
    }
}
