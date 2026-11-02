package br.com.yurimedeiros.meufinanceiro.security

/** Process-memory drafts only. Deadlines use elapsedRealtime, never the wall clock. */
internal class SessionDraftCache<T>(private val discard: (T) -> Unit = {}) {
    companion object { const val MAX_LOCKED_MILLIS = 300_000L }

    var value: T? = null
        private set
    var epoch: Long = 0
        private set
    private var startedAt: Long? = null
    private var expiresAt: Long? = null

    fun getOrPut(create: () -> T): T = value ?: create().also { value = it }

    // Capture on backgrounding, allowing the existing one-minute session grace period.
    // Repeated lifecycle callbacks and failed authentication must not extend this deadline.
    fun background(now: Long, untilLock: Long) {
        if (expiresAt == null) {
            startedAt = now
            expiresAt = now + untilLock + MAX_LOCKED_MILLIS
        }
    }

    fun remaining(now: Long): Long = expiresAt?.let {
        if (now < checkNotNull(startedAt)) 0L else (it - now).coerceAtLeast(0L)
    } ?: 0L

    fun expire(now: Long): Boolean {
        if (expiresAt != null && remaining(now) == 0L) {
            clear()
            return true
        }
        return false
    }

    fun resume(now: Long) {
        expire(now)
        startedAt = null
        expiresAt = null
    }

    fun clear() {
        value?.let(discard)
        value = null
        startedAt = null
        expiresAt = null
        epoch++
    }
}
