package br.com.yurimedeiros.meufinanceiro.ui

import org.junit.Assert.*
import org.junit.Test

class ImportNotificationSwipeTest {
    @Test fun dismissesBothSidesAndUpAtTheGestureThreshold() {
        assertTrue(ImportNotificationSwipe.shouldDismiss(100f, 0f, 400f, 48f))
        assertTrue(ImportNotificationSwipe.shouldDismiss(-100f, 0f, 400f, 48f))
        assertTrue(ImportNotificationSwipe.shouldDismiss(0f, -48f, 400f, 48f))
        assertTrue(ImportNotificationSwipe.shouldDismiss(70f, -60f, 400f, 48f))
    }
    @Test fun downAndSmallMovementsDoNotDismiss() {
        assertFalse(ImportNotificationSwipe.shouldDismiss(0f, 200f, 400f, 48f))
        assertFalse(ImportNotificationSwipe.shouldDismiss(99f, -47f, 400f, 48f))
        assertFalse(ImportNotificationSwipe.shouldDismiss(-99f, 0f, 400f, 48f))
        assertFalse(ImportNotificationSwipe.shouldDismiss(20f, 0f, 40f, 48f))
    }
}
