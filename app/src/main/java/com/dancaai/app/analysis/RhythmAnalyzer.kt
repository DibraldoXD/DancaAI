package com.dancaai.app.analysis

import com.dancaai.app.data.model.RhythmJudgement
import com.dancaai.app.data.model.RhythmMetrics
import com.dancaai.app.data.model.RhythmTiming
import kotlin.math.abs

/**
 * Compara a transferência de peso do praticante com a grade de batidas do
 * metrônomo.
 *
 * O julgamento é de fase, não de contagem: cada transferência é medida até o
 * ponto mais próximo da grade, o que torna o módulo indiferente a quantos tempos
 * do compasso não recebem passo — no forró o quarto tempo é a quebra, e comparar
 * intervalos brutos confundiria a pausa com atraso.
 *
 * Todos os instantes estão na base de `SystemClock.uptimeMillis`: a batida chega
 * com o instante audível estimado pelo Metronome, e a transferência com o
 * instante de captura do frame, não o da inferência.
 */
class RhythmAnalyzer(
    private val toleranceFraction: Float = DEFAULT_TOLERANCE_FRACTION,
) {
    companion object {
        /**
         * Tolerância como fração do intervalo entre batidas — ±15% equivale a
         * ±75 ms a 120 BPM. Proporcional em vez de fixa em ms para não ficar
         * severa demais em andamento lento. É um limiar a calibrar nas sessões
         * piloto (atividade A.3.3 do cronograma).
         */
        const val DEFAULT_TOLERANCE_FRACTION = 0.15f

        /**
         * Passada esta distância da última batida conhecida, a grade é
         * considerada vencida e nada é julgado. Cobre o caso de o praticante
         * continuar dançando depois de desligar o metrônomo.
         */
        private const val MAX_BEAT_AGE_FACTOR = 2
    }

    private var lastBeatUptimeMs: Long? = null
    private var beatIntervalMs: Long? = null
    private var lastTransitionUptimeMs: Long? = null

    private var onTime = 0
    private var early = 0
    private var late = 0
    private var absOffsetSumMs = 0L
    private var transitionIntervalSumMs = 0L
    private var transitionIntervalCount = 0

    fun onBeat(audibleAtUptimeMs: Long, intervalMs: Long) {
        lastBeatUptimeMs = audibleAtUptimeMs
        beatIntervalMs = intervalMs.takeIf { it > 0 }
    }

    /**
     * Julga uma transferência de peso. Devolve nulo quando não há grade de
     * batidas válida — metrônomo desligado ou parado há tempo demais.
     */
    fun onWeightTransition(atUptimeMs: Long): RhythmJudgement? {
        lastTransitionUptimeMs?.let { previous ->
            val delta = atUptimeMs - previous
            if (delta > 0) {
                transitionIntervalSumMs += delta
                transitionIntervalCount++
            }
        }
        lastTransitionUptimeMs = atUptimeMs

        val beat = lastBeatUptimeMs ?: return null
        val interval = beatIntervalMs ?: return null
        if (atUptimeMs - beat > interval * MAX_BEAT_AGE_FACTOR) return null

        // Distância até o ponto mais próximo da grade, que pode ser a batida
        // seguinte: um passo adiantado acontece antes de ela soar, e aí o desvio
        // é negativo. O resto duplo mantém o cálculo correto se a transferência
        // vier de um frame anterior à batida de referência.
        val elapsed = atUptimeMs - beat
        val positionInBeat = ((elapsed % interval) + interval) % interval
        val offsetMs = if (positionInBeat > interval / 2) positionInBeat - interval else positionInBeat

        val timing = when {
            abs(offsetMs) <= (interval * toleranceFraction).toLong() -> RhythmTiming.ON_TIME
            offsetMs < 0 -> RhythmTiming.EARLY
            else -> RhythmTiming.LATE
        }

        when (timing) {
            RhythmTiming.ON_TIME -> onTime++
            RhythmTiming.EARLY -> early++
            RhythmTiming.LATE -> late++
        }
        absOffsetSumMs += abs(offsetMs)

        return RhythmJudgement(timing, offsetMs)
    }

    fun snapshot(): RhythmMetrics = RhythmMetrics(
        onTimeTransitions = onTime,
        earlyTransitions = early,
        lateTransitions = late,
        absOffsetSumMs = absOffsetSumMs,
        transitionIntervalSumMs = transitionIntervalSumMs,
        transitionIntervalCount = transitionIntervalCount,
    )

    fun reset() {
        lastBeatUptimeMs = null
        beatIntervalMs = null
        lastTransitionUptimeMs = null
        onTime = 0
        early = 0
        late = 0
        absOffsetSumMs = 0L
        transitionIntervalSumMs = 0L
        transitionIntervalCount = 0
    }
}
