package com.dancaai.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import com.dancaai.app.ui.theme.DcaTheme
import com.dancaai.app.ui.theme.UiFontFamily

/** Logomarca: quadrado de acento com a figura de um dançarino em movimento. */
@Composable
fun BrandMark(size: Int = 56, modifier: Modifier = Modifier) {
    val accent = DcaTheme.colors.accent
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size.dp)
            .clip(RoundedCornerShape((size * 0.28f).dp))
            .background(accent),
    ) {
        Canvas(modifier = Modifier.size((size * 0.62f).dp)) {
            val s = this.size.width / 24f // escala do viewBox 24x24
            fun p(x: Float, y: Float) = Offset(x * s, y * s)
            val stroke = Stroke(width = 2.4f * s, cap = StrokeCap.Round)

            // cabeça
            drawCircle(color = Color.White, radius = 1.8f * s, center = p(12f, 4.5f))
            // coluna
            drawLine(Color.White, p(12f, 6.5f), p(12f, 12.5f), strokeWidth = 2.4f * s, cap = StrokeCap.Round)
            // braços: 6,10 → 12,12.5 → 18,8
            drawPath(
                Path().apply { moveTo(6f * s, 10f * s); lineTo(12f * s, 12.5f * s); lineTo(18f * s, 8f * s) },
                color = Color.White, style = stroke,
            )
            // pernas
            drawLine(Color.White, p(12f, 12.5f), p(9f, 20f), strokeWidth = 2.4f * s, cap = StrokeCap.Round)
            drawLine(Color.White, p(12f, 12.5f), p(16f, 20f), strokeWidth = 2.4f * s, cap = StrokeCap.Round)
        }
    }
}

/** Logo completa: marca + "Dança AI" (AI em acento, peso leve). */
@Composable
fun Logo(size: Int = 56, modifier: Modifier = Modifier) {
    val colors = DcaTheme.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier,
    ) {
        BrandMark(size = size)
        Text(
            text = buildAnnotatedString {
                withStyle(SpanStyle(color = colors.onSurfaceStrong, fontWeight = FontWeight.Bold)) {
                    append("Dança ")
                }
                withStyle(SpanStyle(color = colors.accent, fontWeight = FontWeight.Light)) {
                    append("AI")
                }
            },
            style = TextStyle(fontFamily = UiFontFamily, fontSize = (size * 0.46f).sp),
        )
    }
}
