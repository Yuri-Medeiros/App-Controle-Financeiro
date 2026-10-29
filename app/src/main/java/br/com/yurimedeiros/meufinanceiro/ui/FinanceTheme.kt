package br.com.yurimedeiros.meufinanceiro.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

// A identidade visual permanece escura, independentemente do tema do aparelho.
private val PurpleNight = darkColorScheme(
    primary = Color(0xFFC2A1FF),
    onPrimary = Color(0xFF1B103B),
    primaryContainer = Color(0xFF5236A7),
    onPrimaryContainer = Color(0xFFF7F0FF),
    secondary = Color(0xFF64DDF0),
    onSecondary = Color(0xFF052F38),
    secondaryContainer = Color(0xFF233D59),
    onSecondaryContainer = Color(0xFFD2F6FF),
    tertiary = Color(0xFF53E0CE),
    background = Color(0xFF090C1C),
    onBackground = Color(0xFFF6F3FF),
    surface = Color(0xFF111124),
    surfaceContainer = Color(0xFF23223D),
    surfaceContainerLow = Color(0xFF1B1C32),
    surfaceContainerHigh = Color(0xFF2B2947),
    onSurface = Color(0xFFF6F3FF),
    onSurfaceVariant = Color(0xFFC5BFD9),
    outline = Color(0xFF78718F),
    outlineVariant = Color(0xFF38364E),
    error = Color(0xFFFF8D9C),
    onError = Color(0xFF590D23),
)

@Composable
internal fun FinanceTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = PurpleNight,
        shapes = Shapes(
            small = RoundedCornerShape(12.dp),
            medium = RoundedCornerShape(16.dp),
            large = RoundedCornerShape(20.dp),
        ),
        content = content,
    )
}
