package br.com.yurimedeiros.meufinanceiro.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Light = lightColorScheme(
    primary = Color(0xFF146B63),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD6EEE7),
    onPrimaryContainer = Color(0xFF103E37),
    secondary = Color(0xFF526660),
    background = Color(0xFFF6F8F7),
    surface = Color(0xFFF6F8F7),
    surfaceContainer = Color(0xFFEBF0ED),
    surfaceContainerLow = Color(0xFFFFFFFF),
    onSurface = Color(0xFF192D29),
    onSurfaceVariant = Color(0xFF586B65),
    outlineVariant = Color(0xFFDCE5E0),
    error = Color(0xFFB3261E),
)
private val Dark = darkColorScheme(
    primary = Color(0xFF92D5C7),
    onPrimary = Color(0xFF003830),
    primaryContainer = Color(0xFF174E43),
    onPrimaryContainer = Color(0xFFCCF2E6),
    secondary = Color(0xFFB3CCC1),
    background = Color(0xFF101817),
    surface = Color(0xFF101817),
    surfaceContainer = Color(0xFF1D2B27),
    surfaceContainerLow = Color(0xFF17231F),
    onSurface = Color(0xFFE1EEE7),
    onSurfaceVariant = Color(0xFFB2C6BB),
    outlineVariant = Color(0xFF364B41),
    error = Color(0xFFFFB4AB),
)

@Composable
internal fun FinanceTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) Dark else Light, content = content)
}
