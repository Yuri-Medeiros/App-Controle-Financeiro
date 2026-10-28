package br.com.yurimedeiros.meufinanceiro.importing

import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle
import java.util.UUID

internal enum class DocumentKind { NUBANK_ACCOUNT, FLASH, NUBANK_CARD }

/** Values retain the source sign: positive card values are charges, not account income. */
internal data class ImportedRow(
    val kind: DocumentKind,
    val date: LocalDate,
    val description: String,
    val cents: Long,
    val sourceId: String? = null,
    val time: LocalTime? = null,
    val paymentMethod: String? = null,
    val balanceCents: Long? = null,
    val statement: LocalDate? = null,
)

internal data class ParsedDocument(
    val kind: DocumentKind,
    val rows: List<ImportedRow>,
    // A proposal from the filename only. The caller must obtain confirmation before first import.
    val proposedStatement: LocalDate? = null,
)

/** Validates the entire document before returning any importable rows. No database writes. */
internal object FinancialCsv {
    private val brDate = DateTimeFormatter.ofPattern("dd/MM/uuuu").withResolverStyle(ResolverStyle.STRICT)
    private val accountHeaders = listOf("Data", "Valor", "Identificador", "Descrição")
    private val flashHeaders = listOf("Data", "Hora", "Movimentação", "Valor", "Meio de Pagamento", "Saldo")
    private val cardHeaders = listOf("date", "title", "amount")

    fun parse(bytes: ByteArray, kind: DocumentKind, filename: String): ParsedDocument {
        require(bytes.size <= 10 * 1024 * 1024) { "Arquivo grande demais para esta importação." }
        val decoder = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        val text = try { decoder.decode(ByteBuffer.wrap(bytes)).toString().removePrefix("\uFEFF") }
        catch (_: java.nio.charset.CharacterCodingException) { throw IllegalArgumentException("CSV não está em UTF-8 válido.") }
        val records = CsvRecords.read(text)
        val expected = when (kind) {
            DocumentKind.NUBANK_ACCOUNT -> accountHeaders
            DocumentKind.FLASH -> flashHeaders
            DocumentKind.NUBANK_CARD -> cardHeaders
        }
        require(records.isNotEmpty() && records.first() == expected) { "Cabeçalho incompatível com o documento selecionado." }
        val reference = if (kind == DocumentKind.NUBANK_CARD) {
            Regex("Nubank_(\\d{4}-\\d{2}-\\d{2})\\.csv").matchEntire(filename)?.groupValues?.get(1)?.let(LocalDate::parse)
        } else null
        val rows = records.drop(1).mapIndexed { index, fields ->
            try {
                require(fields.size == expected.size)
                when (kind) {
                    DocumentKind.NUBANK_ACCOUNT -> {
                        require(fields[2].isNotBlank() && UUID.fromString(fields[2]).toString() == fields[2].lowercase())
                        ImportedRow(kind, LocalDate.parse(fields[0], brDate), nonEmpty(fields[3]),
                            decimalCents(fields[1], brazilian = false), sourceId = fields[2].lowercase())
                    }
                    DocumentKind.FLASH -> ImportedRow(kind, LocalDate.parse(fields[0], brDate), nonEmpty(fields[2]),
                        decimalCents(fields[3], brazilian = true, currency = true),
                        time = LocalTime.parse(fields[1], DateTimeFormatter.ofPattern("HH:mm").withResolverStyle(ResolverStyle.STRICT)),
                        paymentMethod = nonEmpty(fields[4]), balanceCents = decimalCents(fields[5], brazilian = true, currency = true))
                    DocumentKind.NUBANK_CARD -> ImportedRow(kind, LocalDate.parse(fields[0]), nonEmpty(fields[1]),
                        decimalCents(fields[2], brazilian = true))
                }
            } catch (_: IllegalArgumentException) {
                throw IllegalArgumentException("Registro ${index + 1}: campos inválidos; nenhum registro deste arquivo foi importado.")
            } catch (_: java.time.DateTimeException) {
                throw IllegalArgumentException("Registro ${index + 1}: data ou hora inválida; nenhum registro deste arquivo foi importado.")
            } catch (_: ArithmeticException) {
                throw IllegalArgumentException("Registro ${index + 1}: valor monetário fora do limite; nenhum registro deste arquivo foi importado.")
            }
        }
        return ParsedDocument(kind, rows, reference)
    }

    private fun nonEmpty(value: String): String = value.also { require(it.isNotBlank()) }

    private fun decimalCents(raw: String, brazilian: Boolean, currency: Boolean = false): Long {
        var value = raw.trim().replace('\u00A0', ' ')
        if (currency) value = value.replace("R$", "").trim()
        // The supplied card exports use '- 2.980,26'. No other internal spaces are accepted.
        value = value.replace(Regex("^([+-]) +"), "$1")
        val pattern = if (brazilian) "[+-]?(?:[0-9]+|[0-9]{1,3}(?:\\.[0-9]{3})+),[0-9]{2}"
            else "[+-]?[0-9]+\\.[0-9]{2}"
        require(Regex(pattern).matches(value))
        if (brazilian) value = value.replace(".", "").replace(',', '.')
        return value.toBigDecimal().movePointRight(2).longValueExact()
    }
}

/** Comma CSV with quoted commas, escaped quotes and embedded newlines; rejects truncated input. */
internal object CsvRecords {
    fun read(text: String): List<List<String>> {
        val result = mutableListOf<List<String>>()
        val fields = mutableListOf<String>()
        val cell = StringBuilder()
        var quoted = false
        var closedQuote = false
        var started = false
        var i = 0
        fun finishField() { fields += cell.toString(); cell.setLength(0); closedQuote = false; started = false }
        fun finishRow() { finishField(); result += fields.toList(); fields.clear() }
        while (i < text.length) {
            val c = text[i]
            if (quoted) {
                if (c == '"') {
                    if (i + 1 < text.length && text[i + 1] == '"') { cell.append('"'); i++ }
                    else { quoted = false; closedQuote = true }
                } else cell.append(c)
            } else when (c) {
                '"' -> { require(!started && cell.isEmpty() && !closedQuote) { "Aspas inválidas no CSV." }; quoted = true; started = true }
                ',' -> finishField()
                '\r', '\n' -> { finishRow(); if (c == '\r' && i + 1 < text.length && text[i + 1] == '\n') i++ }
                else -> { require(!closedQuote) { "Texto após fechamento de aspas no CSV." }; cell.append(c); started = true }
            }
            i++
        }
        require(!quoted) { "CSV incompleto: aspas não foram fechadas." }
        if (started || closedQuote || cell.isNotEmpty() || fields.isNotEmpty()) finishRow()
        return result
    }
}
