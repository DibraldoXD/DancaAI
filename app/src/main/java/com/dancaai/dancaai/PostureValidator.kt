package com.dancaai.app

import com.google.mediapipe.tasks.components.containers.NormalizedLandmark

object PostureValidator {

    // Índices MediaPipe
    private const val LEFT_SHOULDER  = 11
    private const val RIGHT_SHOULDER = 12
    private const val LEFT_HIP       = 23
    private const val RIGHT_HIP      = 24

    // Thresholds ajustáveis
    private const val SHOULDER_LEVEL_THRESHOLD  = 1.0f   // desativado temporariamente
    private const val SHOULDER_FORWARD_THRESHOLD = 0.23f // diferença Z ombros–quadril para alertar
    private const val MIN_FRONTAL_SPAN           = 0.15f // span mínimo — só valida de frente

    fun validate(landmarks: List<NormalizedLandmark>): PostureResult {
        if (landmarks.size < 25) return PostureResult.Unknown

        val leftShoulder  = landmarks[LEFT_SHOULDER]
        val rightShoulder = landmarks[RIGHT_SHOULDER]
        val leftHip       = landmarks[LEFT_HIP]
        val rightHip      = landmarks[RIGHT_HIP]

        // Verifica visibilidade mínima
        val minVisibility = 0.4f
        if (listOf(leftShoulder, rightShoulder, leftHip, rightHip)
                .any { it.visibility().orElse(0f) < minVisibility }
        ) return PostureResult.Unknown

        val issues = mutableListOf<String>()
        var status = PostureStatus.OK

        // 1. Verifica nivelamento dos ombros (eixo Y)
        val shoulderYDiff = leftShoulder.y() - rightShoulder.y()
        when {
            shoulderYDiff > SHOULDER_LEVEL_THRESHOLD -> {
                issues.add("OMBRO ESQUERDO CAÍDO")
                status = PostureStatus.WARNING
            }
            shoulderYDiff < -SHOULDER_LEVEL_THRESHOLD -> {
                issues.add("OMBRO DIREITO CAÍDO")
                status = PostureStatus.WARNING
            }
        }

        // 2. Verifica ombros encurvados — apenas em vista frontal (portão de span)
        val shoulderSpan = kotlin.math.abs(rightShoulder.x() - leftShoulder.x())
        if (shoulderSpan >= MIN_FRONTAL_SPAN) {
            val avgShoulderZ = (leftShoulder.z() + rightShoulder.z()) / 2f
            val avgHipZ      = (leftHip.z() + rightHip.z()) / 2f
            if (avgShoulderZ - avgHipZ < -SHOULDER_FORWARD_THRESHOLD) {
                issues.add("OMBROS ENCURVADOS")
                status = PostureStatus.WARNING
            }
        }

        // 3. Verifica alinhamento lateral (ombros centralizados sobre quadril)
        val shoulderMidX = (leftShoulder.x() + rightShoulder.x()) / 2f
        val hipMidX      = (leftHip.x() + rightHip.x()) / 2f
        val lateralDiff  = shoulderMidX - hipMidX

        if (kotlin.math.abs(lateralDiff) > 0.5f) {
            val side = if (lateralDiff > 0) "ESQUERDA" else "DIREITA"
            issues.add("TRONCO INCLINADO PARA $side")
            status = PostureStatus.WARNING
        }

        return if (issues.isEmpty()) {
            PostureResult.Good
        } else {
            PostureResult.Bad(issues, status)
        }
    }
}

enum class PostureStatus { OK, WARNING, ERROR }

sealed class PostureResult {
    object Good    : PostureResult()
    object Unknown : PostureResult()
    data class Bad(val issues: List<String>, val status: PostureStatus) : PostureResult()
}