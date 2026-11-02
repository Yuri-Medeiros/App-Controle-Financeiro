package br.com.yurimedeiros.meufinanceiro.people

import br.com.yurimedeiros.meufinanceiro.data.FinanceDatabase
import br.com.yurimedeiros.meufinanceiro.data.Person

internal class PeopleRepository(private val db: FinanceDatabase) {
    fun all(): List<Person> = db.people().all()

    fun add(name: String) {
        val display = PersonNames.display(name)
        require(display.isNotBlank() && display.length <= 120)
        val person = Person().apply {
            this.name = display
            normalizedName = PersonNames.key(display)
        }
        require(db.people().insert(person) > 0) { "Pessoa já cadastrada." }
    }
}
