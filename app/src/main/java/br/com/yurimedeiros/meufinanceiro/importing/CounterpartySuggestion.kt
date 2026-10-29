package br.com.yurimedeiros.meufinanceiro.importing

import java.util.Locale

internal data class Counterparty(val displayName: String, val learningKey: String?, val needsConfirmation: Boolean)

/** Recognition only; it never assigns a category or settles a debt. */
internal object CounterpartySuggestion {
    fun identify(row: ImportedRow): Counterparty {
        val raw = row.description
        if (row.kind == DocumentKind.NUBANK_ACCOUNT && raw.startsWith("Transferência", ignoreCase = true)) {
            val parts = raw.split(" - ")
            val name = parts.getOrNull(1).orEmpty().trim()
            val document = parts.getOrNull(2)?.trim()?.takeIf {
                Regex("(?:[0-9•*]{3}\\.[0-9•*]{3}\\.[0-9•*]{3}-[0-9•*]{2}|[0-9]{2}\\.[0-9]{3}\\.[0-9]{3}/[0-9]{4}-[0-9]{2})").matches(it)
            }
            // Masked documents are not unique on their own. Keep the full name as part of the key.
            if (name.isNotBlank() && document != null) return Counterparty(name, key(row, "person", name, document), false)
            return Counterparty(name.ifBlank { raw }, null, true)
        }
        val merchant = when (row.kind) {
            DocumentKind.FLASH -> if (row.paymentMethod == "Cartão") raw else null
            DocumentKind.NUBANK_CARD -> if (raw == "Pagamento recebido") null else raw.replace(Regex(" - Parcela [0-9]+/[0-9]+$"), "")
            DocumentKind.NUBANK_ACCOUNT -> when {
                raw.startsWith("Compra no débito via NuPay - ") -> raw.removePrefix("Compra no débito via NuPay - ")
                raw.startsWith("Compra no débito - ") -> raw.removePrefix("Compra no débito - ")
                else -> null
            }
        }
        if (merchant.isNullOrBlank()) return Counterparty(raw, null, true)
        return Counterparty(merchant, key(row, "merchant", merchant), false)
    }

    private fun key(row: ImportedRow, type: String, vararg values: String): String {
        // Keep actual financial direction, accounting for the opposite sign convention on cards.
        val direction = when {
            row.cents == 0L -> "zero"
            (row.cents > 0) == (row.kind != DocumentKind.NUBANK_CARD) -> "in"
            else -> "out"
        }
        // Length prefixes prevent collisions if a description contains separator characters.
        return (listOf(type, direction) + values.map { it.trim().replace(Regex("\\s+"), " ").lowercase(Locale.ROOT) })
            .joinToString("") { "${it.length}:$it" }
    }
}
