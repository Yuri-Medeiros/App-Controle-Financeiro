package br.com.yurimedeiros.meufinanceiro.importing

import br.com.yurimedeiros.meufinanceiro.assets.*
import br.com.yurimedeiros.meufinanceiro.ledger.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.UUID

internal data class CustodyPosition(val name: String, val gross: Long, val net: Long, val description: String, val type: InvestmentType = InvestmentType.OTHER)
internal data class CustodyStatement(val date: LocalDate, val positions: List<CustodyPosition>, val gross: Long, val net: Long)

/** Recognizes Nubank custody tables, never summary totals as individual investments. */
internal object CustodyStatements {
    private const val MONEY = "[0-9]+(?:\\.[0-9]{3})*,[0-9]{2}"
    fun parse(text: String): CustodyStatement {
        require(text.length <= 1_000_000)
        val t = text.replace('\u00a0', ' ').replace(Regex("\\s+"), " ")
        require(t.contains("Extrato de Custódia") && t.contains("NU INVESTIMENTOS")) { "PDF de custódia Nubank não reconhecido." }
        val date = LocalDate.parse(Regex("Custódia em: (\\d{2}/\\d{2}/\\d{4})").find(t)?.groupValues?.get(1)
            ?: error("Data de custódia não encontrada."), DateTimeFormatter.ofPattern("dd/MM/uuuu"))
        fun cents(v: String) = FinanceLedger.money(v)
        val total = Regex("Total geral ($MONEY) ($MONEY) ($MONEY) ($MONEY)").find(t) ?: error("Resumo consolidado não encontrado.")
        val positions = mutableListOf<CustodyPosition>()
        val box = Regex("Caixinha [\"“]([^\"”]+)[\"”](.*?)Total", RegexOption.DOT_MATCHES_ALL)
        box.findAll(t).forEach { m ->
            val row = Regex("(RDB.*?) Nubank ($MONEY) ($MONEY) ($MONEY) ($MONEY)").find(m.groupValues[2]) ?: error("Caixinha não reconhecida.")
            positions += CustodyPosition("Caixinha ${m.groupValues[1]}", cents(row.groupValues[2]), cents(row.groupValues[5]), "${row.groupValues[1]} · Nubank · IR R$ ${row.groupValues[3]} · IOF R$ ${row.groupValues[4]}", InvestmentType.RDB)
        }
        val treasury = Regex("(Tesouro (?:IPCA\\+|Selic|Prefixado) (?:\\d{4})) (\\d{2}/\\d{2}/\\d{4}) ($MONEY) ($MONEY) ($MONEY) ($MONEY) ($MONEY) ($MONEY) ($MONEY)")
        treasury.findAll(t).forEach { m -> positions += CustodyPosition(m.groupValues[1], cents(m.groupValues[5]), cents(m.groupValues[9]), "Vencimento ${m.groupValues[2]} · quantidade ${m.groupValues[3]} · aplicado R$ ${m.groupValues[4]} · IR R$ ${m.groupValues[6]} · IOF R$ ${m.groupValues[7]} · B3 R$ ${m.groupValues[8]}", InvestmentType.TREASURY) }
        val etf = Regex("Fundo de Índice \\(ETF\\) ([^()]+\\(([A-Z0-9]+)\\)) ($MONEY) ($MONEY)")
        etf.findAll(t).forEach { m -> positions += CustodyPosition(m.groupValues[1].trim(), cents(m.groupValues[4]), cents(m.groupValues[4]), "ETF · ${m.groupValues[2]} · quantidade ${m.groupValues[3]} · saldo bruto de IR", InvestmentType.ETF) }
        require(positions.isNotEmpty() && positions.map { it.name }.distinct().size == positions.size) { "Posições ausentes ou duplicadas." }
        val gross = cents(total.groupValues[1]); val net = cents(total.groupValues[4])
        require(positions.all { it.net in 0..it.gross } && positions.map { it.gross }.fold(0L, Math::addExact) == gross && positions.map { it.net }.fold(0L, Math::addExact) == net) { "As posições não conferem com o total do PDF. Nenhum valor foi importado." }
        return CustodyStatement(date, positions, gross, net)
    }

    fun actions(statement: CustodyStatement, book: AssetBook): List<LedgerAction> = statement.positions.mapNotNull { p ->
        val id = UUID.nameUUIDFromBytes("nubank-custody:${p.name}".toByteArray(Charsets.UTF_8)).toString()
        val current = book.assets.singleOrNull { it.id == id }
        if (current == null) LedgerAction.Asset(AssetChange.Register(AssetPosition(id, p.name, AssetKind.INVESTMENT, p.net, 0, statement.date, investmentType = p.type)))
        else {
            val latestTransfer = book.history.filterIsInstance<AssetChange.Transfer>().filter { it.id == id }.maxOfOrNull { it.date }
            require(statement.date >= current.confirmedDate && (latestTransfer == null || statement.date >= latestTransfer)) { "Extrato anterior ao saldo atual. Nenhum valor foi alterado." }
            if (current.cents == p.net && current.confirmedDate == statement.date && current.investmentType == p.type) null
            else LedgerAction.Asset(AssetChange.Confirm(id, p.net, 0, statement.date, p.type))
        }
    }
}
