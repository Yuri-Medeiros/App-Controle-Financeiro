package br.com.yurimedeiros.meufinanceiro.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import br.com.yurimedeiros.meufinanceiro.formatting.AppDates

/** The same pending import can be confirmed from Faturas or Movimentações. */
@Composable
internal fun StatementReferenceReview(state: FinanceUiState, model: FinanceViewModel) {
    state.statements.firstOrNull()?.let { statement ->
        var reference by remember(statement.source.source) { mutableStateOf(statement.document.proposedStatement?.let(AppDates::date).orEmpty()) }
        // This pending action has its own light surface and readable field colors.
        // A native filled button avoids stacking the glass border with its outline.
        MaterialTheme(
            colorScheme = lightColorScheme(
                primary = Color(0xFF6554E8),
                onPrimary = Color.White,
                surface = Color(0xFFEFEDFA),
                onSurface = Color(0xFF202039),
                onSurfaceVariant = Color(0xFF55536B),
                outline = Color(0xFF858198),
            ),
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface,
            ) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Confirme a referência da fatura", fontWeight = FontWeight.SemiBold)
                    Text(statement.source.filename, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedTextField(
                        reference, { reference = it },
                        label = { Text("Data de referência") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = FinanceInputShape,
                        singleLine = true,
                        enabled = !state.syncing,
                    )
                    Button(
                        onClick = { model.confirmStatement(reference) },
                        enabled = !state.syncing,
                        shape = FinanceInputShape,
                        border = null,
                    ) { Text("Confirmar referência") }
                }
            }
        }
    }
}
