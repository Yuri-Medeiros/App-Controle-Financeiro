package br.com.yurimedeiros.meufinanceiro.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.yurimedeiros.meufinanceiro.R

private val WelcomeBackground = Color(0xFF080B17)
private val WelcomeLavender = Color(0xFFC4BFFF)
private val WelcomeInk = Color(0xFF111329)
private val WelcomeText = Color(0xFFFAFAFC)

@Composable
internal fun LockedScreen(
    state: FinanceUiState,
    deviceSecure: Boolean,
    onUnlock: () -> Unit,
    onSecuritySettings: () -> Unit,
) {
    Box(Modifier.fillMaxSize().background(WelcomeBackground)) {
        WelcomeBackdrop()
        Scaffold(containerColor = Color.Transparent) { insets ->
            BoxWithConstraints(Modifier.fillMaxSize().padding(insets).padding(horizontal = 28.dp)) {
                val titleSize = (maxWidth.value * 0.15f).sp
                val heroHeight = (maxHeight * 0.43f).coerceIn(180.dp, 420.dp)
                // Fill the safe viewport and distribute space between header, hero and CTA.
                // If content grows beyond it, scroll rather than overlap or clip controls.
                Column(
                    Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                        .heightIn(min = maxHeight).padding(top = 20.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(
                        Modifier.fillMaxWidth().padding(bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(28.dp),
                    ) {
                        Text(
                            "MEU FINANCEIRO",
                            color = WelcomeText,
                            fontSize = 11.sp,
                            letterSpacing = 2.5.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            buildAnnotatedString {
                                withStyle(SpanStyle(color = Color(0xFFF3F1F7))) {
                                    append("Seu dinheiro.\n")
                                }
                                withStyle(SpanStyle(color = Color(0xFFB7B4EF))) {
                                    append("Seu espaço.")
                                }
                            },
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
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        state.error?.let {
                            Text(it, color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodyMedium)
                        }
                        Button(
                            onClick = if (deviceSecure) onUnlock else onSecuritySettings,
                            enabled = !state.opening,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 45.56.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = WelcomeLavender,
                                contentColor = WelcomeInk,
                                disabledContainerColor = WelcomeLavender,
                                disabledContentColor = WelcomeInk,
                            ),
                            contentPadding = PaddingValues(horizontal = 22.dp, vertical = 9.dp),
                        ) {
                            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                Text(
                                    when {
                                        state.opening -> "Abrindo…"
                                        deviceSecure -> "Acessar"
                                        else -> "Configurar bloqueio do Android"
                                    },
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 34.dp),
                                    textAlign = TextAlign.Center,
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                if (state.opening) {
                                    CircularProgressIndicator(Modifier.align(Alignment.CenterEnd).size(24.dp), color = WelcomeInk,
                                        strokeWidth = 2.dp)
                                } else {
                                    Canvas(Modifier.align(Alignment.CenterEnd).size(26.dp)) {
                                        val stroke = 2.dp.toPx()
                                        drawLine(WelcomeInk, Offset(0f, size.height / 2),
                                            Offset(size.width, size.height / 2), stroke, StrokeCap.Round)
                                        drawLine(WelcomeInk, Offset(size.width * 0.60f, size.height * 0.12f),
                                            Offset(size.width, size.height / 2), stroke, StrokeCap.Round)
                                        drawLine(WelcomeInk, Offset(size.width, size.height / 2),
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
}

@Composable
private fun WelcomeBackdrop() {
    // Only decorative geometry is blurred; text, the illustration and CTA stay crisp.
    Canvas(Modifier.fillMaxSize().blur(40.dp)) {
        drawOval(
            color = Color(0xFF5940FF).copy(alpha = 0.26f),
            topLeft = Offset(size.width * 0.72f, -size.height * 0.08f),
            size = Size(size.width * 0.65f, size.height * 0.24f),
        )
        drawOval(
            color = Color(0xFF00A5C4).copy(alpha = 0.16f),
            topLeft = Offset(-size.width * 0.30f, size.height * 0.32f),
            size = Size(size.width * 0.45f, size.height * 0.22f),
        )
        drawOval(
            color = Color(0xFF4939DA).copy(alpha = 0.22f),
            topLeft = Offset(size.width * 0.85f, size.height * 0.80f),
            size = Size(size.width * 0.45f, size.height * 0.27f),
        )
    }
}
