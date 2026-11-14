package br.com.yurimedeiros.meufinanceiro.formatting

import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle
import java.util.Locale

/** Brazilian presentation and input only. Persistence and external protocols keep their original formats. */
internal object AppDates {
    private val locale = Locale.forLanguageTag("pt-BR")
    private val day = DateTimeFormatter.ofPattern("dd/MM/uuuu", locale).withResolverStyle(ResolverStyle.STRICT)
    private val period = DateTimeFormatter.ofPattern("MM/uuuu", locale)
    private val time = DateTimeFormatter.ofPattern("dd/MM/uuuu HH:mm", locale)

    fun date(value: LocalDate): String = value.format(day)
    fun month(value: YearMonth): String = value.format(period)
    fun parse(text: String): LocalDate {
        val value = text.trim()
        require(Regex("[0-9]{2}/[0-9]{2}/[0-9]{4}").matches(value)) { "Informe a data como DD/MM/AAAA." }
        return LocalDate.parse(value, day).also { require(it.year in 1..9999) { "Informe um ano válido." } }
    }
    fun storedDate(value: String): String = runCatching { date(LocalDate.parse(value)) }.getOrDefault("Data indisponível")
    fun timestamp(value: Instant, zone: ZoneId = ZoneId.systemDefault()): String = time.format(value.atZone(zone))
    fun timestamp(value: String, zone: ZoneId = ZoneId.systemDefault()): String =
        runCatching { timestamp(Instant.parse(value), zone) }.getOrDefault("Data indisponível")
    fun createdDate(epochMillis: Long, zone: ZoneId = ZoneId.systemDefault()): String = date(Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalDate())
}
