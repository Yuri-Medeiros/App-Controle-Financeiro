package br.com.yurimedeiros.meufinanceiro.data;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/** Append-only financial history; payloads contain no credentials. */
@Entity(tableName = "financial_events")
public class FinancialEvent {
    @PrimaryKey(autoGenerate = true) public long id;
    public long createdAt;
    @NonNull public String payload = "";
}
