package br.com.yurimedeiros.meufinanceiro.ui

internal data class ImportNotification(
    val id: Long,
    val failed: Boolean,
    val importedCount: Int,
    val skippedDocuments: Int = 0,
    val pendingStatements: Int = 0,
    val investmentsOnly: Boolean = false,
    val pendingCustody: Int = 0,
    val failureMessage: String? = null,
) {
    val hasWarnings: Boolean get() = skippedDocuments > 0 || pendingStatements > 0 || pendingCustody > 0
    val title: String get() = when {
        failed -> "Importação não concluída"
        hasWarnings -> "Importação concluída com avisos"
        else -> "Importação concluída"
    }
    val message: String get() = buildList {
        if (investmentsOnly) add(if (pendingCustody > 0) "Posição de investimentos pronta para revisão."
            else if (failed) "Não foi possível concluir a busca da posição de investimentos."
            else "Nenhuma posição nova de investimentos encontrada no Gmail.")
        else add(when (importedCount) {
            0 -> "Nenhuma nova movimentação importada."
            1 -> "1 movimentação importada."
            else -> "$importedCount movimentações importadas."
        })
        if (failed) add(failureMessage ?: "Os registros já importados foram preservados. Verifique a conexão e a autorização Google e tente novamente.")
        if (skippedDocuments > 0) add("$skippedDocuments mensagens/anexos fora dos formatos suportados.")
        if (pendingStatements > 0) add(
            if (pendingStatements == 1) "1 fatura aguarda confirmação da referência."
            else "$pendingStatements faturas aguardam confirmação da referência."
        )
    }.joinToString(" ")
}
