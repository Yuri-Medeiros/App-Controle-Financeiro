package br.com.yurimedeiros.meufinanceiro.data;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import java.util.List;

@Dao
public interface FinancialEventDao {
    @Query("SELECT * FROM financial_events ORDER BY id") List<FinancialEvent> all();
    @Insert long append(FinancialEvent event);
}
