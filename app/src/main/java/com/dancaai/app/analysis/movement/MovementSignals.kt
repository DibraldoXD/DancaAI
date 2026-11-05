package com.dancaai.app.analysis.movement

import kotlin.math.abs
import kotlin.math.hypot

/**
 * Ponto normalizado do MediaPipe (x, y em fração da imagem; z estimado).
 *
 * `y` cresce PARA BAIXO, como nas coordenadas normalizadas do MediaPipe — vários
 * sinais abaixo dependem disso e invertem de significado se alguém "corrigir".
 */
data class Point3(val x: Float, val y: Float, val z: Float)

/**
 * Os 8 pontos corporais que o classificador de passos usa, num instante.
 *
 * São 8 e não os 11 do contrato da planilha: nariz e orelhas não entram em
 * nenhum dos sinais de movimento. Pedir só o que se usa deixa a dependência
 * explícita e o teste unitário pequeno.
 *
 * [timeMs] é o instante de captura do frame, na base de `SystemClock.uptimeMillis`
 * (a mesma que `PoseLandmarkerHelper.ResultBundle.frameTimeMs` entrega e que o
 * metrônomo usa).
 */
data class PoseSample(
    val timeMs: Long,
    val leftShoulder: Point3,
    val rightShoulder: Point3,
    val leftHip: Point3,
    val rightHip: Point3,
    val leftKnee: Point3,
    val rightKnee: Point3,
    val leftAnkle: Point3,
    val rightAnkle: Point3,
)

/**
 * Os sinais de um quadro, já invariantes a distância da câmera e a estatura.
 *
 * Tudo é dividido pelo COMPRIMENTO DO TRONCO, nunca pelo span de ombros. O span
 * projetado na imagem tende a zero quando o dançarino fica de perfil, e dividir
 * por quase zero explode as features exatamente nos quadros de rotação — que são
 * os que distinguem abertura de giro em linha. Trocar essa base de normalização
 * levou a acurácia do classificador de 0,789 para 0,941 na validação entre
 * participantes (ver `training/movement_classifier/README.md`).
 *
 * Os quatro campos `v*` são velocidades e NÃO saem de um quadro isolado: quem os
 * preenche é [MovementWindow], que tem acesso ao quadro anterior.
 */
data class FrameSignals(
    val timeMs: Long,
    /** Rotação do tronco, COM SINAL. Ver [rotationOf]. */
    val yaw: Float,
    val yawHip: Float,
    val feetSpread: Float,
    val feetDx: Float,
    val feetDy: Float,
    val weightLateral: Float,
    val hipHeight: Float,
    val kneeDy: Float,
    val shoulderDy: Float,
    val vYaw: Float = 0f,
    val vFeetSpread: Float = 0f,
    val vWeightLateral: Float = 0f,
    val vHipHeight: Float = 0f,
) {
    /** Na ordem de `SINAIS_CORPO` em `features.py`. Mudar a ordem invalida o modelo. */
    fun asList(): FloatArray = floatArrayOf(
        yaw, yawHip, feetSpread, feetDx, feetDy, weightLateral,
        hipHeight, kneeDy, shoulderDy, vYaw, vFeetSpread, vWeightLateral, vHipHeight,
    )

    companion object {
        /** Nomes na mesma ordem de [asList], espelhando `SINAIS_CORPO` do Python. */
        val NAMES = listOf(
            "yaw", "yaw_quadril", "abertura_pes", "pes_dx", "pes_dy", "peso_lateral",
            "altura_quadril", "joelho_dy", "ombro_dy",
            "v_yaw", "v_abertura_pes", "v_peso_lateral", "v_altura_quadril",
        )
        const val COUNT = 13
    }
}

/**
 * Converte um quadro de pose nos sinais do classificador.
 *
 * Devolve `null` quando o tronco mede praticamente zero — pessoa deitada, fora
 * de quadro ou com landmarks colapsados. Nesse caso toda razão abaixo estouraria,
 * e uma janela montada com esses valores seria ruído com aparência de feature.
 */
fun signalsOf(sample: PoseSample): FrameSignals? {
    val shoulderMid = midpoint(sample.leftShoulder, sample.rightShoulder)
    val hipMid = midpoint(sample.leftHip, sample.rightHip)
    val feetMid = midpoint(sample.leftAnkle, sample.rightAnkle)

    val torso = hypot(shoulderMid.x - hipMid.x, shoulderMid.y - hipMid.y)
    if (torso < MIN_TORSO) return null

    return FrameSignals(
        timeMs = sample.timeMs,
        // ASSINADO. O módulo dobraria a curva de rotação no zero e transformaria
        // uma rotação suave em oscilação de aparência aleatória — é por isso que
        // o `shoulderSpan` do TrainingScreen, que usa abs(), não serve aqui.
        yaw = (sample.rightShoulder.x - sample.leftShoulder.x) / torso,
        yawHip = (sample.rightHip.x - sample.leftHip.x) / torso,
        feetSpread = hypot(
            sample.leftAnkle.x - sample.rightAnkle.x,
            sample.leftAnkle.y - sample.rightAnkle.y,
        ) / torso,
        feetDx = (sample.rightAnkle.x - sample.leftAnkle.x) / torso,
        feetDy = (sample.leftAnkle.y - sample.rightAnkle.y) / torso,
        // Transferência de peso: deslocamento lateral do quadril em relação ao
        // ponto médio dos pés — a mesma grandeza que o módulo de peso observa.
        weightLateral = (hipMid.x - feetMid.x) / torso,
        // y cresce para baixo, então isto é positivo e mede a altura do quadril
        // sobre os pés.
        hipHeight = (feetMid.y - hipMid.y) / torso,
        kneeDy = (sample.leftKnee.y - sample.rightKnee.y) / torso,
        shoulderDy = (sample.leftShoulder.y - sample.rightShoulder.y) / torso,
    )
}

/**
 * Calibra o yaw em radianos, dividindo pela amplitude frontal observada.
 *
 * `yaw` cru é proporcional ao cosseno do ângulo de rotação, não ao ângulo: a
 * razão ombro/tronco varia de 0,80 a 0,95 entre pessoas de biotipo diferente, e
 * crua essa variação entraria como se fosse sinal.
 *
 * [reference] é a amplitude de |yaw| de frente, que no treino saiu do percentil
 * 95 da gravação inteira. **Só use esta função se o modelo embarcado tiver sido
 * treinado com calibração**; o modelo sem calibração espera o valor cru, e
 * aplicar `arcsin` nele daria entrada fora da escala do treino, sem erro nenhum
 * e com saída errada.
 */
fun calibratedYaw(yaw: Float, reference: Float): Float {
    val safe = if (abs(reference) < 1e-6f) 1e-6f else abs(reference)
    return kotlin.math.asin((yaw / safe).coerceIn(-1f, 1f))
}

private fun midpoint(a: Point3, b: Point3) = Point3(
    (a.x + b.x) / 2f, (a.y + b.y) / 2f, (a.z + b.z) / 2f,
)

/**
 * Tronco mínimo para o quadro valer. Em fração da altura da imagem: abaixo disto
 * a pessoa ocupa tão pouco quadro que as razões perdem significado.
 */
private const val MIN_TORSO = 0.02f
