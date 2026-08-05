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

    fun updateWeightInfo(info: WeightInfo) {
        weightInfo = info
        invalidate()
    }

    // ── Desenho ──────────────────────────────────────────────────────────────

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // Painel de peso sempre visível, independente de pose detectada
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

        // 5. Feedback de postura — acima do HUD inferior (metrônomo/registrar/encerrar)
        when (val p = postureResult) {
            is PostureResult.Good -> {
                canvas.drawText(
                    "✓ POSTURA OK",
                    width * 0.5f - 130f,
                    height * 0.62f,
                    postureOkPaint
                )
            }
            is PostureResult.Bad -> {
                p.issues.forEachIndexed { i, issue ->
                    canvas.drawText(
                        "⚠ $issue",
                        width * 0.5f - 200f,
                        height * 0.50f + i * 44f,
                        postureWarnPaint
                    )
                }
            }
            is PostureResult.Unknown -> {
                canvas.drawText(
                    "Aguardando pose...",
                    width * 0.5f - 160f,
                    height * 0.62f,
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