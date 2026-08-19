package com.dancaai.app

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult
import kotlin.math.max

/**
 * Camada de desenho sobre o preview da câmera.
 *
 * Desenha apenas o que precisa estar ancorado no corpo — esqueleto, landmarks e
 * ombros coloridos pelo resultado da postura. O texto de feedback (postura,
 * transferência de peso, contadores) é responsabilidade do HUD em Compose, que
 * segue o design system do app; duplicar aqui produziria dois feedbacks
 * concorrentes na mesma tela.
 */
class OverlayView(context: Context, attrs: AttributeSet?) : View(context, attrs) {

    private var results: PoseLandmarkerResult? = null
    private var imageWidth: Int = 1
    private var imageHeight: Int = 1
    private var isFrontCamera: Boolean = false
    private var angles: BodyAngles? = null
    private var postureResult: PostureResult = PostureResult.Unknown

    /** Ângulos articulares são instrumentação de desenvolvimento, não feedback ao usuário. */
    var showAngles: Boolean = BuildConfig.DEBUG
        set(value) {
            field = value
            invalidate()
        }

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

    // ── Desenho ──────────────────────────────────────────────────────────────

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

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

        // 4. Ângulos articulares (instrumentação de desenvolvimento)
        angles?.takeIf { showAngles }?.let { a ->
            drawAngle(canvas, landmarks[25], "%.0f°".format(a.leftKnee),      scaleFactor, offsetX, offsetY)
            drawAngle(canvas, landmarks[26], "%.0f°".format(a.rightKnee),     scaleFactor, offsetX, offsetY)
            drawAngle(canvas, landmarks[23], "%.0f°".format(a.leftHip),       scaleFactor, offsetX, offsetY)
            drawAngle(canvas, landmarks[24], "%.0f°".format(a.rightHip),      scaleFactor, offsetX, offsetY)
            drawAngle(canvas, landmarks[11], "%.0f°".format(a.leftShoulder),  scaleFactor, offsetX, offsetY)
            drawAngle(canvas, landmarks[12], "%.0f°".format(a.rightShoulder), scaleFactor, offsetX, offsetY)
            drawAngle(canvas, landmarks[13], "%.0f°".format(a.leftElbow),     scaleFactor, offsetX, offsetY)
            drawAngle(canvas, landmarks[14], "%.0f°".format(a.rightElbow),    scaleFactor, offsetX, offsetY)
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