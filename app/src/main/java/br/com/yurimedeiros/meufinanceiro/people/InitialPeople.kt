package br.com.yurimedeiros.meufinanceiro.people

import androidx.sqlite.db.SupportSQLiteDatabase

/** Seed only when creating or upgrading the database, never on every app launch. */
internal object InitialPeople {
    private val names = listOf(
        "Brenim",
        "Diego Salles",
        "Gabriel Salles",
        "Karina Medeiros",
        "Karinha cunhada",
        "Kellen Medeiros",
        "Marina Bertoluci",
        "Renan Salles",
        "Thais Medeiros",
        "Thiagao",
        "Valderez Medeiros",
    )

    fun insert(db: SupportSQLiteDatabase) {
        names.forEach { name ->
            db.execSQL("INSERT OR IGNORE INTO people (name, normalizedName) VALUES (?, ?)",
                arrayOf(name, PersonNames.key(name)))
        }
    }
}
