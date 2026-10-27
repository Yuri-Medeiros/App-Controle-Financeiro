package br.com.yurimedeiros.meufinanceiro.data;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

/** Local initialization marker. No fabricated financial transactions. */
@Entity(tableName = "local_state")
public class LocalState {
    @PrimaryKey public int id;
    public long createdAtMillis;

    public LocalState(int id, long createdAtMillis) {
        this.id = id;
        this.createdAtMillis = createdAtMillis;
    }
}
