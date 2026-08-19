package com.dancaai.app.data.model

/** Como uma transferência de peso caiu em relação à grade de batidas do metrônomo. */
enum class RhythmTiming { EARLY, ON_TIME, LATE }

/**
 * Veredito de uma transferência: a classificação e o desvio em milissegundos até
 * o ponto mais próximo da grade. Negativo é adiantado, positivo é atrasado.
 */
data class RhythmJudgement(
    val timing: RhythmTiming,
    val offsetMs: Long,
)

/**
 * Medições brutas do módulo de ritmo ao longo de uma sessão.
 *
 * Como no restante do projeto, são contagens e somas, não pontuação — a fórmula
 * de score entra depois da coleta com praticantes.
 */
data class RhythmMetrics(
    val onTimeTransitions: Int = 0,
    val earlyTransitions: Int = 0,
    val lateTransitions: Int = 0,
    /** Soma dos desvios absolutos; dividida por [judgedTransitions] dá o erro médio. */
    val absOffsetSumMs: Long = 0,
    val transitionIntervalSumMs: Long = 0,
    val transitionIntervalCount: Int = 0,
) {
    val judgedTransitions: Int
        get() = onTimeTransitions + earlyTransitions + lateTransitions

    val onTimeRatio: Float?
        get() = if (judgedTransitions > 0) {
            onTimeTransitions / judgedTransitions.toFloat()
        } else {
            null
        }

    /** Erro absoluto médio em relação à batida, em ms. */
    val meanAbsOffsetMs: Float?
        get() = if (judgedTransitions > 0) {
            absOffsetSumMs / judgedTransitions.toFloat()
        } else {
            null
        }

    /**
     * Intervalo médio entre transferências de peso — o IBI do praticante, que o
     * cronograma pede comparar ao intervalo entre batidas do metrônomo.
     */
    val meanTransitionIntervalMs: Float?
        get() = if (transitionIntervalCount > 0) {
            transitionIntervalSumMs / transitionIntervalCount.toFloat()
        } else {
            null
        }

    val isEmpty: Boolean get() = judgedTransitions == 0 && transitionIntervalCount == 0
}
