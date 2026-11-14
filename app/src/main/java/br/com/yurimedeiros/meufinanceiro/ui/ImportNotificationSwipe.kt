package br.com.yurimedeiros.meufinanceiro.ui

import kotlin.math.abs
import kotlin.math.max

internal object ImportNotificationSwipe {
    fun shouldDismiss(x: Float, y: Float, width: Float, minimum: Float): Boolean =
        abs(x) >= max(minimum, width * .25f) || y <= -minimum
}
