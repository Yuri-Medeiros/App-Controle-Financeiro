package br.com.yurimedeiros.meufinanceiro.data

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import br.com.yurimedeiros.meufinanceiro.people.InitialPeople
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import java.io.Closeable

/** Owns the passphrase until every database connection is closed. */
internal class SecureDatabaseSession private constructor(
    val database: FinanceDatabase,
    private val password: ByteArray,
    val createdAtMillis: Long,
) : Closeable {
    override fun close() {
        try {
            database.close()
        } finally {
            password.fill(0)
        }
    }

    companion object {
        const val DATABASE_NAME = "meu-financeiro.db"

        /** Takes ownership of password, including on failure. Call on an IO thread. */
        fun open(
            context: Context,
            password: ByteArray,
            databaseName: String = DATABASE_NAME,
        ): SecureDatabaseSession {
            var database: FinanceDatabase? = null
            try {
                require(password.size == 32)
                System.loadLibrary("sqlcipher")
                database = Room.databaseBuilder(context, FinanceDatabase::class.java, databaseName)
                    .openHelperFactory(SupportOpenHelperFactory(password))
                    .addMigrations(FinanceMigrations.from1To2, FinanceMigrations.from2To3)
                    .addCallback(object : RoomDatabase.Callback() {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            InitialPeople.insert(db)
                        }
                    })
                    .build()
                // Force key validation now, before presenting an unlocked screen.
                database.openHelper.writableDatabase
                val dao = database.localState()
                dao.initialize(LocalState(1, System.currentTimeMillis()))
                val state = checkNotNull(dao.read())
                return SecureDatabaseSession(database, password, state.createdAtMillis)
            } catch (error: Throwable) {
                try {
                    database?.close()
                } finally {
                    password.fill(0)
                }
                throw error
            }
        }
    }
}
