package br.com.yurimedeiros.meufinanceiro.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

internal val FinanceInputShape = RoundedCornerShape(16.dp)

// A identidade visual permanece escura, independentemente do tema do aparelho.
private val NeonNight = darkColorScheme(
    primary = Color(0xFF00E5D0),
    onPrimary = Color(0xFF04262E),
    primaryContainer = Color(0xFF164563),
    onPrimaryContainer = Color(0xFFF7F0FF),
    secondary = Color(0xFF00ACDA),
    onSecondary = Color(0xFF052F38),
    secondaryContainer = Color(0xFF233D59),
    onSecondaryContainer = Color(0xFFD2F6FF),
    tertiary = Color(0xFF923AFC),
    background = Color(0xFF070D20),
    onBackground = Color(0xFFF6F3FF),
    surface = Color(0xFF111124),
    surfaceContainer = Color(0xFF23223D),
    surfaceContainerLow = Color(0xFF1B1C32),
    surfaceContainerHigh = Color(0xFF2B2947),
    onSurface = Color(0xFFF6F3FF),
    onSurfaceVariant = Color(0xFFCCD8E8),
    outline = Color(0xFF8298B3),
    outlineVariant = Color(0xFF344D68),
    error = Color(0xFFFF8D9C),
    onError = Color(0xFF590D23),
)

@Composable
internal fun FinanceTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = NeonNight,
        shapes = Shapes(
            extraSmall = FinanceInputShape,
            small = RoundedCornerShape(12.dp),
            medium = FinanceInputShape,
            large = RoundedCornerShape(20.dp),
        ),
        content = content,
    )
}
