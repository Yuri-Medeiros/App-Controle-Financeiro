package br.com.yurimedeiros.meufinanceiro.importing

internal data class PaymentCandidate(val id: Long, val account: String, val row: ImportedRow)

/** A payment moves money between account and card; it is never another consumption category. */
internal object PaymentMatching {
    fun isPayment(row: ImportedRow) = row.cents < 0 && when (row.kind) {
        DocumentKind.NUBANK_CARD -> row.description == "Pagamento recebido"
        DocumentKind.NUBANK_ACCOUNT -> row.description.startsWith("Pagamento de fatura", true)
        DocumentKind.FLASH -> false
    }

    fun pairs(candidates: List<PaymentCandidate>): Map<Long, Long> {
        val result = mutableMapOf<Long, Long>()
        candidates.filter { isPayment(it.row) }.groupBy { Triple(it.account, it.row.date, it.row.cents) }.values.forEach { group ->
            val accounts = group.filter { it.row.kind == DocumentKind.NUBANK_ACCOUNT }
            val cards = group.filter { it.row.kind == DocumentKind.NUBANK_CARD }
            if (accounts.size == 1 && cards.size == 1) {
                result[accounts.single().id] = cards.single().id
                result[cards.single().id] = accounts.single().id
            }
        }
        return result
    }
}
