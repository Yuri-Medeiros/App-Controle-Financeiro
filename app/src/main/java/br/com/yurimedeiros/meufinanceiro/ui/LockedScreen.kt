package br.com.yurimedeiros.meufinanceiro.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.yurimedeiros.meufinanceiro.R

private val WelcomeBackground = Color(0xFF061222)
private val WelcomeViolet = Color(0xFF6554E8)
private val WelcomeText = Color(0xFFFAFAFC)

@Composable
internal fun LockedScreen(
    state: FinanceUiState,
    deviceSecure: Boolean,
    onUnlock: () -> Unit,
    onSecuritySettings: () -> Unit,
) {
    Scaffold(containerColor = WelcomeBackground) { insets ->
        BoxWithConstraints(Modifier.fillMaxSize().padding(insets).padding(horizontal = 28.dp)) {
            val titleSize = (maxWidth.value / 5.8f).coerceAtMost(76f).sp
            val heroHeight = (maxHeight * 0.43f).coerceIn(180.dp, 420.dp)
            // Fill the safe viewport and distribute space between header, hero and CTA.
            // If content grows beyond it, scroll rather than overlap or clip controls.
            Column(
                Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                    .heightIn(min = maxHeight).padding(top = 20.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(
                    Modifier.fillMaxWidth().padding(bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(28.dp),
                ) {
                    Canvas(Modifier.size(44.dp, 36.dp)) {
                        drawCircle(WelcomeViolet, radius = size.height * 0.47f,
                            center = Offset(size.height * 0.47f, size.height * 0.47f))
                        val center = Offset(size.width * 0.75f, size.height * 0.70f)
                        drawCircle(WelcomeBackground, radius = size.height * 0.32f, center = center)
                        drawCircle(Color(0xFF00CBE6), radius = size.height * 0.28f, center = center)
                    }
                    Text(
                        "Meu\nFinanceiro",
                        color = WelcomeText,
                        fontSize = titleSize,
                        lineHeight = titleSize * 0.95f,
                        letterSpacing = (-2).sp,
                        fontWeight = FontWeight.ExtraBold,
                    )
                }
                Column(
                    Modifier.fillMaxWidth().padding(bottom = 28.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    Image(
                        painterResource(R.drawable.welcome_piggy),
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxWidth().height(heroHeight),
                    )
                    Text(
                        "Seu dinheiro. Suas conquistas.",
                        color = Color(0xFFC6C5E5),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    state.error?.let {
                        Text(it, color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium)
                    }
                    Button(
                        onClick = if (deviceSecure) onUnlock else onSecuritySettings,
                        enabled = !state.opening,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 68.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = WelcomeViolet,
                            contentColor = WelcomeText,
                            disabledContainerColor = WelcomeViolet,
                            disabledContentColor = WelcomeText,
                        ),
                        contentPadding = PaddingValues(horizontal = 22.dp, vertical = 18.dp),
                    ) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                when {
                                    state.opening -> "Abrindo suas conquistas…"
                                    deviceSecure -> "Acessar"
                                    else -> "Configurar bloqueio do Android"
                                },
                                modifier = Modifier.weight(1f),
                                fontSize = 22.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                            if (state.opening) {
                                CircularProgressIndicator(Modifier.size(24.dp), color = WelcomeText,
                                    strokeWidth = 2.dp)
                            } else {
                                Canvas(Modifier.size(26.dp)) {
                                    val stroke = 2.dp.toPx()
                                    drawLine(WelcomeText, Offset(0f, size.height / 2),
                                        Offset(size.width, size.height / 2), stroke, StrokeCap.Round)
                                    drawLine(WelcomeText, Offset(size.width * 0.60f, size.height * 0.12f),
                                        Offset(size.width, size.height / 2), stroke, StrokeCap.Round)
                                    drawLine(WelcomeText, Offset(size.width, size.height / 2),
                                        Offset(size.width * 0.60f, size.height * 0.88f), stroke, StrokeCap.Round)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
