package br.com.yurimedeiros.meufinanceiro.security

import org.junit.Assert.*
import org.junit.Test

class SessionDraftCacheTest {
    @Test fun exactFiveMinutesAfterAutomaticLockDiscardsDrafts() {
        val discarded = mutableListOf<String>()
        val cache = SessionDraftCache<String> { discarded += it }
        cache.getOrPut { "formulário preenchido" }
        cache.background(10_000, 60_000)
        assertFalse(cache.expire(369_999))
        assertEquals(1L, cache.remaining(369_999))
        assertEquals("formulário preenchido", cache.value)
        assertTrue(cache.expire(370_000))
        assertNull(cache.value)
        assertEquals(listOf("formulário preenchido"), discarded)
        assertEquals(1L, cache.epoch)
    }

    @Test fun repeatedBackgroundAndFailedUnlockDoNotRenewTheCache() {
        val cache = SessionDraftCache<String>()
        cache.getOrPut { "rascunho" }
        cache.background(10_000, 60_000)
        cache.background(200_000, 60_000)
        assertTrue(cache.expire(370_000))
        assertNull(cache.value)
    }

    @Test fun unlockBeforeDeadlineResumesTheSameDraftWithoutRecreatingIt() {
        val cache = SessionDraftCache<Any>()
        val draft = cache.getOrPut { Any() }
        cache.background(10_000, 60_000)
        cache.resume(100_000)
        assertSame(draft, cache.getOrPut { error("Must restore the draft") })
        // Once active, this is the live form rather than a cached, locked draft.
        assertFalse(cache.expire(500_000))
    }

    @Test fun unlockAtOrAfterDeadlineCannotRecoverAnExpiredDraft() {
        for (now in listOf(370_000L, 900_000L)) {
            val cache = SessionDraftCache<String>()
            cache.getOrPut { "antigo" }
            cache.background(10_000, 60_000)
            cache.resume(now)
            assertEquals("novo", cache.getOrPut { "novo" })
        }
    }

    @Test fun briefBackgroundReturnAllowsANewIndependentWindow() {
        val cache = SessionDraftCache<String>()
        cache.getOrPut { "rascunho" }
        cache.background(10_000, 60_000)
        cache.resume(30_000)
        cache.background(100_000, 60_000)
        assertFalse(cache.expire(370_000))
        assertTrue(cache.expire(460_000))
    }

    @Test fun manualLockDiscardsImmediatelyAndCannotBeUndoneByUnlocking() {
        val discarded = mutableListOf<String>()
        val cache = SessionDraftCache<String> { discarded += it }
        cache.getOrPut { "antigo" }
        cache.background(10_000, 60_000)
        cache.clear()
        cache.resume(20_000)
        assertNull(cache.value)
        assertEquals(listOf("antigo"), discarded)
        assertEquals("novo", cache.getOrPut { "novo" })
    }

    @Test fun delayedTimerCannotExtendTheWindowWhenReturningFromBackground() {
        val cache = SessionDraftCache<String>()
        cache.getOrPut { "rascunho" }
        cache.background(10_000, 60_000)
        // Android may suspend timer execution: checking on foreground must still expire.
        assertTrue(cache.expire(1_000_000))
        assertNull(cache.value)
        assertFalse(cache.expire(1_000_001))
    }

    @Test fun monotonicClockRegressionDiscardsRatherThanExtendingTheWindow() {
        val cache = SessionDraftCache<String>()
        cache.getOrPut { "rascunho" }
        cache.background(10_000, 60_000)
        assertTrue(cache.expire(9_999))
        assertNull(cache.value)
    }
}
