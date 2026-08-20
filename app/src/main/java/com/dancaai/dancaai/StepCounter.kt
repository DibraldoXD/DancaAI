package com.dancaai.app

import android.os.SystemClock
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult
import kotlin.math.abs

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
 * Transferência de peso em duas camadas:
 *
 *  1ª) leftRatio = distToRightAnkle / (distToLeft + distToRight)   [eixo X]
 *      • 1.0 = quadril sobre tornozelo esquerdo → ESQUERDA
 *      • 0.0 = quadril sobre tornozelo direito  → DIREITA
 *      • Zona neutra: 0.40–0.60
 *
 *  2ª) Desempate por Z dos tornozelos (só na zona neutra)
 *      • zD − zE > +Z_TIEBREAK → tornozelo D mais longe = E mais à frente → ESQUERDA
 *      • zD − zE < −Z_TIEBREAK → tornozelo E mais longe = D mais à frente → DIREITA
 *      Resolve o caso em que o corpo se move pra frente/trás: a perspectiva
 *      encolhe o leftRatio para a zona neutra, mas o Z ainda indica qual pé está
 *      à frente (e portanto com peso).
 *
 * Validado em 18 capturas / 3 usuários: 18/18 corretos com as duas camadas.
 */
class StepCounter {

    companion object {
        private const val LEFT_HIP    = 23
        private const val RIGHT_HIP   = 24
        private const val LEFT_ANKLE  = 27
        private const val RIGHT_ANKLE = 28

        private const val UPPER_RATIO    = 0.60f   // acima → ESQUERDA  (camada 1)
        private const val LOWER_RATIO    = 0.40f   // abaixo → DIREITA   (camada 1)
        private const val MIN_TOTAL_DIST = 0.008f  // evita divisão por zero
        private const val Z_TIEBREAK    = 0.10f   // |zD−zE| mínimo para desempate (camada 2)

        private const val ALPHA         = 0.35f   // EMA de suavização (~3 frames de lag)
        private const val DIR_THRESHOLD = 0.002f
        private const val HISTORY_SIZE  = 8
        private const val ERROR_DISPLAY_FRAMES = 60
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
     * Toda troca de perna de apoio, correta ou não, com o instante do frame que a
     * revelou. É a referência do módulo de ritmo para comparar com as batidas.
     */
    var onWeightTransition: ((leg: WeightLeg, atUptimeMs: Long) -> Unit)? = null

    private val legHistory = ArrayDeque<WeightLeg>()
    private var currentLeg = WeightLeg.NEUTRAL
    private var errorCountdown = 0

    private var sHipCX:   Float? = null
    private var sHipCY:   Float? = null
    private var sAnkleLX: Float? = null
    private var sAnkleRX: Float? = null
    private var sAnkleLZ: Float? = null
    private var sAnkleRZ: Float? = null
    private var prevHipCX: Float? = null
    private var prevHipCY: Float? = null

    fun process(result: PoseLandmarkerResult, atUptimeMs: Long = SystemClock.uptimeMillis()) {
        if (result.landmarks().isEmpty()) { emitInfo(MovementDirection.NEUTRAL); return }
        val lm = result.landmarks()[0]

        val hipL   = lm.getOrNull(LEFT_HIP)    ?: run { emitInfo(MovementDirection.NEUTRAL); return }
        val hipR   = lm.getOrNull(RIGHT_HIP)   ?: run { emitInfo(MovementDirection.NEUTRAL); return }
        val ankleL = lm.getOrNull(LEFT_ANKLE)  ?: run { emitInfo(MovementDirection.NEUTRAL); return }
        val ankleR = lm.getOrNull(RIGHT_ANKLE) ?: run { emitInfo(MovementDirection.NEUTRAL); return }

        fun ema(prev: Float?, cur: Float) = prev?.let { it * (1f - ALPHA) + cur * ALPHA } ?: cur

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

        val newLeg = if (totalDist > MIN_TOTAL_DIST) {
            val leftRatio = distR / totalDist   // 1.0 = peso esquerda, 0.0 = peso direita
            when {
                leftRatio > UPPER_RATIO -> WeightLeg.LEFT
                leftRatio < LOWER_RATIO -> WeightLeg.RIGHT
                else -> {
                    // Zona neutra: desempate pelo Z dos tornozelos.
                    // Quando o corpo avança/recua, a perspectiva encolhe o leftRatio,
                    // mas o Z indica qual pé está à frente (e portanto com peso).
                    // zD − zE > 0 → tornozelo D mais longe = pé E à frente → ESQUERDA
                    val zDiff = sAnkleRZ!! - sAnkleLZ!!
                    when {
                        zDiff >  Z_TIEBREAK -> WeightLeg.LEFT
                        zDiff < -Z_TIEBREAK -> WeightLeg.RIGHT
                        else                -> WeightLeg.NEUTRAL
                    }
                }
            }
        } else WeightLeg.NEUTRAL

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

        // Transição + histórico de ciclo
        if (newLeg != WeightLeg.NEUTRAL && newLeg != currentLeg) {
            val isError = legHistory.isNotEmpty() && legHistory.last() == newLeg
            if (isError) {
                errorCount++
                errorCountdown = ERROR_DISPLAY_FRAMES
            } else {
                correctCount++
                counter++
                stage = if (newLeg == WeightLeg.LEFT) "esquerda" else "direita"
                checkReps()
                onStageChanged?.invoke(stage!!, counter)
            }
            if (legHistory.size >= HISTORY_SIZE) legHistory.removeFirst()
            legHistory.addLast(newLeg)
            onWeightTransition?.invoke(newLeg, atUptimeMs)
        }

        currentLeg = newLeg
        emitInfo(direction)
    }

    private fun emitInfo(direction: MovementDirection) {
        if (errorCountdown > 0) errorCountdown--
        onWeightInfoChanged?.invoke(
            WeightInfo(
                leg          = currentLeg,
                direction    = direction,
                showError    = errorCountdown > 0,
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
        currentLeg = WeightLeg.NEUTRAL
        legHistory.clear(); errorCountdown = 0
        sHipCX = null; sHipCY = null
        sAnkleLX = null; sAnkleRX = null
        sAnkleLZ = null; sAnkleRZ = null
        prevHipCX = null; prevHipCY = null
    }
}
