package br.com.yurimedeiros.meufinanceiro.data;

import androidx.room.Database;
import androidx.room.RoomDatabase;

@Database(entities = {LocalState.class, Movement.class, ImportDocument.class}, version = 2, exportSchema = true)
public abstract class FinanceDatabase extends RoomDatabase {
    public abstract LocalStateDao localState();
    public abstract MovementDao movements();
}
