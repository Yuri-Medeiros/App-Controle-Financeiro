package br.com.yurimedeiros.meufinanceiro.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

internal object FinanceMigrations {
    val from1To2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("CREATE TABLE IF NOT EXISTS movements (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, confirmedAt INTEGER NOT NULL, pairedMovementId INTEGER NOT NULL, payload TEXT NOT NULL, origin TEXT NOT NULL, status TEXT NOT NULL, category TEXT NOT NULL, friendlyName TEXT NOT NULL)")
            db.execSQL("CREATE TABLE IF NOT EXISTS import_documents (source TEXT NOT NULL PRIMARY KEY, reference TEXT NOT NULL)")
        }
    }
}
