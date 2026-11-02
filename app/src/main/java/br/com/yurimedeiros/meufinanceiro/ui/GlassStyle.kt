package br.com.yurimedeiros.meufinanceiro.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.unit.dp

internal val NeonPalette = listOf(Color(0xFF00E5D0), Color(0xFF00ACDA), Color(0xFF0152B3), Color(0xFF5121D2), Color(0xFF923AFC))
private val NeonStops = listOf(0f, 0.25f, 0.55f, 0.80f, 1f)

// CSS 135deg: upper left to lower right, with the same stops on any aspect ratio.
internal fun neonGradient(alpha: Float = 1f): Brush = object : ShaderBrush() {
    override fun createShader(size: Size): Shader {
        val halfSpan = (size.width + size.height) / 4f
        val center = Offset(size.width / 2f, size.height / 2f)
        return LinearGradientShader(
            from = center - Offset(halfSpan, halfSpan),
            to = center + Offset(halfSpan, halfSpan),
            colors = NeonPalette.map { it.copy(alpha = alpha) }, colorStops = NeonStops,
        )
    }
}

internal val GlassBorder: Brush = SolidColor(Color(0xFF8298B3).copy(alpha = 0.65f))

internal fun Modifier.glassSurface(highlighted: Boolean = false): Modifier =
    background(Color(0xFF080F24).copy(alpha = if (highlighted) 0.58f else 0.72f))
        .background(neonGradient(if (highlighted) 0.32f else 0.13f))
        .background(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.12f), Color.White.copy(alpha = 0.02f))))

@Composable
internal fun GlassButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, content: @Composable RowScope.() -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Button(
        onClick = onClick, enabled = enabled, modifier = modifier.background(neonGradient(if (enabled) 0.32f else 0.06f), shape),
        shape = shape, border = BorderStroke(1.dp, GlassBorder),
        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent, contentColor = Color.White,
            disabledContainerColor = Color.White.copy(alpha = 0.04f), disabledContentColor = Color.White.copy(alpha = 0.45f)),
    ) {
        // The content stays sharp; translucency and highlights belong to the button surface.
        content()
    }
}
