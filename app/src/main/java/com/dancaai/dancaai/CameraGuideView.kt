package com.dancaai.app

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import kotlin.math.roundToInt

class CameraGuideView(context: Context, attrs: AttributeSet?) : View(context, attrs) {

    private var guideResult: CameraGuideResult? = null
    private var isGuideVisible = true

    private val bgPaint = Paint().apply {
        color = Color.argb(180, 0, 0, 0)
        style = Paint.Style.FILL
    }

    private val barBgPaint = Paint().apply {
        color = Color.argb(80, 255, 255, 255)
        style = Paint.Style.FILL
    }

    private val messagePaint = Paint().apply {
        color = Color.rgb(210, 210, 210)
        textSize = 22f
    }

    private val idealPaint = Paint().apply {
        color = Color.GREEN
        textSize = 32f
        typeface = Typeface.DEFAULT_BOLD
        setShadowLayer(4f, 2f, 2f, Color.BLACK)
    }

    private val barHeight = 20f
    private val barRadius = 20f

    fun update(result: CameraGuideResult) {
        guideResult = result
        invalidate()
    }

    fun toggleVisibility() {
        isGuideVisible = !isGuideVisible
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (!isGuideVisible) return
        val g = guideResult ?: return

        val panelW = 700f
        val panelH = 700f
        val left   = 4f
        val top    = 4f

        // Fundo
        canvas.drawRoundRect(RectF(left, top, left + panelW, top + panelH), 16f, 16f, bgPaint)

        if (g.isIdeal) {
            canvas.drawText(
                "✓ POSIÇÃO IDEAL",
                left + 20f,
                top + panelH / 2f + 12f,
                idealPaint
            )
            return
        }

        // 3 linhas: corpo, distância, centralização
        val rowSpacing = panelH / 5f
        drawRow(canvas, "👤", g.bodyMessage,       g.bodyScore,      left + 12f, top + rowSpacing,       panelW - 60f)
        drawRow(canvas, "📏", g.distanceMessage,   g.distanceScore,  left + 12f, top + rowSpacing * 2f,  panelW - 60f)
        drawRow(canvas, "↔️", g.centerMessage,     g.centerScore,    left + 12f, top + rowSpacing * 3f,  panelW - 60f)
    }

    private fun drawRow(
        canvas: Canvas,
        icon: String,
        message: String,
        score: Float,
        x: Float,
        y: Float,
        barWidth: Float
    ) {
        // Mensagem
        canvas.drawText("$icon $message", x, y - 4f, messagePaint)

        // Barra fundo
        val barTop = y + 4f
        val barBot = barTop + barHeight
        canvas.drawRoundRect(RectF(x, barTop, x + barWidth, barBot), barRadius, barRadius, barBgPaint)

        // Barra preenchida
        val fillW = (barWidth * score).coerceAtLeast(barRadius * 2)
        val fillPaint = Paint().apply {
            color = scoreToColor(score)
            style = Paint.Style.FILL
        }
        canvas.drawRoundRect(RectF(x, barTop, x + fillW, barBot), barRadius, barRadius, fillPaint)

        // Percentual
        val pctPaint = Paint().apply {
            color = Color.WHITE
            textSize = 20f
            typeface = Typeface.DEFAULT_BOLD
        }
        canvas.drawText("${(score * 100).roundToInt()}%", x + barWidth + 6f, barBot, pctPaint)
    }

    private fun scoreToColor(score: Float): Int = when {
        score >= 0.85f -> Color.rgb(50, 200, 80)
        score >= 0.60f -> Color.rgb(255, 180, 0)
        else           -> Color.rgb(220, 60, 60)
    }
}