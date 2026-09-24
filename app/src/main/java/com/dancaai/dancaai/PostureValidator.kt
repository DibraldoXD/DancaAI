package com.dancaai.app

import com.google.mediapipe.tasks.components.containers.NormalizedLandmark

object PostureValidator {

    // Índices MediaPipe
    private const val LEFT_SHOULDER  = 11
    private const val RIGHT_SHOULDER = 12
    private const val LEFT_HIP       = 23
    private const val RIGHT_HIP      = 24

    // Thresholds ajustáveis. SHOULDER_FORWARD_THRESHOLD é internal (não private)
    // de propósito: é a única fonte de verdade do threshold de ombros encurvados,
    // referenciada também no debug do TrainingScreen para não haver drift entre os dois.
    private const val SHOULDER_LEVEL_THRESHOLD_DEG  = 5f    // ângulo da linha de ombros com a horizontal
    internal const val SHOULDER_FORWARD_THRESHOLD    = 0.40f // Zdiff normalizado pelo span (invariante à distância/estatura)
    private const val MIN_FRONTAL_SPAN               = 0.15f // span mínimo — só valida de frente
    private const val LATERAL_TILT_THRESHOLD_DEG     = 10f  // inclinação lateral do tronco em graus

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

        val issues = mutableListOf<PostureIssue>()
        var status = PostureStatus.OK

        // 1. Verifica nivelamento dos ombros — ângulo da linha de ombros com a horizontal
        // Latyshev [11]: desvio MediaPipe nos ombros é 1.6–2.1% da altura — adequado para ângulo.
        // Threshold 5° está acima da margem de erro do sensor (~2°) e dentro do range clínico aceitável.
        val shoulderDY = leftShoulder.y() - rightShoulder.y() // positivo = ombro esq mais baixo
        val shoulderDX = kotlin.math.abs(rightShoulder.x() - leftShoulder.x())
        val shoulderAngleDeg = Math.toDegrees(
            kotlin.math.atan2(kotlin.math.abs(shoulderDY).toDouble(), shoulderDX.toDouble())
        ).toFloat()
        if (shoulderAngleDeg > SHOULDER_LEVEL_THRESHOLD_DEG) {
            if (shoulderDY > 0) issues.add(PostureIssue.LEFT_SHOULDER_DROP)
            else                issues.add(PostureIssue.RIGHT_SHOULDER_DROP)
            status = PostureStatus.WARNING
        }

        // 2. Verifica ombros encurvados — apenas em vista frontal (portão de span)
        // Z normalizado pelo span — invariante à distância câmera e estatura (BlazePose [13]).
        val shoulderSpan = kotlin.math.abs(rightShoulder.x() - leftShoulder.x())
        if (shoulderSpan >= MIN_FRONTAL_SPAN) {
            val avgShoulderZ    = (leftShoulder.z() + rightShoulder.z()) / 2f
            val avgHipZ         = (leftHip.z()       + rightHip.z())      / 2f
            val normalizedZDiff = (avgShoulderZ - avgHipZ) / shoulderSpan
            if (normalizedZDiff < -SHOULDER_FORWARD_THRESHOLD) {
                issues.add(PostureIssue.SHOULDERS_FORWARD)
                status = PostureStatus.WARNING
            }
        }

        // 3. Verifica inclinação lateral do tronco — apenas em vista frontal (portão de span)
        // Calcula o ângulo do segmento quadril→ombro com a vertical via atan2.
        // Latyshev [11]: ombros e quadril têm precisão <3% da altura — adequado para cálculo angular.
        if (shoulderSpan >= MIN_FRONTAL_SPAN) {
            val shoulderMidX = (leftShoulder.x() + rightShoulder.x()) / 2f
            val shoulderMidY = (leftShoulder.y() + rightShoulder.y()) / 2f
            val hipMidX      = (leftHip.x()       + rightHip.x())      / 2f
            val hipMidY      = (leftHip.y()        + rightHip.y())      / 2f
            val dx = shoulderMidX - hipMidX
            val dy = hipMidY - shoulderMidY  // positivo quando quadril está abaixo do ombro (normal)
            val lateralTiltDeg = Math.toDegrees(kotlin.math.atan2(dx.toDouble(), dy.toDouble())).toFloat()
            when {
                lateralTiltDeg >  LATERAL_TILT_THRESHOLD_DEG -> {
                    issues.add(PostureIssue.TRUNK_TILT_LEFT)
                    status = PostureStatus.WARNING
                }
                lateralTiltDeg < -LATERAL_TILT_THRESHOLD_DEG -> {
                    issues.add(PostureIssue.TRUNK_TILT_RIGHT)
                    status = PostureStatus.WARNING
                }
            }
        }

        return if (issues.isEmpty()) {
            PostureResult.Good
        } else {
            PostureResult.Bad(issues, status)
        }
    }
}

/**
 * Desvios posturais que o validador reconhece.
 *
 * O nome da constante é o identificador gravado no banco — estável mesmo que o
 * texto exibido mude. O [label] e o [advice] ficam aqui, junto da regra que
 * detecta o desvio, para que o conselho dado ao praticante não se descole do
 * critério biomecânico que o originou.
 */
enum class PostureIssue(val label: String, val advice: String) {
    LEFT_SHOULDER_DROP(
        "Ombro esquerdo caído",
        "A linha dos ombros pendeu para a esquerda. Procure mantê-la paralela ao chão.",
    ),
    RIGHT_SHOULDER_DROP(
        "Ombro direito caído",
        "A linha dos ombros pendeu para a direita. Procure mantê-la paralela ao chão.",
    ),
    SHOULDERS_FORWARD(
        "Ombros encurvados",
        "Os ombros ficaram à frente do quadril. Abra o peito e leve as escápulas para trás.",
    ),
    TRUNK_TILT_LEFT(
        "Tronco inclinado para a esquerda",
        "O tronco saiu do eixo vertical. Distribua o peso e mantenha o quadril sob os ombros.",
    ),
    TRUNK_TILT_RIGHT(
        "Tronco inclinado para a direita",
        "O tronco saiu do eixo vertical. Distribua o peso e mantenha o quadril sob os ombros.",
    ),
}

enum class PostureStatus { OK, WARNING, ERROR }

sealed class PostureResult {
    object Good    : PostureResult()
    object Unknown : PostureResult()
    data class Bad(val issues: List<PostureIssue>, val status: PostureStatus) : PostureResult()
}