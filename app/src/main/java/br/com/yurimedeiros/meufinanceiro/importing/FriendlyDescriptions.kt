package br.com.yurimedeiros.meufinanceiro.importing

import java.util.Locale

/** Exact identity for user-provided descriptions; never assigns categories. */
internal object FriendlyDescriptions {
    fun key(row: ImportedRow): String {
        CounterpartySuggestion.identify(row).learningKey?.let { return "entity:$it" }
        val direction = when {
            row.cents == 0L -> "zero"
            (row.cents > 0) == (row.kind != DocumentKind.NUBANK_CARD) -> "in"
            else -> "out"
        }
        val description = row.description.trim().replace(Regex("\\s+"), " ").lowercase(Locale.ROOT)
        return "description:${row.kind.name}:$direction:${description.length}:$description"
    }
}
