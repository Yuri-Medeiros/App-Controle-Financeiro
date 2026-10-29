package br.com.yurimedeiros.meufinanceiro.data;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(tableName = "people", indices = {@Index(value = {"normalizedName"}, unique = true)})
public class Person {
    @PrimaryKey(autoGenerate = true) public long id;
    @NonNull public String name = "";
    @NonNull public String normalizedName = "";
}
