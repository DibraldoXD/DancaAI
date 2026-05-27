package com.dancaai.app

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult
import kotlin.math.max
import kotlin.math.min

/**
 * Equivalente ao mp_drawing.draw_landmarks() do Python.
 * Desenha os landmarks e conexões sobre a imagem da câmera em tempo real.
 */
class OverlayView(context: Context, attrs: AttributeSet?) : View(context, attrs) {

    private var results: PoseLandmarkerResult? = null
    private var imageWidth: Int = 1
    private var imageHeight: Int = 1
    private var scaleFactor: Float = 1f

    // Cores equivalentes ao Python:
    // DrawingSpec(color=(245,117,66)) → ponto laranja
    // DrawingSpec(color=(245,66,230)) → conexão rosa/magenta
    private val pointPaint = Paint().apply {
        color = Color.rgb(245, 117, 66)  // laranja
        strokeWidth = 8f
        style = Paint.Style.FILL
    }

    private val linePaint = Paint().apply {
        color = Color.rgb(245, 66, 230)  // magenta
        strokeWidth = 4f
        style = Paint.Style.STROKE
    }

    fun setResults(
        poseLandmarkerResults: PoseLandmarkerResult,
        imageWidth: Int,
        imageHeight: Int
    ) {
        results = poseLandmarkerResults
        this.imageWidth = imageWidth
        this.imageHeight = imageHeight
        invalidate()  // força redesenho
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val results = results ?: return
        if (results.landmarks().isEmpty()) return

        // Calcula fator de escala mantendo proporção
        scaleFactor = max(width * 1f / imageWidth, height * 1f / imageHeight)

        val landmarks = results.landmarks()[0]

        // Desenha conexões (equivalente ao POSE_CONNECTIONS do Python)
        for (connection in PoseLandmarker.POSE_LANDMARKS) {
            val start = landmarks.getOrNull(connection!!.start()) ?: continue
            val end   = landmarks.getOrNull(connection.end())   ?: continue

            canvas.drawLine(
                mirrorX(start.x()) * imageWidth * scaleFactor,
                start.y() * imageHeight * scaleFactor,
                mirrorX(end.x()) * imageWidth * scaleFactor,
                end.y() * imageHeight * scaleFactor,
                linePaint
            )
        }

        // Desenha pontos
        for (landmark in landmarks) {
            canvas.drawCircle(
                mirrorX(landmark.x()) * imageWidth * scaleFactor,
                landmark.y() * imageHeight * scaleFactor,
                8f,
                pointPaint
            )
        }
    }

    // Espelha X para câmera frontal (opcional — desative para câmera traseira)
    private fun mirrorX(x: Float): Float = x  // troque por (1f - x) para câmera frontal
}
