package br.com.yurimedeiros.meufinanceiro.planning

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class InvoiceDueCalendarTest {
    @Test fun dueMovesPastWeekendsAndMovableHolidaysAcrossYears() {
        mapOf("2026-11" to "2026-11-09", "2026-12" to "2026-12-08",
            "2027-02" to "2027-02-10", "2027-05" to "2027-05-10",
            "2023-06" to "2023-06-09").forEach { (month, expected) ->
            assertEquals(LocalDate.parse(expected), InvoiceDueCalendar.due(YearMonth.parse(month)))
        }
    }
    @Test fun localNationalAndYearEndClosuresAreSkipped() {
        mapOf("2026-01-20" to "2026-01-21", "2026-06-19" to "2026-06-22",
            "2026-07-09" to "2026-07-10", "2026-11-20" to "2026-11-23",
            "2026-12-31" to "2027-01-04").forEach { (date, expected) ->
            assertEquals(LocalDate.parse(expected), InvoiceDueCalendar.nextBusinessDay(LocalDate.parse(date)))
        }
        assertTrue(InvoiceDueCalendar.isBusinessDay(LocalDate.of(2026, 6, 5)))
    }
}
