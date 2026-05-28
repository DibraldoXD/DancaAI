package com.dancaai.app

import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult
import kotlin.math.abs

class StepCounter {

    companion object {
        const val LEFT_KNEE = 25
        const val RIGHT_KNEE = 26
        const val LEFT_HIP = 23
        const val RIGHT_HIP = 24

        // Suavização — quantos frames considerar
        const val SMOOTHING_WINDOW = 8

        // Threshold dinâmico — % da distância entre quadris
        const val THRESHOLD_RATIO = 0.15f

        // Zona morta — precisa passar do threshold por esta margem extra
        const val DEAD_ZONE_RATIO = 0.05f

        // Confiança mínima do landmark
        const val MIN_VISIBILITY = 0.5f
    }

    var counter: Int = 0
        private set

    var stage: String? = null
        private set

    var onThreeRepsCompleted: (() -> Unit)? = null
    var onStageChanged: ((stage: String, counter: Int) -> Unit)? = null

    // Buffer de suavização
    private val kneeDiffBuffer = ArrayDeque<Float>(SMOOTHING_WINDOW)

    // Estado interno para zona morta
    private var lastConfirmedStage: String? = null

    fun process(result: PoseLandmarkerResult) {
        if (result.landmarks().isEmpty()) return
        val landmarks = result.landmarks()[0]

        val leftKnee  = landmarks.getOrNull(LEFT_KNEE)  ?: return
        val rightKnee = landmarks.getOrNull(RIGHT_KNEE) ?: return
        val leftHip   = landmarks.getOrNull(LEFT_HIP)   ?: return
        val rightHip  = landmarks.getOrNull(RIGHT_HIP)  ?: return

        // Verifica visibilidade mínima
        if ((leftKnee.visibility().orElse(0f))  < MIN_VISIBILITY) return
        if ((rightKnee.visibility().orElse(0f)) < MIN_VISIBILITY) return

        // Diferença bruta Y entre joelhos
        val rawDiff = leftKnee.y() - rightKnee.y()

        // Adiciona ao buffer de suavização
        if (kneeDiffBuffer.size >= SMOOTHING_WINDOW) kneeDiffBuffer.removeFirst()
        kneeDiffBuffer.addLast(rawDiff)

        // Média suavizada
        val smoothedDiff = kneeDiffBuffer.average().toFloat()

        // Threshold dinâmico baseado na largura do quadril
        val hipWidth = abs(leftHip.x() - rightHip.x())
        val threshold = hipWidth * THRESHOLD_RATIO
        val deadZone  = hipWidth * DEAD_ZONE_RATIO

        // Determina novo stage com zona morta
        val newStage = when {
            smoothedDiff > threshold + deadZone  -> "esquerda"
            smoothedDiff < -(threshold + deadZone) -> "direita"
            else -> null  // zona neutra — não muda stage
        }

        // Só conta quando muda de stage confirmado
        if (newStage != null && newStage != lastConfirmedStage) {
            lastConfirmedStage = newStage
            stage = newStage
            counter++
            checkReps()
            onStageChanged?.invoke(newStage, counter)
        }
    }

    private fun checkReps() {
        if (counter == 4) {
            onThreeRepsCompleted?.invoke()
            counter = 1
        }
    }

    fun reset() {
        counter = 0
        stage = null
        lastConfirmedStage = null
        kneeDiffBuffer.clear()
    }
}