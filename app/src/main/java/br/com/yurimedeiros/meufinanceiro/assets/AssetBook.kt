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
) {
    val valueCents: Long? get() = if (disposed) 0 else if (kind == AssetKind.BITCOIN) btcPrice?.let {
        BigDecimal(it).multiply(BigDecimal.valueOf(satoshis, 8)).movePointRight(2).setScale(0, RoundingMode.HALF_EVEN).longValueExact()
    } else cents
}
internal data class GoalSettings(val targetCents: Long = 10000000, val selectedAssets: Set<String> = emptySet(),
    val includeBank: Boolean = false, val includeFlash: Boolean = false, val includeReceivables: Boolean = false)
internal sealed interface AssetChange {
    data class Register(val asset: AssetPosition) : AssetChange
    data class Confirm(val id: String, val cents: Long, val satoshis: Long, val date: LocalDate) : AssetChange
    data class Configure(val id: String, val vehiclePath: String, val monthlyFgts: Long, val depositsActive: Boolean) : AssetChange
    data class Transfer(val id: String, val movementId: Long, val kind: AssetTransferKind, val bankCents: Long, val feesCents: Long,
        val satoshis: Long, val date: LocalDate, val includedInBalance: Boolean) : AssetChange
    data class Quote(val id: String, val cents: Long, val btcPrice: String?, val timestamp: String, val source: String) : AssetChange
    data class Goal(val settings: GoalSettings) : AssetChange
}
internal data class AssetBook(val assets: List<AssetPosition> = emptyList(), val goal: GoalSettings = GoalSettings(), val history: List<AssetChange> = emptyList())
internal object AssetRules {
    fun satoshis(text: String): Long = BigDecimal(text.trim().replace(',', '.')).movePointRight(8).longValueExact().also { require(it >= 0) }
    fun apply(book: AssetBook, change: AssetChange): AssetBook {
        var assets = book.assets; var goal = book.goal
        when (change) {
            is AssetChange.Register -> {
                val a = change.asset
                require(a.id.isNotBlank() && a.name.isNotBlank() && assets.none { it.id == a.id })
                require(a.cents >= 0 && a.satoshis >= 0)
                assets = assets + a
                goal = goal.copy(selectedAssets = goal.selectedAssets + a.id)
            }
            is AssetChange.Confirm -> {
                val a = assets.single { it.id == change.id }
                require(!a.disposed && change.cents >= 0 && change.satoshis >= 0 && change.date >= a.confirmedDate) { "Use uma confirmação de saldo mais recente. O histórico anterior foi preservado." }
                assets = assets.map { if (it.id != a.id) it else it.copy(cents = change.cents, satoshis = change.satoshis, confirmedDate = change.date) }
            }
            is AssetChange.Configure -> {
                val a = assets.single { it.id == change.id }; require(!a.disposed && change.monthlyFgts >= 0)
                require(change.vehiclePath.isBlank() || Regex("(carros|motos|caminhoes)/marcas/[0-9]+/modelos/[0-9]+/anos/[0-9]{4}-[0-9]+").matches(change.vehiclePath))
                assets = assets.map { if (it.id != a.id) it else it.copy(vehiclePath = change.vehiclePath, monthlyFgts = change.monthlyFgts, fgtsDepositsActive = change.depositsActive) }
            }
            is AssetChange.Transfer -> {
                val a = assets.single { it.id == change.id }
                require(change.movementId > 0 && change.bankCents > 0 && change.feesCents >= 0)
                require(a.kind != AssetKind.FGTS)
                require(book.history.filterIsInstance<AssetChange.Transfer>().none { it.movementId == change.movementId })
                require(!a.disposed && (change.includedInBalance || change.date >= a.confirmedDate))
                val outgoing = change.kind == AssetTransferKind.CONTRIBUTION
                if (outgoing) require(change.feesCents < change.bankCents)
                if (a.kind == AssetKind.VEHICLE && !outgoing) require(change.kind == AssetTransferKind.SALE) { "A venda retira o veículo inteiro do patrimônio." }
                if (change.kind == AssetTransferKind.SALE) require(a.kind == AssetKind.VEHICLE && !change.includedInBalance)
                val principal = if (outgoing) change.bankCents - change.feesCents else Math.addExact(change.bankCents, change.feesCents)
                assets = assets.map { if (it.id != a.id) it else when {
                    change.kind == AssetTransferKind.SALE -> it.copy(disposed = true)
                    change.includedInBalance -> it
                    it.kind == AssetKind.BITCOIN -> {
                        require(change.satoshis > 0)
                        val quantity = if (outgoing) Math.addExact(it.satoshis, change.satoshis) else Math.subtractExact(it.satoshis, change.satoshis)
                        require(quantity >= 0); it.copy(satoshis = quantity)
                    }
                    else -> {
                        val cents = if (outgoing) Math.addExact(it.cents, principal) else Math.subtractExact(it.cents, principal)
                        require(cents >= 0); it.copy(cents = cents)
                    }
                } }
            }
            is AssetChange.Quote -> {
                val a = assets.single { it.id == change.id }; require(!a.disposed)
                Instant.parse(change.timestamp); require(change.source.isNotBlank())
                if (a.kind == AssetKind.BITCOIN) require(change.btcPrice != null && BigDecimal(change.btcPrice).signum() > 0)
                else require(a.kind == AssetKind.VEHICLE && change.cents > 0)
                assets = assets.map { if (it.id != a.id) it else it.copy(cents = if (it.kind == AssetKind.VEHICLE) change.cents else it.cents, btcPrice = change.btcPrice, quotedAt = change.timestamp, quoteSource = change.source).also { updated -> updated.valueCents } }
            }
            is AssetChange.Goal -> {
                require(change.settings.targetCents > 0 && change.settings.selectedAssets.all { id -> assets.any { it.id == id } })
                goal = change.settings
            }
        }
        return AssetBook(assets, goal, book.history + change)
    }
}
