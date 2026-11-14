package br.com.yurimedeiros.meufinanceiro.security

import org.junit.Assert.*
import org.junit.Test

class ResumeWindowTest {
    @Test fun preservesDraftAfterAuthenticationTimeoutButExpiresAtFiveMinutes() {
        val window = ResumeWindow(); window.start(1000)
        assertEquals(240000L, window.remaining(61000))
        assertEquals(1L, window.remaining(300999))
        assertEquals(0L, window.remaining(301000))
    }
    @Test fun repeatedBackgroundDoesNotExtendCacheAndClockRegressionExpiresIt() {
        val window = ResumeWindow(); window.start(1000); window.start(61000)
        assertEquals(0L, window.remaining(301000))
        assertEquals(0L, window.remaining(999))
        window.clear(); assertEquals(0L, window.remaining(1000))
        window.start(400000); assertEquals(300000L, window.remaining(400000))
    }
}
