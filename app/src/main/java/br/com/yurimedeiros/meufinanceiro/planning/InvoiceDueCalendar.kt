package br.com.yurimedeiros.meufinanceiro.planning

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

/** Recurring bank/SP/Ribeirão Preto rules, calculated offline for each invoice year. */
internal object InvoiceDueCalendar {
    const val BANK_SOURCE = "https://feriadosbancarios.febraban.org.br/"
    const val MUNICIPAL_SOURCE = "https://www.ribeiraopreto.sp.gov.br/legislacao-municipal/pesquisa/lei/32092"
    const val SOURCE_DATE = "2026-11-14"

    fun due(reference: YearMonth, explicit: LocalDate? = null): LocalDate = nextBusinessDay(explicit ?: reference.atDay(8))

    fun isBusinessDay(date: LocalDate): Boolean = date.dayOfWeek !in setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY) && date !in holidays(date.year)

    fun nextBusinessDay(date: LocalDate): LocalDate {
        var result = date
        while (!isBusinessDay(result)) result = result.plusDays(1)
        return result
    }

    internal fun holidays(year: Int): Set<LocalDate> {
        val easter = easter(year)
        var lastBankDay = LocalDate.of(year, 12, 31)
        while (lastBankDay.dayOfWeek in setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)) lastBankDay = lastBankDay.minusDays(1)
        return setOf("01-01", "01-20", "04-21", "05-01", "06-19", "07-09", "09-07", "10-12", "11-02", "11-15", "11-20", "12-25")
            .map { LocalDate.of(year, it.substringBefore('-').toInt(), it.substringAfter('-').toInt()) }.toSet() +
            setOf(easter.minusDays(48), easter.minusDays(47), easter.minusDays(2), easter.plusDays(60), lastBankDay)
    }

    // Gregorian Meeus/Jones/Butcher computus. Dates are rules, not a claim that a
    // future annual calendar or exceptional closure has already been published.
    private fun easter(year: Int): LocalDate {
        val a = Math.floorMod(year, 19)
        val b = Math.floorDiv(year, 100)
        val c = Math.floorMod(year, 100)
        val d = Math.floorDiv(b, 4)
        val e = Math.floorMod(b, 4)
        val f = Math.floorDiv(b + 8, 25)
        val g = Math.floorDiv(b - f + 1, 3)
        val h = Math.floorMod(19 * a + b - d - g + 15, 30)
        val i = Math.floorDiv(c, 4)
        val k = Math.floorMod(c, 4)
        val l = Math.floorMod(32 + 2 * e + 2 * i - h - k, 7)
        val m = Math.floorDiv(a + 11 * h + 22 * l, 451)
        val n = h + l - 7 * m + 114
        return LocalDate.of(year, n / 31, n % 31 + 1)
    }
}
