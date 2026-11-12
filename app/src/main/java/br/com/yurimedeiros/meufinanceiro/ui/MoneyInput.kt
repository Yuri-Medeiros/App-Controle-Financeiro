package br.com.yurimedeiros.meufinanceiro.ui

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation

private val plainMoney = Regex("[+-]?[0-9]*(?:,[0-9]*)?")
private val dotDecimalMoney = Regex("[+-]?[0-9]*\\.[0-9]*")
private val groupedBrazilianMoney = Regex("[+-]?[0-9]{1,3}(?:\\.[0-9]{3})+(?:,[0-9]*)?")
private val groupedAmericanMoney = Regex("[+-]?[0-9]{1,3}(?:,[0-9]{3})+\\.[0-9]*")

/** Keep a decimal string in the form state; grouping never changes cents or rounds input. */
internal fun normalizeMoneyInput(input: String): String? {
    val text = input.trim().replace("R$", "").filterNot { it.isWhitespace() }
    return when {
        groupedBrazilianMoney.matches(text) -> text.replace(".", "")
        groupedAmericanMoney.matches(text) -> text.replace(",", "").replace('.', ',')
        plainMoney.matches(text) -> text
        dotDecimalMoney.matches(text) -> text.replace('.', ',')
        else -> null
    }
}

internal object MoneyInputVisualTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val raw = text.text
        if (!plainMoney.matches(raw)) return VisualTransformation.None.filter(text)
        val integerEnd = raw.indexOf(',').let { if (it < 0) raw.length else it }
        val integerStart = if (raw.startsWith('-') || raw.startsWith('+')) 1 else 0
        val output = StringBuilder()
        val forward = IntArray(raw.length + 1)
        val backward = mutableListOf(0)
        raw.forEachIndexed { index, char ->
            if (index > integerStart && index < integerEnd && (integerEnd - index) % 3 == 0) {
                output.append('.')
                backward += index
            }
            forward[index] = output.length
            output.append(char)
            backward += index + 1
        }
        forward[raw.length] = output.length
        return TransformedText(AnnotatedString(output.toString()), object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int = forward[offset.coerceIn(0, raw.length)]
            override fun transformedToOriginal(offset: Int): Int = backward[offset.coerceIn(0, output.length)]
        })
    }
}
