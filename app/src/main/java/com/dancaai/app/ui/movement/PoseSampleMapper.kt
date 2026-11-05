package com.dancaai.app.ui.movement

import com.dancaai.app.analysis.movement.Point3
import com.dancaai.app.analysis.movement.PoseSample
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark

/**
 * Índices BlazePose dos 8 pontos que o classificador de passos usa.
 *
 * São os mesmos índices que `toSnapshot()` em `TrainingScreen.kt` usa para montar
 * a linha da planilha — e têm de ser, porque é dessa planilha que o modelo foi
 * treinado. Um índice trocado aqui alimenta o modelo com o joelho no lugar do
 * tornozelo: nenhum erro, resposta errada.
 */
private const val LEFT_SHOULDER = 11
private const val RIGHT_SHOULDER = 12
private const val LEFT_HIP = 23
private const val RIGHT_HIP = 24
private const val LEFT_KNEE = 25
private const val RIGHT_KNEE = 26
private const val LEFT_ANKLE = 27
private const val RIGHT_ANKLE = 28

/**
 * Converte um frame de landmarks do MediaPipe na amostra do classificador.
 *
 * Devolve `null` quando algum dos 8 pontos falta. Preencher ausência com zero —
 * como `toSnapshot()` faz, porque ali o destino é uma planilha que será
 * inspecionada à mão — colocaria um ponto em (0,0,0), o canto da imagem, e isso
 * viraria uma trajetória absurda que o modelo classificaria com confiança.
 */
fun List<NormalizedLandmark>.toPoseSample(frameTimeMs: Long): PoseSample? {
    fun at(index: Int): Point3? =
        getOrNull(index)?.let { Point3(it.x(), it.y(), it.z()) }

    return PoseSample(
        timeMs = frameTimeMs,
        leftShoulder = at(LEFT_SHOULDER) ?: return null,
        rightShoulder = at(RIGHT_SHOULDER) ?: return null,
        leftHip = at(LEFT_HIP) ?: return null,
        rightHip = at(RIGHT_HIP) ?: return null,
        leftKnee = at(LEFT_KNEE) ?: return null,
        rightKnee = at(RIGHT_KNEE) ?: return null,
        leftAnkle = at(LEFT_ANKLE) ?: return null,
        rightAnkle = at(RIGHT_ANKLE) ?: return null,
    )
}
