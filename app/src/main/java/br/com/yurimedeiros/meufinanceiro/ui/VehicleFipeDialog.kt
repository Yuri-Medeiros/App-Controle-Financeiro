package br.com.yurimedeiros.meufinanceiro.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import br.com.yurimedeiros.meufinanceiro.assets.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun VehicleFipeDialog(asset: AssetPosition, state: FinanceUiState, model: FinanceViewModel, close: () -> Unit, isNew: Boolean = false) {
    val initial = remember(asset.id) { FipeVehicle.parse(asset.vehiclePath) }
    var name by remember { mutableStateOf(if (isNew) "" else asset.name) }
    val titleEditor = remember { ModalTitleEditState() }
    var type by remember { mutableStateOf(initial?.type) }
    var brand by remember { mutableStateOf(initial?.brand.orEmpty()) }
    var vehicleModel by remember { mutableStateOf(initial?.model.orEmpty()) }
    var year by remember { mutableStateOf(initial?.year.orEmpty()) }
    var brands by remember { mutableStateOf(emptyList<FipeOption>()) }
    var models by remember { mutableStateOf(emptyList<FipeOption>()) }
    var years by remember { mutableStateOf(emptyList<FipeOption>()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var retry by remember { mutableIntStateOf(0) }
    var lookupGeneration by remember { mutableIntStateOf(0) }
    val cache = remember { mutableMapOf<String, List<FipeOption>>() }
    val interactive = LocalFinanceInteractive.current && state.unlocked && state.visible
    LaunchedEffect(type, brand, vehicleModel, retry, interactive) {
        val ticket = ++lookupGeneration
        if (!interactive || type == null) { loading = false; return@LaunchedEffect }
        loading = true; error = null
        suspend fun read(path: String): List<FipeOption> {
            cache[path]?.let { return it }
            return withContext(Dispatchers.IO) { FipeQuotes.catalog(path) }.also { cache[path] = it }
        }
        try {
            val prefix = checkNotNull(type).apiKey
            brands = read("$prefix/brands")
            if (brands.none { it.code == brand }) { models = emptyList(); years = emptyList(); return@LaunchedEffect }
            models = read("$prefix/brands/$brand/models")
            if (models.none { it.code == vehicleModel }) { years = emptyList(); return@LaunchedEffect }
            years = read("$prefix/brands/$brand/models/$vehicleModel/years")
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) { if (ticket == lookupGeneration) error = FipeQuotes.failure(failure) }
        finally { if (ticket == lookupGeneration) loading = false }
    }
    val complete = type != null && brands.any { it.code == brand } && models.any { it.code == vehicleModel } && years.any { it.code == year }
    val identityUnchanged = !isNew && initial != null && type == initial.type && brand == initial.brand &&
        vehicleModel == initial.model && year == initial.year
    AssetDialog(if (isNew) "Adicionar veículo" else "Editar veículo", state, close, eyebrow = null, confirmLabel = if (isNew) "Adicionar veículo" else "Salvar",
        confirmEnabled = !titleEditor.editing && (!loading || identityUnchanged) && !state.quotingVehicles && (complete || identityUnchanged) && name.isNotBlank(), confirm = {
            val path = FipeVehicle(checkNotNull(type), brand, vehicleModel, year).path
            if (isNew) model.registerVehicle(asset.id, name.trim(), path, close)
            else model.configureVehicle(asset.id, name.trim(), path, close)
        }, titleContent = {
            EditableModalTitle(name, { name = it }, Modifier.weight(1f), placeholder = "Novo veículo",
                original = if (isNew) "Adicionar veículo" else "Editar veículo", enabled = !state.syncing, editState = titleEditor)
        }) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            FipeOptionField("Tipo", type?.key.orEmpty(), FipeType.entries.map { FipeOption(it.key, it.label) }, !state.syncing && !loading) { code ->
                type = FipeType.entries.single { it.key == code }
                brand = ""; vehicleModel = ""; year = ""
                brands = emptyList(); models = emptyList(); years = emptyList()
            }
            FipeOptionField("Marca", brand, brands, !state.syncing && !loading && type != null) {
                brand = it; vehicleModel = ""; year = ""; models = emptyList(); years = emptyList()
            }
            FipeOptionField("Modelo", vehicleModel, models, !state.syncing && !loading && brand.isNotBlank()) {
                vehicleModel = it; year = ""; years = emptyList()
            }
            FipeOptionField("Ano-modelo e combustível", year, years, !state.syncing && !loading && vehicleModel.isNotBlank()) { year = it }
            if (state.quotingVehicles) Text("Consultando FIPE…", style = MaterialTheme.typography.bodySmall)
            if (loading) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                Text("Carregando opções…", style = MaterialTheme.typography.bodySmall)
            }
            error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = { retry++ }, enabled = !loading && !state.syncing) { Text("Tentar novamente") }
            }
        }
    }
}

@Composable
private fun FipeOptionField(label: String, selected: String, options: List<FipeOption>, enabled: Boolean, choose: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Box {
            OutlinedButton(onClick = { query = ""; open = true }, enabled = enabled && options.isNotEmpty(),
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp), shape = FinanceInputShape) {
                Text(options.firstOrNull { it.code == selected }?.name ?: "Selecionar", Modifier.weight(1f))
                Text("⌄")
            }
            DropdownMenu(expanded = open && enabled && LocalFinanceInteractive.current, onDismissRequest = { open = false },
                modifier = Modifier.widthIn(min = 240.dp, max = 360.dp).heightIn(max = 360.dp)) {
                OutlinedTextField(query, { query = it }, label = { Text("Buscar") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(8.dp))
                val filtered = options.filter { FipeQuotes.normalize(it.name).contains(FipeQuotes.normalize(query.trim())) }
                filtered.take(100).forEach { option ->
                    DropdownMenuItem(text = { Text(option.name) }, onClick = { open = false; choose(option.code) })
                }
                if (filtered.isEmpty()) Text("Nenhuma opção encontrada.", Modifier.padding(16.dp))
                if (filtered.size > 100) Text("Refine a busca para encontrar outras opções.", Modifier.padding(16.dp), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
