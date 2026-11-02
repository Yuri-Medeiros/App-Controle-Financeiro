package br.com.yurimedeiros.meufinanceiro.data;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import java.util.List;

@Dao
public interface PersonDao {
    @Query("SELECT * FROM people ORDER BY name COLLATE NOCASE, id")
    List<Person> all();

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    long insert(Person person);
}
