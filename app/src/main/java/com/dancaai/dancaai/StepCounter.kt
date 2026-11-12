package com.dancaai.app

import android.os.SystemClock
import com.dancaai.app.analysis.PauseWindow
import com.dancaai.app.analysis.WeightStateMachine
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult
import kotlin.math.abs
import kotlin.math.exp

enum class WeightLeg { LEFT, RIGHT, NEUTRAL }

enum class MovementDirection { LEFT, RIGHT, UP, DOWN, NEUTRAL }

data class WeightInfo(
    val leg: WeightLeg,
    val direction: MovementDirection,
    val showError: Boolean,
    val correctCount: Int,
    val errorCount: Int
)

/**
 * Um frame do módulo de peso, para diagnóstico: o que entrou, o que a máquina decidiu e
 * se a pausa do compasso estava aberta. Só é montado quando [StepCounter.onTrace] está
 * ligado (build debug).
 */
data class WeightTrace(
    val atMs: Long,
    /** Razão com as posições cruas deste frame (sem suavização); `null` se tornozelos juntos. */
    val rawRatio: Float?,
    /** Razão suavizada, a que a máquina de estados lê. */
    val smoothRatio: Float?,
    val zDiff: Float,
    /** Apoio confirmado depois deste frame. */
    val state: WeightLeg,
    /** Apoio confirmado NESTE frame, ou `null` se não houve troca. */
    val transition: WeightLeg?,
    val isError: Boolean,
    val inPause: Boolean,
)

/**
 * Transferência de peso: extrai os landmarks, suaviza e entrega a razão para a
 * [WeightStateMachine], que decide o apoio confirmado e o que é erro de marcação.
 *
 *  leftRatio = distToRightAnkle / (distToLeft + distToRight)   [eixo X]
 *      • 1.0 = quadril sobre tornozelo esquerdo → ESQUERDA
 *      • 0.0 = quadril sobre tornozelo direito  → DIREITA
 *      • Zona neutra: 0.45–0.55, com histerese e permanência mínima (ver a máquina)
 *
 * A suavização é uma EMA por constante de tempo ([SMOOTHING_TAU_MS]) e não por frame:
 * com ~30 Hz no app e 10 Hz nas gravações da planilha, um alpha fixo por frame daria
 * filtros diferentes e a calibração offline não valeria para o aparelho.
 *
 * Os limiares de [WeightStateMachine.Config] foram calibrados sobre as gravações de
 * balanço, base 1 e base 2 da planilha (24 gravações, 274 s, execuções corretas, ou seja,
 * todo erro ali é falso positivo) com `modelo_postura/calibrar_marcacao.py`: 87 → 3 erros
 * e neutro de 20,1% → 1,6% a 30 Hz. O que sobra são transferências parciais e pés juntos
 * (ver o log de marcação do build debug). Não há gravação de erro verdadeiro, então a
 * sensibilidade a erros reais não foi medida, só a especificidade.
 */
class StepCounter {

    companion object {
        private const val LEFT_HIP    = 23
        private const val RIGHT_HIP   = 24
        private const val LEFT_ANKLE  = 27
        private const val RIGHT_ANKLE = 28

        private const val MIN_TOTAL_DIST = 0.008f  // evita divisão por zero
        private const val DIR_THRESHOLD  = 0.002f

        /** alpha = 1 − exp(−Δt/τ). τ = 77 ms reproduz o alpha de 0,35 do filtro antigo a 30 Hz. */
        private const val SMOOTHING_TAU_MS = 77f
        private const val MAX_SMOOTHING_DT_MS = 1_000L

        /** Quanto tempo o aviso de erro fica na tela (antes: 60 frames). */
        private const val ERROR_DISPLAY_MS = 2_000L
    }

    var counter: Int = 0
        private set
    var stage: String? = null
        private set
    var correctCount: Int = 0
        private set
    var errorCount: Int = 0
        private set

    var onThreeRepsCompleted: (() -> Unit)? = null
    var onStageChanged: ((stage: String, counter: Int) -> Unit)? = null
    var onWeightInfoChanged: ((info: WeightInfo) -> Unit)? = null

    /**
     * Toda troca de perna de apoio, correta ou não, com o instante em que ela COMEÇOU
     * (não o do commit, que espera a permanência mínima). É a referência do módulo de
     * ritmo para comparar com as batidas.
     */
    var onWeightTransition: ((leg: WeightLeg, atUptimeMs: Long) -> Unit)? = null

    /** Diagnóstico por frame; `null` (padrão) não custa nada. Ligado só no build debug. */
    var onTrace: ((WeightTrace) -> Unit)? = null

    private val machine = WeightStateMachine()
    private val pauseWindow = PauseWindow()
    private var errorShownUntilMs = Long.MIN_VALUE

    private var lastFrameMs: Long? = null
    private var sHipCX:   Float? = null
    private var sHipCY:   Float? = null
    private var sAnkleLX: Float? = null
    private var sAnkleRX: Float? = null
    private var sAnkleLZ: Float? = null
    private var sAnkleRZ: Float? = null
    private var prevHipCX: Float? = null
    private var prevHipCY: Float? = null

    /**
     * Informa a batida de pausa do metrônomo (1-2-3-Pausa). Enquanto a janela da
     * pausa estiver aberta a máquina adia qualquer troca de perna: o pé livre
     * encosta ou alivia ali e não é marcação. Ver [PauseWindow].
     */
    fun markPauseBeat(audibleAtMs: Long, intervalMs: Long, beatsPerBar: Int) =
        pauseWindow.mark(audibleAtMs, intervalMs, beatsPerBar)

    /** Metrônomo parado ou padrão sem pausa: volta a regra geral. */
    fun clearPauseBeat() = pauseWindow.clear()

    fun process(result: PoseLandmarkerResult, atUptimeMs: Long = SystemClock.uptimeMillis()) {
        val lm = result.landmarks().firstOrNull()
        if (lm == null) { emitInfo(MovementDirection.NEUTRAL, atUptimeMs); return }
        processLandmarks(lm, atUptimeMs)
    }

    /** Ponto de entrada sem MediaPipe: os testes de unidade alimentam landmarks sintéticos. */
    fun processLandmarks(lm: List<NormalizedLandmark>, atUptimeMs: Long) {
        val hipL   = lm.getOrNull(LEFT_HIP)    ?: run { emitInfo(MovementDirection.NEUTRAL, atUptimeMs); return }
        val hipR   = lm.getOrNull(RIGHT_HIP)   ?: run { emitInfo(MovementDirection.NEUTRAL, atUptimeMs); return }
        val ankleL = lm.getOrNull(LEFT_ANKLE)  ?: run { emitInfo(MovementDirection.NEUTRAL, atUptimeMs); return }
        val ankleR = lm.getOrNull(RIGHT_ANKLE) ?: run { emitInfo(MovementDirection.NEUTRAL, atUptimeMs); return }

        val dt = lastFrameMs?.let { (atUptimeMs - it).coerceIn(1L, MAX_SMOOTHING_DT_MS) }
        val alpha = dt?.let { 1f - exp(-it / SMOOTHING_TAU_MS) } ?: 1f
        lastFrameMs = atUptimeMs
        fun ema(prev: Float?, cur: Float) = prev?.let { it + (cur - it) * alpha } ?: cur

        prevHipCX = sHipCX
        prevHipCY = sHipCY
        sHipCX   = ema(sHipCX,   (hipL.x() + hipR.x()) / 2f)
        sHipCY   = ema(sHipCY,   (hipL.y() + hipR.y()) / 2f)
        sAnkleLX = ema(sAnkleLX, ankleL.x())
        sAnkleRX = ema(sAnkleRX, ankleR.x())
        sAnkleLZ = ema(sAnkleLZ, ankleL.z())
        sAnkleRZ = ema(sAnkleRZ, ankleR.z())

        val hipX  = sHipCX!!
        val ankLX = sAnkleLX!!
        val ankRX = sAnkleRX!!

        // Distância horizontal do quadril a cada tornozelo
        val distL     = abs(hipX - ankLX)
        val distR     = abs(hipX - ankRX)
        val totalDist = distL + distR
        val leftRatio = if (totalDist > MIN_TOTAL_DIST) distR / totalDist else null  // 1.0 = peso esquerda
        val zDiff     = sAnkleRZ!! - sAnkleLZ!!

        // Direção pelo delta do quadril suavizado
        val direction: MovementDirection = prevHipCX?.let { px ->
            val dx = hipX - px
            val dy = sHipCY!! - prevHipCY!!
            when {
                abs(dx) >= abs(dy) && abs(dx) > DIR_THRESHOLD ->
                    if (dx > 0) MovementDirection.RIGHT else MovementDirection.LEFT
                abs(dy) >  abs(dx) && abs(dy) > DIR_THRESHOLD ->
                    if (dy > 0) MovementDirection.DOWN  else MovementDirection.UP
                else -> MovementDirection.NEUTRAL
            }
        } ?: MovementDirection.NEUTRAL

        val inPause = pauseWindow.contains(atUptimeMs)
        val transition = machine.update(leftRatio, zDiff, atUptimeMs, inPause = inPause)
        onTrace?.let { trace ->
            val rawL = abs((hipL.x() + hipR.x()) / 2f - ankleL.x())
            val rawR = abs((hipL.x() + hipR.x()) / 2f - ankleR.x())
            val rawTotal = rawL + rawR
            trace(
                WeightTrace(
                    atMs = atUptimeMs,
                    rawRatio = if (rawTotal > MIN_TOTAL_DIST) rawR / rawTotal else null,
                    smoothRatio = leftRatio,
                    zDiff = zDiff,
                    state = machine.state,
                    transition = transition?.leg,
                    isError = transition?.isError == true,
                    inPause = inPause,
                ),
            )
        }
        if (transition != null && transition.leg != WeightLeg.NEUTRAL) {
            if (transition.isError) {
                errorCount++
                errorShownUntilMs = atUptimeMs + ERROR_DISPLAY_MS
            } else {
                correctCount++
                counter++
                stage = if (transition.leg == WeightLeg.LEFT) "esquerda" else "direita"
                checkReps()
                onStageChanged?.invoke(stage!!, counter)
            }
            onWeightTransition?.invoke(transition.leg, transition.startedAtMs)
        }

        emitInfo(direction, atUptimeMs)
    }

    private fun emitInfo(direction: MovementDirection, atMs: Long) {
        onWeightInfoChanged?.invoke(
            WeightInfo(
                leg          = machine.state,
                direction    = direction,
                showError    = atMs < errorShownUntilMs,
                correctCount = correctCount,
                errorCount   = errorCount
            )
        )
    }

    private fun checkReps() {
        if (counter == 4) { onThreeRepsCompleted?.invoke(); counter = 1 }
    }

    fun reset() {
        counter = 0; stage = null
        correctCount = 0; errorCount = 0
        machine.reset()
        errorShownUntilMs = Long.MIN_VALUE
        lastFrameMs = null
        sHipCX = null; sHipCY = null
        sAnkleLX = null; sAnkleRX = null
        sAnkleLZ = null; sAnkleRZ = null
        prevHipCX = null; prevHipCY = null
    }
}
