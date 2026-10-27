package br.com.yurimedeiros.meufinanceiro.security

import org.junit.Assert.*
import org.junit.Test

class SessionTimeoutTest {
    @Test fun foregroundDoesNotExpire() {
        assertFalse(SessionTimeout().expired(Long.MAX_VALUE))
    }

    @Test fun exactOneMinuteBoundaryRequiresAuthentication() {
        val policy = SessionTimeout()
        policy.background(10_000)
        assertFalse(policy.expired(69_999))
        assertEquals(1L, policy.remaining(69_999))
        assertTrue(policy.expired(70_000))
        assertEquals(0L, policy.remaining(70_000))
    }

    @Test fun repeatedBackgroundSignalsCannotExtendTheDeadline() {
        val policy = SessionTimeout()
        policy.background(10_000)
        policy.background(60_000)
        assertTrue(policy.expired(70_000))
    }

    @Test fun briefReturnStartsANewBackgroundInterval() {
        val policy = SessionTimeout()
        policy.background(10_000)
        assertFalse(policy.expired(30_000))
        policy.foreground()
        policy.background(50_000)
        assertFalse(policy.expired(109_999))
        assertTrue(policy.expired(110_000))
    }

    @Test fun backwardsMonotonicClockFailsClosed() {
        val policy = SessionTimeout()
        policy.background(10_000)
        assertTrue(policy.expired(9_999))
        assertEquals(0L, policy.remaining(9_999))
    }
}
