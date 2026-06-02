package com.dancaai.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.dancaai.app.ui.theme.DcaTheme

/**
 * Gráfico de linha + área preenchida (gradiente do acento).
 * Usado na Home (evolução semanal) e no Resultado (evolução da sessão).
 */
@Composable
fun LineAreaChart(
    data: List<Int>,
    modifier: Modifier = Modifier,
    minValue: Int = 40,
    maxValue: Int = 100,
    showDots: Boolean = false,
    gridLines: List<Int> = emptyList(),
) {
    val accent = DcaTheme.colors.accent
    val outline = DcaTheme.colors.outline
    val bg = DcaTheme.colors.bg

    Canvas(modifier = modifier.fillMaxWidth().height(80.dp)) {
        if (data.size < 2) return@Canvas
        val w = size.width
        val h = size.height
        val range = (maxValue - minValue).coerceAtLeast(1).toFloat()
        fun y(v: Int) = h - ((v - minValue) / range) * h
        fun x(i: Int) = i / (data.size - 1).toFloat() * w

        // gridlines tracejadas
        val dashed = PathEffect.dashPathEffect(floatArrayOf(4f, 8f))
        gridLines.forEach { gv ->
            val gy = y(gv)
            drawLine(outline, Offset(0f, gy), Offset(w, gy), strokeWidth = 1f, pathEffect = dashed)
        }

        val points = data.mapIndexed { i, v -> Offset(x(i), y(v)) }
        val line = Path().apply {
            moveTo(points.first().x, points.first().y)
            points.drop(1).forEach { lineTo(it.x, it.y) }
        }
        val area = Path().apply {
            addPath(line)
            lineTo(w, h)
            lineTo(0f, h)
            close()
        }
        drawPath(
            area,
            brush = Brush.verticalGradient(
                listOf(accent.copy(alpha = 0.40f), accent.copy(alpha = 0f)),
            ),
        )
        drawPath(line, color = accent, style = Stroke(width = 2.4f, cap = StrokeCap.Round))

        if (showDots) {
            points.forEachIndexed { i, p ->
                val last = i == points.lastIndex
                if (last) drawCircle(bg, radius = 6f, center = p)
                drawCircle(accent, radius = if (last) 4f else 2.4f, center = p)
            }
        }
    }
}

/** Barras de atividade (histórico de 30 dias). 0 = dia sem treino. */
@Composable
fun HistoryBars(heights: List<Int>, modifier: Modifier = Modifier) {
    val accent = DcaTheme.colors.accent
    val empty = DcaTheme.colors.surface2
    Row(
        modifier = modifier.fillMaxWidth().height(64.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        heights.forEach { hv ->
            Canvas(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(if (hv > 0) (hv / 100f).coerceIn(0.06f, 1f) else 0.06f),
            ) {
                drawRoundRect(
                    color = if (hv > 0) accent.copy(alpha = 0.4f + (hv / 100f) * 0.6f) else empty,
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(2f, 2f),
                )
            }
        }
    }
}

/**
 * Score circular grande (tela de Resultado): anel de progresso colorido
 * por threshold + número central.
 */
@Composable
fun CircleScore(
    value: Int,
    modifier: Modifier = Modifier,
    diameter: Int = 200,
    content: @Composable () -> Unit,
) {
    val track = DcaTheme.colors.surface2
    val ring = com.dancaai.app.ui.theme.scoreColor(value)
    Box(contentAlignment = Alignment.Center, modifier = modifier.size(diameter.dp)) {
        Canvas(modifier = Modifier.fillMaxWidth().size(diameter.dp)) {
            val stroke = 10.dp.toPx()
            val inset = stroke / 2f
            val arcSize = androidx.compose.ui.geometry.Size(size.width - stroke, size.height - stroke)
            val topLeft = Offset(inset, inset)
            drawArc(
                color = track, startAngle = 0f, sweepAngle = 360f, useCenter = false,
                topLeft = topLeft, size = arcSize, style = Stroke(width = stroke),
            )
            drawArc(
                color = ring, startAngle = -90f, sweepAngle = (value / 100f) * 360f, useCenter = false,
                topLeft = topLeft, size = arcSize, style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
        }
        content()
    }
}
