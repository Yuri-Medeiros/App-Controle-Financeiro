package br.com.yurimedeiros.meufinanceiro.security

/** The draft lifetime starts on leaving the app, independently from authentication timeout. */
internal class ResumeWindow {
    private var startedAt: Long? = null
    fun start(now: Long) { if (startedAt == null) startedAt = now }
    fun clear() { startedAt = null }
    fun remaining(now: Long): Long = startedAt?.let {
        if (now < it) 0 else (300_000L - (now - it)).coerceAtLeast(0)
    } ?: 0
}
