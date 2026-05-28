package com.dancaai.app

import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import kotlin.math.acos
import kotlin.math.sqrt

object AngleCalculator {

    /**
     * Calcula o ângulo em graus formado por três landmarks.
     * O ponto B é o vértice (articulação central).
     *
     *        A
     *        |
     *        B  ← ângulo calculado aqui
     *        |
     *        C
     */
    fun angle(a: NormalizedLandmark, b: NormalizedLandmark, c: NormalizedLandmark): Float {
        // Vetores BA e BC
        val baX = a.x() - b.x()
        val baY = a.y() - b.y()
        val bcX = c.x() - b.x()
        val bcY = c.y() - b.y()

        // Produto escalar
        val dot = baX * bcX + baY * bcY

        // Magnitudes
        val magBA = sqrt(baX * baX + baY * baY)
        val magBC = sqrt(bcX * bcX + bcY * bcY)

        if (magBA == 0f || magBC == 0f) return 0f

        // Ângulo em graus
        val cosAngle = (dot / (magBA * magBC)).coerceIn(-1f, 1f)
        return Math.toDegrees(acos(cosAngle.toDouble()).toFloat().toDouble()).toFloat()
    }

    // ─── Ângulos principais para dança de salão ───────────────────────────

    /** Ângulo do joelho esquerdo (quadril → joelho → tornozelo) */
    fun leftKnee(lm: List<NormalizedLandmark>): Float =
        angle(lm[23], lm[25], lm[27])  // left_hip → left_knee → left_ankle

    /** Ângulo do joelho direito */
    fun rightKnee(lm: List<NormalizedLandmark>): Float =
        angle(lm[24], lm[26], lm[28])  // right_hip → right_knee → right_ankle

    /** Ângulo do quadril esquerdo (ombro → quadril → joelho) */
    fun leftHip(lm: List<NormalizedLandmark>): Float =
        angle(lm[11], lm[23], lm[25])  // left_shoulder → left_hip → left_knee

    /** Ângulo do quadril direito */
    fun rightHip(lm: List<NormalizedLandmark>): Float =
        angle(lm[12], lm[24], lm[26])  // right_shoulder → right_hip → right_knee

    /** Ângulo do ombro esquerdo (cotovelo → ombro → quadril) */
    fun leftShoulder(lm: List<NormalizedLandmark>): Float =
        angle(lm[13], lm[11], lm[23])  // left_elbow → left_shoulder → left_hip

    /** Ângulo do ombro direito */
    fun rightShoulder(lm: List<NormalizedLandmark>): Float =
        angle(lm[14], lm[12], lm[24])  // right_elbow → right_shoulder → right_hip

    /** Ângulo do cotovelo esquerdo (ombro → cotovelo → pulso) */
    fun leftElbow(lm: List<NormalizedLandmark>): Float =
        angle(lm[11], lm[13], lm[15])  // left_shoulder → left_elbow → left_wrist

    /** Ângulo do cotovelo direito */
    fun rightElbow(lm: List<NormalizedLandmark>): Float =
        angle(lm[12], lm[14], lm[16])  // right_shoulder → right_elbow → right_wrist

    /**
     * Retorna todos os ângulos de uma vez como um objeto de dados.
     * Retorna null se os landmarks não tiverem visibilidade suficiente.
     */
    fun compute(landmarks: List<NormalizedLandmark>): BodyAngles? {
        if (landmarks.size < 29) return null

        // Verifica visibilidade mínima dos landmarks principais
        val keyIndices = listOf(11, 12, 13, 14, 23, 24, 25, 26, 27, 28)
        if (keyIndices.any { (landmarks[it].visibility().orElse(0f)) < 0.4f }) return null

        return BodyAngles(
            leftKnee     = leftKnee(landmarks),
            rightKnee    = rightKnee(landmarks),
            leftHip      = leftHip(landmarks),
            rightHip     = rightHip(landmarks),
            leftShoulder = leftShoulder(landmarks),
            rightShoulder= rightShoulder(landmarks),
            leftElbow    = leftElbow(landmarks),
            rightElbow   = rightElbow(landmarks)
        )
    }
}

data class BodyAngles(
    val leftKnee: Float,
    val rightKnee: Float,
    val leftHip: Float,
    val rightHip: Float,
    val leftShoulder: Float,
    val rightShoulder: Float,
    val leftElbow: Float,
    val rightElbow: Float
)