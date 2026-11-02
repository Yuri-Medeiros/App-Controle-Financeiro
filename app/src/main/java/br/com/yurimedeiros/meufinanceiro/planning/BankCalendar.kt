package br.com.yurimedeiros.meufinanceiro.planning

import br.com.yurimedeiros.meufinanceiro.domain.FinancialPlanning
import java.time.LocalDate

/** Verified annual snapshot, not a claim that future calendars have been published. */
internal object BankCalendar {
    const val SOURCE_DATE = "2026-10-30"
    const val MUNICIPAL_SOURCE = "https://www.ribeiraopreto.sp.gov.br/portal/principal/feriados"
    const val BANK_SOURCE = "https://portal.febraban.org.br/noticia/4413"
    private val dates = setOf("01-01", "01-20", "02-16", "02-17", "04-03", "04-21", "05-01", "06-04", "06-19", "07-09", "09-07", "10-12", "11-02", "11-15", "11-20", "12-25", "12-31").map { LocalDate.parse("2026-$it") }.toSet()
    fun propose(date: LocalDate): LocalDate {
        adjust(date)?.let { return it }
        var candidate = date
        while (candidate in dates || candidate.dayOfWeek in setOf(java.time.DayOfWeek.SATURDAY, java.time.DayOfWeek.SUNDAY)) candidate = candidate.plusDays(1)
        return candidate // A proposal only; an unknown year still requires confirmation.
    }
    fun adjust(date: LocalDate): LocalDate? = runCatching { FinancialPlanning.nextBusinessDay(date, dates, setOf(2026)) }.getOrNull()
}
