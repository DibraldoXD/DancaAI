package com.dancaai.app

import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import kotlin.math.abs

object CameraGuide {

    // Landmarks que DEVEM estar visíveis para considerar corpo completo
    // Nariz, ombros, quadris, joelhos, tornozelos e pés
    private val REQUIRED_LANDMARKS = listOf(
        0,              // nose
        11, 12,         // shoulders
        23, 24,         // hips
        25, 26,         // knees
        27, 28,         // ankles
        31, 32          // foot index
    )

    private const val MIN_VISIBILITY   = 0.6f   // visibilidade mínima por landmark
    private const val CENTER_MIN       = 0.30f  // centro horizontal mínimo
    private const val CENTER_MAX       = 0.70f  // centro horizontal máximo
    private const val BODY_HEIGHT_MIN  = 0.50f  // corpo deve ocupar pelo menos 50% da altura
    private const val BODY_HEIGHT_MAX  = 0.60f  // e no máximo 92% (pra não cortar)
    private const val IDEAL_THRESHOLD  = 0.85f  // score mínimo para considerar ideal

    fun evaluate(landmarks: List<NormalizedLandmark>): CameraGuideResult {
        if (landmarks.size < 33) return CameraGuideResult.unknown("Posicione-se na câmera")

        // ── 1. CORPO COMPLETO ─────────────────────────────────────────────────
        // Verifica se todos os landmarks necessários estão visíveis
        val invisibleLandmarks = REQUIRED_LANDMARKS.filter { idx ->
            landmarks[idx].visibility().orElse(0f) < MIN_VISIBILITY
        }

        val bodyScore: Float
        val bodyMessage: String

        if (invisibleLandmarks.isNotEmpty()) {
            // Identifica quais partes estão faltando
            val missingParts = invisibleLandmarks.map { landmarkName(it) }.distinct()
            bodyScore   = 1f - (invisibleLandmarks.size.toFloat() / REQUIRED_LANDMARKS.size)
            bodyMessage = "Faltando: ${missingParts.joinToString(", ")}"
        } else {
            bodyScore   = 1f
            bodyMessage = "✓ Corpo completo"
        }

        // ── 2. DISTÂNCIA / TAMANHO DO CORPO ──────────────────────────────────
        val nose      = landmarks[0]
        val leftFoot  = landmarks[31]
        val rightFoot = landmarks[32]
        val bodyTop   = nose.y()
        val bodyBot   = maxOf(leftFoot.y(), rightFoot.y())
        val bodyHeight = bodyBot - bodyTop

        val distScore: Float
        val distMessage: String

        when {
            bodyHeight < BODY_HEIGHT_MIN -> {
                distScore   = bodyHeight / BODY_HEIGHT_MIN
                distMessage = "Aproxime-se"
            }
            bodyHeight > BODY_HEIGHT_MAX -> {
                distScore   = 1f - (bodyHeight - BODY_HEIGHT_MAX) / (1f - BODY_HEIGHT_MAX)
                distMessage = "Afaste-se"
            }
            bodyHeight < BODY_HEIGHT_MIN + 0.07f -> {
                distScore   = 0.85f
                distMessage = "Um pouco mais perto"
            }
            bodyHeight > BODY_HEIGHT_MAX - 0.05f -> {
                distScore   = 0.85f
                distMessage = "Um pouco mais longe"
            }
            else -> {
                distScore   = 1f
                distMessage = "✓ Distância ideal"
            }
        }

        // ── 3. CENTRALIZAÇÃO HORIZONTAL ───────────────────────────────────────
        val leftShoulder  = landmarks[11]
        val rightShoulder = landmarks[12]
        val leftHip       = landmarks[23]
        val rightHip      = landmarks[24]

        val bodyMidX = (leftShoulder.x() + rightShoulder.x() +
                leftHip.x()      + rightHip.x()) / 4f

        val centerScore: Float
        val centerMessage: String

        when {
            bodyMidX < CENTER_MIN -> {
                centerScore   = bodyMidX / CENTER_MIN
                centerMessage = "Mova a câmera para a esquerda"
            }
            bodyMidX > CENTER_MAX -> {
                centerScore   = 1f - (bodyMidX - CENTER_MAX) / (1f - CENTER_MAX)
                centerMessage = "Mova a câmera para a direita"
            }
            bodyMidX < CENTER_MIN + 0.05f -> {
                centerScore   = 0.85f
                centerMessage = "Centralize um pouco mais"
            }
            bodyMidX > CENTER_MAX - 0.05f -> {
                centerScore   = 0.85f
                centerMessage = "Centralize um pouco mais"
            }
            else -> {
                centerScore   = 1f
                centerMessage = "✓ Centralizado"
            }
        }

        // ── Score geral ───────────────────────────────────────────────────────
        // Corpo completo tem peso maior — é o critério mais importante
        val overallScore = (bodyScore * 0.5f + distScore * 0.3f + centerScore * 0.2f)
        val isIdeal = overallScore >= IDEAL_THRESHOLD &&
                bodyScore == 1f &&       // corpo DEVE estar completo
                distScore >= 0.85f &&    // distância deve estar ok
                centerScore >= 0.85f     // centralização deve estar ok

        return CameraGuideResult(
            bodyScore      = bodyScore,
            bodyMessage    = bodyMessage,
            distanceScore  = distScore,
            distanceMessage= distMessage,
            centerScore    = centerScore,
            centerMessage  = centerMessage,
            overallScore   = overallScore,
            isIdeal        = isIdeal
        )
    }

    private fun landmarkName(index: Int): String = when (index) {
        0        -> "rosto"
        11, 12   -> "ombros"
        23, 24   -> "quadril"
        25, 26   -> "joelhos"
        27, 28   -> "tornozelos"
        31, 32   -> "pés"
        else     -> "ponto $index"
    }
}

data class CameraGuideResult(
    val bodyScore:       Float,
    val bodyMessage:     String,
    val distanceScore:   Float,
    val distanceMessage: String,
    val centerScore:     Float,
    val centerMessage:   String,
    val overallScore:    Float,
    val isIdeal:         Boolean
) {
    companion object {
        fun unknown(msg: String = "Posicione-se na câmera") = CameraGuideResult(
            bodyScore       = 0f,
            bodyMessage     = msg,
            distanceScore   = 0f,
            distanceMessage = "--",
            centerScore     = 0f,
            centerMessage   = "--",
            overallScore    = 0f,
            isIdeal         = false
        )
    }
}