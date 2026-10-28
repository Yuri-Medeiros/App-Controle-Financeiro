package br.com.yurimedeiros.meufinanceiro.data;

import androidx.room.Entity;
import androidx.room.PrimaryKey;
import androidx.annotation.NonNull;

@Entity(tableName = "movements")
public class Movement {
    @PrimaryKey(autoGenerate = true) public long id;
    public long confirmedAt;
    @NonNull public String payload = "";
    @NonNull public String origin = "";
    @NonNull public String status = "PENDING";
    @NonNull public String category = "";
    @NonNull public String friendlyName = "";
}
