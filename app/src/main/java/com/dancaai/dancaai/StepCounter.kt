package com.dancaai.app

import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult

/**
 * Equivalente à lógica do notebook Python:
 *   - Monitora posição Y dos joelhos e X/Z dos pés
 *   - Detecta transferência de peso esquerda/direita
 *   - Conta passos e notifica quando completa 3 repetições
 */
class StepCounter {

    // Índices dos landmarks MediaPipe (mesmos do Python mp_pose.PoseLandmark)
    companion object {
        const val LEFT_KNEE = 25
        const val RIGHT_KNEE = 26
        const val LEFT_FOOT_INDEX = 31
        const val RIGHT_FOOT_INDEX = 32
        const val THRESHOLD = 0.0007f   // mesmo valor do Python
    }

    var counter: Int = 0
        private set

    var stage: String? = null
        private set

    // Callback chamado quando completa 3 repetições
    var onThreeRepsCompleted: (() -> Unit)? = null

    // Callback chamado a cada mudança de stage
    var onStageChanged: ((stage: String, counter: Int) -> Unit)? = null

    fun process(result: PoseLandmarkerResult) {
        if (result.landmarks().isEmpty()) return

        val landmarks = result.landmarks()[0]  // primeira pessoa detectada

        val kneey1 = landmarks.getOrNull(LEFT_KNEE)?.y() ?: return
        val kneey2 = landmarks.getOrNull(RIGHT_KNEE)?.y() ?: return

        // Lógica idêntica ao Python:
        // if kneey1 > kneey2 + 0.0007 → transferência para esquerda
        if (kneey1 > kneey2 + THRESHOLD) {
            if (stage != "esquerda") {
                counter++
                stage = "esquerda"
                checkReps()
                onStageChanged?.invoke("esquerda", counter)
            }
        }
        // if kneey1 + 0.0007 < kneey2 → transferência para direita
        if (kneey1 + THRESHOLD < kneey2) {
            if (stage != "direita") {
                counter++
                stage = "direita"
                checkReps()
                onStageChanged?.invoke("direita", counter)
            }
        }
    }

    private fun checkReps() {
        if (counter == 4) {
            onThreeRepsCompleted?.invoke()
            counter = 1  // reseta para 1 igual ao Python
        }
    }

    fun reset() {
        counter = 0
        stage = null
    }
}
