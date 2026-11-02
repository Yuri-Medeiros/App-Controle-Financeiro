package br.com.yurimedeiros.meufinanceiro.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

@Composable
internal fun BackupSettings(state: FinanceUiState, model: FinanceViewModel, authorize: () -> Unit) {
    var confirmEmpty by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf(false) }
    // Deliberately not rememberSaveable: recovery passwords never enter saved instance state.
    var password by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    GlassPanel {
        Text("Backup e recuperação", style = MaterialTheme.typography.titleMedium)
        Text(if (state.backupEnabled) "Automático nesta sessão · mudanças agrupadas por 30 segundos" else "Senha de recuperação independente do bloqueio e da assinatura", style = MaterialTheme.typography.bodySmall)
        state.backupMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        if (state.backupNeedsConfirmation) OutlinedButton(onClick = { confirmEmpty = true }) { Text("Iniciar com os dados deste aparelho") }
        if (state.backupWorking) LinearProgressIndicator(Modifier.fillMaxWidth())
        TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "Recolher" else "Gerenciar backup") }
        if (expanded) {
            Text("Guarde esta senha fora do aparelho. Ela não fica no backup e não pode ser recuperada pelo app. Use pelo menos 12 caracteres.", style = MaterialTheme.typography.bodySmall)
            OutlinedTextField(password, { password = it }, label = { Text("Senha de recuperação") }, modifier = Modifier.fillMaxWidth(), shape = FinanceInputShape, singleLine = true,
                visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), enabled = !state.backupWorking)
            OutlinedTextField(confirmation, { confirmation = it }, label = { Text("Confirmar senha") }, modifier = Modifier.fillMaxWidth(), shape = FinanceInputShape, singleLine = true,
                visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), enabled = !state.backupWorking)
            Button(onClick = {
                if (model.prepareBackup(password, confirmation, automatic = true)) { password = ""; confirmation = ""; authorize() }
            }, enabled = !state.syncing && !state.backupWorking) { Text("Ativar backup nesta sessão") }
            OutlinedButton(onClick = {
                if (model.prepareBackup(password, confirmation, automatic = false)) { password = ""; confirmation = ""; authorize() }
            }, enabled = !state.syncing && !state.backupWorking) { Text("Buscar backups para restaurar") }
            if (state.backupEnabled) TextButton(onClick = model::backupNow, enabled = !state.backupWorking && !state.syncing) { Text("Salvar agora") }
            Text("Mais recente e uma versão diária dos últimos 15 dias. Versões antigas só são removidas após verificar o novo envio.", style = MaterialTheme.typography.bodySmall)
            state.backupFiles.forEach { file ->
                TextButton(onClick = { model.previewBackup(file) }, enabled = !state.backupWorking && !state.syncing) { Text("Revisar backup · ${file.created}") }
            }
        }
    }
    if (confirmEmpty) LedgerDialog("Usar os dados deste aparelho?", state, { confirmEmpty = false }, confirmLabel = "Confirmar novo início", confirm = {
        model.confirmNewBackup(); confirmEmpty = false
    }) {
        Text("Existem backups anteriores, mas este aparelho ainda não tem movimentações. Novos backups usarão os dados locais e a retenção de 15 dias continuará sendo aplicada. Para recuperar seu histórico, cancele e escolha Buscar backups para restaurar.")
    }
    state.backupPreview?.let { preview -> LedgerDialog("Prévia da restauração", state, model::cancelRestore,
        confirmLabel = "Restaurar estes dados", confirm = if (state.backupWorking || state.syncing) null else model::confirmRestore) {
        Text("Backup de ${preview.date}")
        Text("${preview.movements} movimentações · ${preview.people} pessoas · ${preview.financialActions} registros financeiros")
        Text("A restauração substitui os dados locais pelos deste backup. Antes, o estado atual será salvo e verificado no Drive. O bloqueio continua usando a segurança deste aparelho.")
    } }
}
