package com.dancaai.app

import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import kotlin.math.abs
import kotlin.math.sqrt

object CameraGuide {

    // ── Zonas ideais ─────────────────────────────────────────────────────────

    // Distância ideal: corpo deve ocupar entre 40% e 75% da altura do frame
    private const val DIST_MIN = 0.40f
    private const val DIST_MAX = 0.75f

    // Ângulo ideal: linha ombro-quadril deve ser quase horizontal (< 5% de inclinação)
    private const val ANGLE_THRESHOLD = 0.05f

    // Centralização: centro do corpo deve estar entre 35% e 65% do frame horizontal
    private const val CENTER_MIN = 0.35f
    private const val CENTER_MAX = 0.65f

    fun evaluate(landmarks: List<NormalizedLandmark>): CameraGuideResult {
        if (landmarks.size < 29) return CameraGuideResult.unknown()

        val leftShoulder  = landmarks[11]
        val rightShoulder = landmarks[12]
        val leftHip       = landmarks[23]
        val rightHip      = landmarks[24]
        val leftAnkle     = landmarks[27]
        val rightAnkle    = landmarks[28]
        val nose          = landmarks[0]

        // Verifica visibilidade mínima
        val minVis = 0.4f
        if (listOf(leftShoulder, rightShoulder, leftHip, rightHip)
                .any { it.visibility().orElse(0f) < minVis }
        ) return CameraGuideResult.unknown()

        // ── 1. DISTÂNCIA ─────────────────────────────────────────────────────
        // Usa a altura do corpo (nariz até tornozelo) como proxy de distância
        val bodyTop    = nose.y()
        val bodyBottom = maxOf(leftAnkle.y(), rightAnkle.y())
        val bodyHeight = bodyBottom - bodyTop  // 0.0 a 1.0

        val distScore = when {
            bodyHeight < DIST_MIN -> bodyHeight / DIST_MIN          // muito longe
            bodyHeight > DIST_MAX -> 1f - (bodyHeight - DIST_MAX) / (1f - DIST_MAX) // muito perto
            else -> 1f  // zona ideal
        }.coerceIn(0f, 1f)

        val distMessage = when {
            bodyHeight < DIST_MIN      -> "Aproxime-se"
            bodyHeight > DIST_MAX      -> "Afaste-se"
            bodyHeight < DIST_MIN + 0.05f -> "Um pouco mais perto"
            bodyHeight > DIST_MAX - 0.05f -> "Um pouco mais longe"
            else                       -> "✓ Distância ideal"
        }

        // ── 2. ÂNGULO DA CÂMERA ───────────────────────────────────────────────
        // Verifica se ombros e quadril estão no mesmo nível horizontal
        // (câmera muito alta/baixa distorce a perspectiva)
        val shoulderMidY = (leftShoulder.y() + rightShoulder.y()) / 2f
        val hipMidY      = (leftHip.y() + rightHip.y()) / 2f
        val expectedRatio = 0.35f  // ombros devem estar ~35% acima do quadril

        val actualRatio  = hipMidY - shoulderMidY
        val angleDiff    = abs(actualRatio - expectedRatio)

        val angleScore = (1f - angleDiff / 0.20f).coerceIn(0f, 1f)

        val angleMessage = when {
            actualRatio < expectedRatio - ANGLE_THRESHOLD -> "Incline a câmera para baixo"
            actualRatio > expectedRatio + ANGLE_THRESHOLD -> "Incline a câmera para cima"
            else -> "✓ Ângulo ideal"
        }

        // ── 3. CENTRALIZAÇÃO ─────────────────────────────────────────────────
        val bodyMidX = (leftShoulder.x() + rightShoulder.x() +
                        leftHip.x()      + rightHip.x()) / 4f

        val centerScore = when {
            bodyMidX < CENTER_MIN -> bodyMidX / CENTER_MIN
            bodyMidX > CENTER_MAX -> 1f - (bodyMidX - CENTER_MAX) / (1f - CENTER_MAX)
            else -> 1f
        }.coerceIn(0f, 1f)

        val centerMessage = when {
            bodyMidX < CENTER_MIN      -> "Mova a câmera para a direita"
            bodyMidX > CENTER_MAX      -> "Mova a câmera para a esquerda"
            bodyMidX < CENTER_MIN + 0.05f -> "Centralize um pouco mais"
            bodyMidX > CENTER_MAX - 0.05f -> "Centralize um pouco mais"
            else                       -> "✓ Centralizado"
        }

        // ── Score geral ───────────────────────────────────────────────────────
        val overallScore = (distScore * 0.4f + angleScore * 0.3f + centerScore * 0.3f)

        return CameraGuideResult(
            distanceScore   = distScore,
            distanceMessage = distMessage,
            angleScore      = angleScore,
            angleMessage    = angleMessage,
            centerScore     = centerScore,
            centerMessage   = centerMessage,
            overallScore    = overallScore,
            isIdeal         = overallScore >= 0.85f
        )
    }
}

data class CameraGuideResult(
    val distanceScore:   Float,
    val distanceMessage: String,
    val angleScore:      Float,
    val angleMessage:    String,
    val centerScore:     Float,
    val centerMessage:   String,
    val overallScore:    Float,
    val isIdeal:         Boolean
) {
    companion object {
        fun unknown() = CameraGuideResult(
            distanceScore   = 0f,
            distanceMessage = "Posicione-se na câmera",
            angleScore      = 0f,
            angleMessage    = "--",
            centerScore     = 0f,
            centerMessage   = "--",
            overallScore    = 0f,
            isIdeal         = false
        )
    }
}
