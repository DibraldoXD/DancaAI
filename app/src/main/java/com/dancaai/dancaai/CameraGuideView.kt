package com.dancaai.app

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import kotlin.math.roundToInt

/**
 * View que exibe o HUD de guia de posicionamento da câmera.
 * Mostra barras de progresso para distância, ângulo e centralização.
 */
class CameraGuideView(context: Context, attrs: AttributeSet?) : View(context, attrs) {

    private var guideResult: CameraGuideResult? = null
    private var isVisible = true

    // ── Paints ───────────────────────────────────────────────────────────────

    private val bgPaint = Paint().apply {
        color = Color.argb(180, 0, 0, 0)
        style = Paint.Style.FILL
    }

    private val barBgPaint = Paint().apply {
        color = Color.argb(100, 255, 255, 255)
        style = Paint.Style.FILL
    }

    private val labelPaint = Paint().apply {
        color = Color.WHITE
        textSize = 26f
        typeface = Typeface.DEFAULT_BOLD
    }

    private val messagePaint = Paint().apply {
        color = Color.rgb(200, 200, 200)
        textSize = 22f
    }

    private val idealPaint = Paint().apply {
        color = Color.GREEN
        textSize = 30f
        typeface = Typeface.DEFAULT_BOLD
        setShadowLayer(4f, 2f, 2f, Color.BLACK)
    }

    private val barRadius = 8f
    private val barHeight = 14f

    // ── Público ───────────────────────────────────────────────────────────────

    fun update(result: CameraGuideResult) {
        guideResult = result
        invalidate()
    }

    fun toggleVisibility() {
        isVisible = !isVisible
        invalidate()
    }

    // ── Desenho ───────────────────────────────────────────────────────────────

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (!isVisible) return
        val g = guideResult ?: return

        val panelW = 300f
        val panelH = 160f
        val margin = 16f
        val left   = margin
        val top    = margin

        // Fundo do painel
        val rect = RectF(left, top, left + panelW, top + panelH)
        canvas.drawRoundRect(rect, 16f, 16f, bgPaint)

        // Se posição ideal, mostra só o check
        if (g.isIdeal) {
            canvas.drawText("✓ POSIÇÃO IDEAL", left + 20f, top + panelH / 2f + 10f, idealPaint)
            return
        }

        // Linhas do HUD
        drawRow(canvas, "📏", g.distanceMessage,  g.distanceScore, left + 14f, top + 44f,  panelW - 28f)
        drawRow(canvas, "📐", g.angleMessage,     g.angleScore,    left + 14f, top + 90f,  panelW - 28f)
        drawRow(canvas, "👤", g.centerMessage,    g.centerScore,   left + 14f, top + 136f, panelW - 28f)
    }

    private fun drawRow(
        canvas: Canvas,
        icon: String,
        message: String,
        score: Float,
        x: Float,
        y: Float,
        maxWidth: Float
    ) {
        // Ícone + mensagem
        canvas.drawText("$icon $message", x, y - 4f, messagePaint)

        // Barra de fundo
        val barTop  = y + 4f
        val barBot  = barTop + barHeight
        val bgRect  = RectF(x, barTop, x + maxWidth, barBot)
        canvas.drawRoundRect(bgRect, barRadius, barRadius, barBgPaint)

        // Barra de progresso
        val fillW   = (maxWidth * score).coerceAtLeast(barRadius * 2)
        val fillRect = RectF(x, barTop, x + fillW, barBot)
        val barColor = scoreToColor(score)
        val fillPaint = Paint().apply {
            color = barColor
            style = Paint.Style.FILL
        }
        canvas.drawRoundRect(fillRect, barRadius, barRadius, fillPaint)

        // Percentual
        val pct = (score * 100).roundToInt()
        val pctPaint = Paint().apply {
            color = Color.WHITE
            textSize = 20f
            typeface = Typeface.DEFAULT_BOLD
        }
        canvas.drawText("$pct%", x + maxWidth + 4f, barBot, pctPaint)
    }

    private fun scoreToColor(score: Float): Int = when {
        score >= 0.85f -> Color.rgb(50, 200, 80)   // verde
        score >= 0.60f -> Color.rgb(255, 180, 0)   // amarelo
        else           -> Color.rgb(220, 60, 60)   // vermelho
    }
}
