package br.com.yurimedeiros.meufinanceiro.assets

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.Instant

internal enum class AssetKind { INVESTMENT, BITCOIN, FGTS, VEHICLE }
internal enum class AssetTransferKind { CONTRIBUTION, REDEMPTION, SALE }
internal data class AssetPosition(
    val id: String, val name: String, val kind: AssetKind, val cents: Long, val satoshis: Long,
    val confirmedDate: LocalDate, val disposed: Boolean = false,
    val btcPrice: String? = null, val quotedAt: String? = null, val quoteSource: String? = null,
    val vehiclePath: String = "", val monthlyFgts: Long = 0, val fgtsDepositsActive: Boolean = false,
    val fipeReference: String? = null, val investmentType: InvestmentType? = null,
) {
    val valueCents: Long? get() = if (disposed) 0 else if (kind == AssetKind.BITCOIN) btcPrice?.let {
        BigDecimal(it).multiply(BigDecimal.valueOf(satoshis, 8)).movePointRight(2).setScale(0, RoundingMode.HALF_EVEN).longValueExact()
    } else cents
}
internal data class GoalSettings(val targetCents: Long = 10000000, val selectedAssets: Set<String> = emptySet(),
    val includeBank: Boolean = false, val includeFlash: Boolean = false, val includeReceivables: Boolean = false)
internal sealed interface AssetChange {
    data class Register(val asset: AssetPosition) : AssetChange
    data class Confirm(val id: String, val cents: Long, val satoshis: Long, val date: LocalDate, val investmentType: InvestmentType? = null) : AssetChange
    data class Configure(val id: String, val vehiclePath: String, val monthlyFgts: Long, val depositsActive: Boolean, val name: String? = null) : AssetChange
    data class Transfer(val id: String, val movementId: Long, val kind: AssetTransferKind, val bankCents: Long, val feesCents: Long,
        val satoshis: Long, val date: LocalDate, val includedInBalance: Boolean) : AssetChange
    data class Quote(val id: String, val cents: Long, val btcPrice: String?, val timestamp: String, val source: String,
        val fipeReference: String? = null, val vehiclePath: String? = null) : AssetChange
    data class Remove(val id: String, val date: LocalDate) : AssetChange
    data class Goal(val settings: GoalSettings) : AssetChange
}
internal data class AssetBook(val assets: List<AssetPosition> = emptyList(), val goal: GoalSettings = GoalSettings(), val history: List<AssetChange> = emptyList())
internal object AssetRules {
    fun validateNew(book: AssetBook, change: AssetChange) {
        when (change) {
            is AssetChange.Register -> {
                if (change.asset.kind in setOf(AssetKind.FGTS, AssetKind.BITCOIN)) {
                    require(book.assets.none { it.kind == change.asset.kind }) { "Já existe um cadastro desse tipo. Use aportes e saques." }
                }
                if (change.asset.kind == AssetKind.VEHICLE) {
                    require(FipeVehicle.parse(change.asset.vehiclePath) != null && change.asset.fipeReference != null &&
                        change.asset.quotedAt != null && !change.asset.quoteSource.isNullOrBlank()) { "Cadastre o veículo pela FIPE." }
                }
            }
            is AssetChange.Confirm -> require(book.assets.single { it.id == change.id }.kind == AssetKind.INVESTMENT) { "Veículos usam a FIPE; para os demais tipos, use aportes e saques." }
            is AssetChange.Configure -> if (book.assets.single { it.id == change.id }.kind == AssetKind.VEHICLE) {
                require(FipeVehicle.parse(change.vehiclePath) != null) { "Selecione a identificação FIPE do veículo." }
            }
            is AssetChange.Transfer -> if (book.assets.single { it.id == change.id }.kind == AssetKind.VEHICLE) {
                require(change.kind == AssetTransferKind.SALE) { "Veículos permitem apenas venda. Para outro veículo, adicione um novo cadastro." }
            }
            is AssetChange.Quote -> {
                val asset = book.assets.single { it.id == change.id }
                val previous = asset.quotedAt
                require(previous == null || !Instant.parse(change.timestamp).isBefore(Instant.parse(previous))) { "A avaliação salva é mais recente. Seu saldo foi preservado." }
                if (asset.kind == AssetKind.VEHICLE) {
                    require(change.fipeReference != null) { "A avaliação do veículo deve vir da FIPE." }
                    require(change.vehiclePath == asset.vehiclePath) { "A identificação FIPE foi alterada durante a consulta." }
                    require(asset.fipeReference == null || java.time.YearMonth.parse(change.fipeReference) >= java.time.YearMonth.parse(asset.fipeReference))
                    require(!Instant.parse(change.timestamp).atZone(java.time.ZoneId.systemDefault()).toLocalDate().isBefore(asset.confirmedDate))
                }
            }
            else -> Unit
        }
    }
    fun satoshis(text: String): Long = BigDecimal(text.trim().replace(',', '.')).movePointRight(8).longValueExact().also { require(it >= 0) }
    fun apply(book: AssetBook, change: AssetChange): AssetBook {
        var assets = book.assets; var goal = book.goal
        when (change) {
            is AssetChange.Register -> {
                val a = change.asset.let { if (it.kind in setOf(AssetKind.FGTS, AssetKind.BITCOIN)) it.copy(name = if (it.kind == AssetKind.FGTS) "FGTS" else "Bitcoin") else it }
                require(a.id.isNotBlank() && a.name.isNotBlank() && assets.none { it.id == a.id })
                require(a.cents >= 0 && a.satoshis >= 0)
                require(a.investmentType == null || a.kind == AssetKind.INVESTMENT)
                a.fipeReference?.let {
                    require(a.kind == AssetKind.VEHICLE && a.vehiclePath.isNotBlank())
                    java.time.YearMonth.parse(it)
                }
                assets = assets + a
                goal = goal.copy(selectedAssets = goal.selectedAssets + a.id)
            }
            is AssetChange.Confirm -> {
                val a = assets.single { it.id == change.id }
                require(change.investmentType == null || a.kind == AssetKind.INVESTMENT)
                require(!a.disposed && change.cents >= 0 && change.satoshis >= 0 && change.date >= a.confirmedDate) { "Use uma confirmação de saldo mais recente. O histórico anterior foi preservado." }
                assets = assets.map { if (it.id != a.id) it else it.copy(cents = change.cents, satoshis = change.satoshis, confirmedDate = change.date,
                    investmentType = change.investmentType ?: it.investmentType,
                    fipeReference = if (it.kind == AssetKind.VEHICLE) null else it.fipeReference,
                    quotedAt = if (it.kind == AssetKind.VEHICLE) null else it.quotedAt, quoteSource = if (it.kind == AssetKind.VEHICLE) null else it.quoteSource) }
            }
            is AssetChange.Configure -> {
                val a = assets.single { it.id == change.id }; require(!a.disposed && change.monthlyFgts >= 0)
                require(change.name == null || (a.kind == AssetKind.VEHICLE && change.name.isNotBlank())) { "Informe o nome do veículo." }
                require(change.vehiclePath.isBlank() || Regex("(carros|motos|caminhoes)/marcas/[0-9]+/modelos/[0-9]+/anos/(?:[0-9]{4}|32000)-[0-9]+").matches(change.vehiclePath))
                assets = assets.map { if (it.id != a.id) it else it.copy(name = change.name?.trim() ?: it.name, vehiclePath = change.vehiclePath, monthlyFgts = change.monthlyFgts, fgtsDepositsActive = change.depositsActive,
                    fipeReference = if (it.kind == AssetKind.VEHICLE && it.vehiclePath != change.vehiclePath) null else it.fipeReference,
                    quotedAt = if (it.kind == AssetKind.VEHICLE && it.vehiclePath != change.vehiclePath) null else it.quotedAt,
                    quoteSource = if (it.kind == AssetKind.VEHICLE && it.vehiclePath != change.vehiclePath) null else it.quoteSource) }
            }
            is AssetChange.Transfer -> {
                val a = assets.single { it.id == change.id }
                require((change.movementId > 0 || (a.kind == AssetKind.FGTS && change.movementId == 0L)) && change.bankCents > 0 && change.feesCents >= 0)
                if (a.kind == AssetKind.FGTS) require(change.feesCents == 0L && !change.includedInBalance && change.kind != AssetTransferKind.SALE)
                require(book.history.filterIsInstance<AssetChange.Transfer>().none { change.movementId > 0 && it.movementId == change.movementId })
                require(!a.disposed && (change.includedInBalance || change.date >= a.confirmedDate))
                val outgoing = change.kind == AssetTransferKind.CONTRIBUTION
                if (outgoing) require(change.feesCents < change.bankCents)
                if (a.kind == AssetKind.VEHICLE && !outgoing) require(change.kind == AssetTransferKind.SALE) { "A venda retira o veículo inteiro do patrimônio." }
                if (change.kind == AssetTransferKind.SALE) require(a.kind == AssetKind.VEHICLE && !change.includedInBalance)
                val principal = if (outgoing) change.bankCents - change.feesCents else Math.addExact(change.bankCents, change.feesCents)
                assets = assets.map { if (it.id != a.id) it else when {
                    change.kind == AssetTransferKind.SALE -> it.copy(disposed = true, fipeReference = null, quotedAt = null, quoteSource = null)
                    change.includedInBalance -> it
                    it.kind == AssetKind.BITCOIN -> {
                        require(change.satoshis > 0)
                        val quantity = if (outgoing) Math.addExact(it.satoshis, change.satoshis) else Math.subtractExact(it.satoshis, change.satoshis)
                        require(quantity >= 0); it.copy(satoshis = quantity)
                    }
                    else -> {
                        val cents = if (outgoing) Math.addExact(it.cents, principal) else Math.subtractExact(it.cents, principal)
                        require(cents >= 0)
                        it.copy(cents = cents,
                            fipeReference = if (it.kind == AssetKind.VEHICLE) null else it.fipeReference,
                            quotedAt = if (it.kind == AssetKind.VEHICLE) null else it.quotedAt,
                            quoteSource = if (it.kind == AssetKind.VEHICLE) null else it.quoteSource)
                    }
                } }
            }
            is AssetChange.Quote -> {
                val a = assets.single { it.id == change.id }; require(!a.disposed)
                Instant.parse(change.timestamp); require(change.source.isNotBlank())
                require((change.fipeReference == null) == (change.vehiclePath == null)) { "Identificação e referência FIPE incompletas." }
                if (a.kind == AssetKind.BITCOIN) require(change.btcPrice != null && BigDecimal(change.btcPrice).signum() > 0)
                else require(a.kind == AssetKind.VEHICLE && change.cents > 0)
                change.fipeReference?.let {
                    require(a.kind == AssetKind.VEHICLE && change.vehiclePath == a.vehiclePath)
                    java.time.YearMonth.parse(it)
                }
                assets = assets.map { if (it.id != a.id) it else it.copy(cents = if (it.kind == AssetKind.VEHICLE) change.cents else it.cents, btcPrice = change.btcPrice, quotedAt = change.timestamp, quoteSource = change.source, fipeReference = change.fipeReference).also { updated -> updated.valueCents } }
            }
            is AssetChange.Remove -> {
                val asset = assets.single { it.id == change.id }
                require(asset.kind == AssetKind.VEHICLE && !asset.disposed) { "O veículo já foi retirado do patrimônio." }
                assets = assets.map { if (it.id == change.id) it.copy(disposed = true) else it }
            }
            is AssetChange.Goal -> {
                require(change.settings.targetCents > 0 && change.settings.selectedAssets.all { id -> assets.any { it.id == id } })
                goal = change.settings
            }
        }
        return AssetBook(assets, goal, book.history + change)
    }
}
