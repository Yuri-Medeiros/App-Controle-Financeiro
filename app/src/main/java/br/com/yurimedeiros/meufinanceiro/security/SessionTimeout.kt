package br.com.yurimedeiros.meufinanceiro.security

/** Monotonic time only: changing the wall clock must not extend a session. */
internal class SessionTimeout(private val timeoutMillis: Long = 60_000L) {
    private var backgroundedAt: Long? = null

    fun background(now: Long) {
        if (backgroundedAt == null) backgroundedAt = now
    }

    fun expired(now: Long): Boolean = backgroundedAt?.let {
        now < it || now - it >= timeoutMillis
    } ?: false

    fun remaining(now: Long): Long = backgroundedAt?.let {
        if (now < it) 0L else (timeoutMillis - (now - it)).coerceAtLeast(0L)
    } ?: timeoutMillis

    fun foreground() { backgroundedAt = null }
}
