package br.com.yurimedeiros.meufinanceiro.backup

import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class BackupRetentionTest {
    @Test fun keepsNewestPerDayWithinFifteenDaysAndMostRecent() {
        val today = LocalDate.of(2026, 10, 30)
        val files = (0..20).flatMap { age -> (1..3).map { hour ->
            DriveBackupFile("$age-$hour", Instant.parse("${today.minusDays(age.toLong())}T0$hour:00:00Z"), 100)
        } }
        val keep = BackupRetention.keep(files, today)
        assertEquals(15, keep.size)
        assertTrue((0..14).all { "$it-3" in keep })
        assertTrue(keep.none { it.startsWith("15-") })
    }
    @Test fun latestSurvivesEvenWhenNoBackupIsRecent() {
        val old = DriveBackupFile("old", Instant.parse("2026-01-01T00:00:00Z"), 10)
        assertEquals(setOf("old"), BackupRetention.keep(listOf(old), LocalDate.of(2026, 10, 30)))
        assertTrue(BackupRetention.keep(emptyList(), LocalDate.now()).isEmpty())
    }
    @Test fun gapsDoNotGenerateDailyVersions() {
        val a = DriveBackupFile("a", Instant.parse("2026-10-30T12:00:00Z"), 10)
        val b = DriveBackupFile("b", Instant.parse("2026-10-25T12:00:00Z"), 10)
        assertEquals(setOf("a", "b"), BackupRetention.keep(listOf(b, a), LocalDate.of(2026, 10, 30)))
    }
}
