package br.com.yurimedeiros.meufinanceiro.data;

import androidx.room.Database;
import androidx.room.RoomDatabase;

@Database(entities = {LocalState.class}, version = 1, exportSchema = true)
public abstract class FinanceDatabase extends RoomDatabase {
    public abstract LocalStateDao localState();
}
