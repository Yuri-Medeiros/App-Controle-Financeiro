package br.com.yurimedeiros.meufinanceiro.people

import java.util.Locale

internal object PersonNames {
    fun display(name: String): String = name.trim().replace(Regex("\\s+"), " ")
    fun key(name: String): String = display(name).lowercase(Locale.ROOT)
}
