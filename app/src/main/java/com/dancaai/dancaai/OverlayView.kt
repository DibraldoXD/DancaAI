package com.dancaai.app

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult
import kotlin.math.max

class OverlayView(context: Context, attrs: AttributeSet?) : View(context, attrs) {

    private var results: PoseLandmarkerResult? = null
    private var imageWidth: Int = 1
    private var imageHeight: Int = 1
    private var isFrontCamera: Boolean = false
    private var angles: BodyAngles? = null
    private var postureResult: PostureResult = PostureResult.Unknown
    private var debugLandmarks: List<NormalizedLandmark>? = null
    private var weightInfo: WeightInfo = WeightInfo(WeightLeg.NEUTRAL, MovementDirection.NEUTRAL, false, 0, 0)

    // ── Paints ──────────────────────────────────────────────────────────────

    private val pointPaint = Paint().apply {
        color = Color.rgb(245, 117, 66)
        strokeWidth = 10f
        style = Paint.Style.FILL
    }

    private val linePaint = Paint().apply {
        color = Color.rgb(245, 66, 230)
        strokeWidth = 5f
        style = Paint.Style.STROKE
    }

    private val anglePaint = Paint().apply {
        color = Color.YELLOW
        textSize = 32f
        typeface = Typeface.DEFAULT_BOLD
        setShadowLayer(3f, 1f, 1f, Color.BLACK)
    }

    private val postureOkPaint = Paint().apply {
        color = Color.GREEN
        textSize = 38f
        typeface = Typeface.DEFAULT_BOLD
        setShadowLayer(4f, 2f, 2f, Color.BLACK)
    }

    private val postureWarnPaint = Paint().apply {
        color = Color.RED
        textSize = 38f
        typeface = Typeface.DEFAULT_BOLD
        setShadowLayer(4f, 2f, 2f, Color.BLACK)
    }

    private val shoulderOkPaint = Paint().apply {
        color = Color.GREEN
        strokeWidth = 12f
        style = Paint.Style.FILL
    }

    private val shoulderWarnPaint = Paint().apply {
        color = Color.RED
        strokeWidth = 12f
        style = Paint.Style.FILL
    }

    private val weightBgPaint = Paint().apply {
        color = Color.argb(160, 0, 0, 0)
        style = Paint.Style.FILL
    }

    private val weightLegPaint = Paint().apply {
        textSize = 52f
        typeface = Typeface.DEFAULT_BOLD
        setShadowLayer(4f, 2f, 2f, Color.BLACK)
    }

    private val weightDirPaint = Paint().apply {
        textSize = 64f
        typeface = Typeface.DEFAULT_BOLD
        setShadowLayer(4f, 2f, 2f, Color.BLACK)
    }

    private val weightErrorPaint = Paint().apply {
        color = Color.rgb(255, 60, 60)
        textSize = 36f
        typeface = Typeface.DEFAULT_BOLD
        setShadowLayer(4f, 2f, 2f, Color.BLACK)
    }

    private val weightCounterPaint = Paint().apply {
        color = Color.WHITE
        textSize = 32f
        typeface = Typeface.DEFAULT_BOLD
        setShadowLayer(3f, 1f, 1f, Color.BLACK)
    }

    private val debugBgPaint = Paint().apply {
        color = Color.argb(170, 0, 0, 0)
        style = Paint.Style.FILL
    }

    private val debugTextPaint = Paint().apply {
        color = Color.WHITE
        textSize = 24f
        typeface = Typeface.MONOSPACE
        setShadowLayer(2f, 1f, 1f, Color.BLACK)
    }

    private val debugLabelPaint = Paint().apply {
        color = Color.CYAN
        textSize = 24f
        typeface = Typeface.MONOSPACE
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
    }

    // ── Métodos públicos ─────────────────────────────────────────────────────

    fun setResults(
        poseLandmarkerResults: PoseLandmarkerResult,
        imageWidth: Int,
        imageHeight: Int,
        isFrontCamera: Boolean = false
    ) {
        results = poseLandmarkerResults
        this.imageWidth = imageWidth
        this.imageHeight = imageHeight
        this.isFrontCamera = isFrontCamera
        invalidate()
    }

    fun updateAngles(angles: BodyAngles) {
        this.angles = angles
        invalidate()
    }

    fun updatePosture(result: PostureResult) {
        this.postureResult = result
        invalidate()
    }

    fun updateDebugLandmarks(landmarks: List<NormalizedLandmark>) {
        this.debugLandmarks = landmarks
        invalidate()
    }

    fun updateWeightInfo(info: WeightInfo) {
        weightInfo = info
        invalidate()
    }

    // ── Desenho ──────────────────────────────────────────────────────────────

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // Painel de debug sempre visível, independente de pose detectada
        drawDebugPanel(canvas)
        drawWeightPanel(canvas)

        val results = results ?: return
        if (results.landmarks().isEmpty()) return

        val scaleFactor = max(width * 1f / imageWidth, height * 1f / imageHeight)
        val offsetX = (width  - imageWidth  * scaleFactor) / 2f
        val offsetY = (height - imageHeight * scaleFactor) / 2f

        val landmarks = results.landmarks()[0]

        // 1. Conexões do esqueleto
        for (connection in PoseLandmarker.POSE_LANDMARKS) {
            val start = landmarks.getOrNull(connection!!.start()) ?: continue
            val end   = landmarks.getOrNull(connection.end())     ?: continue
            canvas.drawLine(
                toScreenX(start.x(), scaleFactor, offsetX),
                toScreenY(start.y(), scaleFactor, offsetY),
                toScreenX(end.x(),   scaleFactor, offsetX),
                toScreenY(end.y(),   scaleFactor, offsetY),
                linePaint
            )
        }

        // 2. Pontos dos landmarks
        for (landmark in landmarks) {
            canvas.drawCircle(
                toScreenX(landmark.x(), scaleFactor, offsetX),
                toScreenY(landmark.y(), scaleFactor, offsetY),
                8f,
                pointPaint
            )
        }

        // 3. Ombros coloridos conforme postura
        val isPostureBad = postureResult is PostureResult.Bad
        val shoulderPaint = if (isPostureBad) shoulderWarnPaint else shoulderOkPaint
        canvas.drawCircle(
            toScreenX(landmarks[11].x(), scaleFactor, offsetX),
            toScreenY(landmarks[11].y(), scaleFactor, offsetY),
            16f, shoulderPaint
        )
        canvas.drawCircle(
            toScreenX(landmarks[12].x(), scaleFactor, offsetX),
            toScreenY(landmarks[12].y(), scaleFactor, offsetY),
            16f, shoulderPaint
        )

        // 4. Ângulos articulares
        angles?.let { a ->
            drawAngle(canvas, landmarks[25], "%.0f°".format(a.leftKnee),      scaleFactor, offsetX, offsetY)
            drawAngle(canvas, landmarks[26], "%.0f°".format(a.rightKnee),     scaleFactor, offsetX, offsetY)
            drawAngle(canvas, landmarks[23], "%.0f°".format(a.leftHip),       scaleFactor, offsetX, offsetY)
            drawAngle(canvas, landmarks[24], "%.0f°".format(a.rightHip),      scaleFactor, offsetX, offsetY)
            drawAngle(canvas, landmarks[11], "%.0f°".format(a.leftShoulder),  scaleFactor, offsetX, offsetY)
            drawAngle(canvas, landmarks[12], "%.0f°".format(a.rightShoulder), scaleFactor, offsetX, offsetY)
            drawAngle(canvas, landmarks[13], "%.0f°".format(a.leftElbow),     scaleFactor, offsetX, offsetY)
            drawAngle(canvas, landmarks[14], "%.0f°".format(a.rightElbow),    scaleFactor, offsetX, offsetY)
        }

        // 5. Feedback de postura na parte inferior da tela
        when (val p = postureResult) {
            is PostureResult.Good -> {
                canvas.drawText(
                    "✓ POSTURA OK",
                    width * 0.5f - 130f,
                    height * 0.93f,
                    postureOkPaint
                )
            }
            is PostureResult.Bad -> {
                p.issues.forEachIndexed { i, issue ->
                    canvas.drawText(
                        "⚠ $issue",
                        width * 0.5f - 200f,
                        height * 0.88f + i * 44f,
                        postureWarnPaint
                    )
                }
            }
            is PostureResult.Unknown -> {
                canvas.drawText(
                    "Aguardando pose...",
                    width * 0.5f - 160f,
                    height * 0.93f,
                    anglePaint
                )
            }
        }

    }

    private fun drawWeightPanel(canvas: Canvas) {
        val info = weightInfo

        val legText = when (info.leg) {
            WeightLeg.LEFT    -> "ESQUERDA"
            WeightLeg.RIGHT   -> "DIREITA"
            WeightLeg.NEUTRAL -> "NEUTRO"
        }
        val legColor = when (info.leg) {
            WeightLeg.LEFT    -> Color.rgb(64,  196, 255)
            WeightLeg.RIGHT   -> Color.rgb(255, 180, 50)
            WeightLeg.NEUTRAL -> Color.rgb(200, 200, 200)
        }
        val dirSymbol = when (info.direction) {
            MovementDirection.LEFT    -> "←"
            MovementDirection.RIGHT   -> "→"
            MovementDirection.UP      -> "↑"
            MovementDirection.DOWN    -> "↓"
            MovementDirection.NEUTRAL -> "•"
        }

        val padH  = 16f
        val padV  = 12f
        val lineH = 56f
        val panelW = 340f
        val linesCount = if (info.showError) 3 else 2
        val panelH = linesCount * lineH + padV * 2
        val left = 8f
        val top  = 8f

        canvas.drawRoundRect(
            RectF(left, top, left + panelW, top + panelH),
            12f, 12f, weightBgPaint
        )

        // Linha 1: perna + seta
        weightLegPaint.color = legColor
        canvas.drawText(legText, left + padH, top + padV + lineH * 0.82f, weightLegPaint)
        weightDirPaint.color = legColor
        canvas.drawText(dirSymbol, left + panelW - padH - 64f, top + padV + lineH * 0.82f, weightDirPaint)

        // Linha 2: contadores
        canvas.drawText(
            "✓ ${info.correctCount}    ✗ ${info.errorCount}",
            left + padH, top + padV + lineH * 1.82f, weightCounterPaint
        )

        // Linha 3: aviso de erro (somente quando ativo)
        if (info.showError) {
            canvas.drawText(
                "⚠ MARCAÇÃO INCORRETA",
                left + padH, top + padV + lineH * 2.82f, weightErrorPaint
            )
        }
    }

    private fun drawDebugPanel(canvas: Canvas) {
        data class Row(val label: String, val idx: Int)
        val rows = listOf(
            Row("NAR  ", 0),
            Row("ORE-E", 7),  Row("ORE-D", 8),
            Row("OMB-E", 11), Row("OMB-D", 12),
            Row("QDR-E", 23), Row("QDR-D", 24),
            Row("JOE-E", 25), Row("JOE-D", 26),
            Row("TRN-E", 27), Row("TRN-D", 28),
        )

        val lineH  = 28f
        val panelW = 430f
        val panelH = (rows.size + 1) * lineH + 44f  // +1 para linha derivada
        val left   = width - panelW - 8f
        val top    = 8f

        canvas.drawRoundRect(
            RectF(left, top, left + panelW, top + panelH),
            8f, 8f, debugBgPaint
        )

        rows.forEachIndexed { i, row ->
            val lmk = debugLandmarks?.getOrNull(row.idx)
            val y   = top + 28f + i * lineH
            canvas.drawText("${row.label}:", left + 8f, y, debugLabelPaint)
            val value = if (lmk != null)
                "x=%.3f y=%.3f z=%.3f".format(lmk.x(), lmk.y(), lmk.z())
            else
                "aguardando..."
            canvas.drawText(value, left + 90f, y, debugTextPaint)
        }

        // Linha de métricas derivadas para diagnóstico Z
        val lm = debugLandmarks
        val yD = top + 28f + rows.size * lineH + 6f
        canvas.drawText("CALC :", left + 8f, yD, debugLabelPaint)
        if (lm != null && lm.size >= 25) {
            val lS    = lm[11]; val rS = lm[12]
            val lH    = lm[23]; val rH = lm[24]
            val span  = kotlin.math.abs(rS.x() - lS.x())
            val zDiff = (lS.z() + rS.z()) / 2f - (lH.z() + rH.z()) / 2f
            val normZ = if (span > 0.01f) zDiff / span else 0f
            canvas.drawText(
                "span=%.3f  Zdiff=%.3f  norm=%.2f".format(span, zDiff, normZ),
                left + 90f, yD, debugTextPaint
            )
        } else {
            canvas.drawText("aguardando...", left + 90f, yD, debugTextPaint)
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private fun drawAngle(
        canvas: Canvas,
        landmark: NormalizedLandmark,
        text: String,
        scaleFactor: Float,
        offsetX: Float,
        offsetY: Float
    ) {
        canvas.drawText(
            text,
            toScreenX(landmark.x(), scaleFactor, offsetX) + 10f,
            toScreenY(landmark.y(), scaleFactor, offsetY) - 10f,
            anglePaint
        )
    }

    private fun toScreenX(x: Float, scaleFactor: Float, offsetX: Float): Float {
        val normalizedX = if (isFrontCamera) 1f - x else x
        return normalizedX * imageWidth * scaleFactor + offsetX
    }

    private fun toScreenY(y: Float, scaleFactor: Float, offsetY: Float): Float {
        return y * imageHeight * scaleFactor + offsetY
    }
}