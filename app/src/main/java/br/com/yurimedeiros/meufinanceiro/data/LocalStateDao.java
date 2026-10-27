package br.com.yurimedeiros.meufinanceiro.data;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

@Dao
public interface LocalStateDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    void initialize(LocalState state);

    @Query("SELECT * FROM local_state WHERE id = 1")
    LocalState read();
}
