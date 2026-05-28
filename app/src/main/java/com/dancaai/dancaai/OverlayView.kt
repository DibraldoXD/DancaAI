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

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val results = results ?: return
        if (results.landmarks().isEmpty()) return

        // Calcula escala mantendo proporção (igual ao Python)
        val scaleFactor = max(width * 1f / imageWidth, height * 1f / imageHeight)

        // Calcula offset para centralizar o overlay na tela
        val offsetX = (width  - imageWidth  * scaleFactor) / 2f
        val offsetY = (height - imageHeight * scaleFactor) / 2f

        val landmarks = results.landmarks()[0]

        // Desenha conexões
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

        // Desenha pontos
        for (landmark in landmarks) {
            canvas.drawCircle(
                toScreenX(landmark.x(), scaleFactor, offsetX),
                toScreenY(landmark.y(), scaleFactor, offsetY),
                8f,
                pointPaint
            )
        }


        // Desenha ângulos nas articulações principais
        angles?.let { a ->
            val lm = results.landmarks()[0]
            drawAngle(canvas, lm[25], "%.0f°".format(a.leftKnee),  scaleFactor, offsetX, offsetY)
            drawAngle(canvas, lm[26], "%.0f°".format(a.rightKnee), scaleFactor, offsetX, offsetY)
            drawAngle(canvas, lm[23], "%.0f°".format(a.leftHip),   scaleFactor, offsetX, offsetY)
            drawAngle(canvas, lm[24], "%.0f°".format(a.rightHip),  scaleFactor, offsetX, offsetY)
        }
    }

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

    private var angles: BodyAngles? = null

    fun updateAngles(angles: BodyAngles) {
        this.angles = angles
        invalidate()
    }

    private val anglePaint = Paint().apply {
        color = Color.YELLOW
        textSize = 32f
        typeface = Typeface.DEFAULT_BOLD
    }

    private fun toScreenX(x: Float, scaleFactor: Float, offsetX: Float): Float {
        // Espelha horizontalmente para câmera frontal
        val normalizedX = if (isFrontCamera) 1f - x else x
        return normalizedX * imageWidth * scaleFactor + offsetX
    }

    private fun toScreenY(y: Float, scaleFactor: Float, offsetY: Float): Float {
        return y * imageHeight * scaleFactor + offsetY
    }
}