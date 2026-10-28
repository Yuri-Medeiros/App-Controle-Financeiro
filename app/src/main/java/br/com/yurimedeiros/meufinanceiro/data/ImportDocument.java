package br.com.yurimedeiros.meufinanceiro.data;

import androidx.room.Entity;
import androidx.room.PrimaryKey;
import androidx.annotation.NonNull;

@Entity(tableName = "import_documents")
public class ImportDocument {
    @PrimaryKey @NonNull public String source = "";
    @NonNull public String reference = "";
}
