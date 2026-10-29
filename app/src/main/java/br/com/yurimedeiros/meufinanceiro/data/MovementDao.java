package br.com.yurimedeiros.meufinanceiro.data;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;
import java.util.List;

@Dao
public interface MovementDao {
    @Query("SELECT * FROM movements ORDER BY id") List<Movement> all();
    @Insert long insert(Movement movement);
    @Update void update(Movement movement);
    @Query("SELECT * FROM import_documents WHERE source = :source") ImportDocument document(String source);
    @Insert void insertDocument(ImportDocument document);
}
