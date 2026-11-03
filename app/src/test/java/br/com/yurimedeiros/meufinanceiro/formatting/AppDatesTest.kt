package br.com.yurimedeiros.meufinanceiro.formatting

import br.com.yurimedeiros.meufinanceiro.assets.*
import br.com.yurimedeiros.meufinanceiro.ledger.*
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

class AppDatesTest {
    @Test fun parsesBrazilianDatesWithoutSwappingDayAndMonth() {
        assertEquals(LocalDate.of(2026,2,1), AppDates.parse("01/02/2026"))
        assertEquals(LocalDate.of(2024,2,29), AppDates.parse(" 29/02/2024 "))
        assertEquals("03/11/2026", AppDates.date(LocalDate.of(2026,11,3)))
    }
    @Test fun rejectsImpossibleDatesAndNonBrazilianInputInsteadOfSilentlyAdjusting() {
        for (text in listOf("29/02/2026", "31/04/2026", "00/11/2026", "01/13/2026", "2026-11-03", "3/11/2026", "03/11/26", "01/01/0000", "")) {
            assertTrue(text, runCatching { AppDates.parse(text) }.isFailure)
        }
    }
    @Test fun brazilianInputPersistsAsIsoAndReopensWithSameDay() {
        val date = AppDates.parse("03/11/2026")
        val action = LedgerAction.Asset(AssetChange.Register(AssetPosition("a", "Investimento", AssetKind.INVESTMENT, 10000, 0, date)))
        val encoded = LedgerCodec.encode(action)
        assertTrue(encoded.contains("2026-11-03"))
        val restored = FinanceLedger.replay(listOf(LedgerCodec.decode(encoded))).assets.assets.single()
        assertEquals("03/11/2026", AppDates.date(restored.confirmedDate))
        assertEquals("03/11/2026", AppDates.storedDate("2026-11-03"))
    }
    @Test fun timestampUsesLocalCalendarDateAndKeepsTimeForBackupsAndQuotes() {
        val instant = Instant.parse("2026-11-03T01:10:00Z")
        val zone = ZoneId.of("America/Sao_Paulo")
        assertEquals("02/11/2026 22:10", AppDates.timestamp(instant, zone))
        assertEquals("02/11/2026 22:10", AppDates.timestamp(instant.toString(), zone))
        assertEquals("02/11/2026", AppDates.createdDate(instant.toEpochMilli(), zone))
        assertEquals("Data indisponível", AppDates.timestamp("invalid", zone))
    }
    @Test fun monthlyReferencesHaveBrazilianOrderWithoutInventingADay() {
        val period = YearMonth.of(2026,11)
        assertEquals("11/2026", AppDates.month(period))
        assertEquals("2026-11", period.toString())
    }
}
