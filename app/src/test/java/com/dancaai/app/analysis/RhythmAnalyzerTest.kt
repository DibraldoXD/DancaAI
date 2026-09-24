package com.dancaai.app.analysis

import com.dancaai.app.data.model.RhythmTiming
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Cenário base: 120 BPM, ou seja, uma batida a cada 500 ms, com tolerância
 * padrão de ±15% — ±75 ms.
 */
class RhythmAnalyzerTest {

    private val analyzer = RhythmAnalyzer()

    @Test
    fun `sem metronomo nao ha veredito`() {
        assertNull(analyzer.onWeightTransition(atUptimeMs = 1_000))
    }

    @Test
    fun `passo exatamente na batida esta no tempo`() {
        analyzer.onBeat(audibleAtUptimeMs = 1_000, intervalMs = INTERVAL)

        val judgement = analyzer.onWeightTransition(atUptimeMs = 1_000)!!

        assertEquals(RhythmTiming.ON_TIME, judgement.timing)
        assertEquals(0L, judgement.offsetMs)
    }

    @Test
    fun `desvio dentro da tolerancia conta como no tempo`() {
        analyzer.onBeat(audibleAtUptimeMs = 1_000, intervalMs = INTERVAL)

        val judgement = analyzer.onWeightTransition(atUptimeMs = 1_075)!!

        assertEquals(RhythmTiming.ON_TIME, judgement.timing)
        assertEquals(75L, judgement.offsetMs)
    }

    @Test
    fun `passo bem depois da batida esta atrasado`() {
        analyzer.onBeat(audibleAtUptimeMs = 1_000, intervalMs = INTERVAL)

        val judgement = analyzer.onWeightTransition(atUptimeMs = 1_150)!!

        assertEquals(RhythmTiming.LATE, judgement.timing)
        assertEquals(150L, judgement.offsetMs)
    }

    @Test
    fun `passo proximo da batida seguinte esta adiantado`() {
        analyzer.onBeat(audibleAtUptimeMs = 1_000, intervalMs = INTERVAL)

        // 1350 está a 150 ms da próxima batida (1500), não a 350 da anterior:
        // o julgamento é contra o ponto mais próximo da grade
        val judgement = analyzer.onWeightTransition(atUptimeMs = 1_350)!!

        assertEquals(RhythmTiming.EARLY, judgement.timing)
        assertEquals(-150L, judgement.offsetMs)
    }

    @Test
    fun `passo pouco antes da batida seguinte ainda esta no tempo`() {
        analyzer.onBeat(audibleAtUptimeMs = 1_000, intervalMs = INTERVAL)

        val judgement = analyzer.onWeightTransition(atUptimeMs = 1_450)!!

        assertEquals(RhythmTiming.ON_TIME, judgement.timing)
        assertEquals(-50L, judgement.offsetMs)
    }

    @Test
    fun `grade vencida deixa de julgar`() {
        analyzer.onBeat(audibleAtUptimeMs = 1_000, intervalMs = INTERVAL)

        // o praticante desligou o metrônomo e continuou dançando
        assertNull(analyzer.onWeightTransition(atUptimeMs = 1_000 + INTERVAL * 2 + 1))
    }

    @Test
    fun `intervalo entre passos e medido mesmo sem metronomo`() {
        analyzer.onWeightTransition(atUptimeMs = 1_000)
        analyzer.onWeightTransition(atUptimeMs = 1_400)
        analyzer.onWeightTransition(atUptimeMs = 1_800)

        val metrics = analyzer.snapshot()

        // dois intervalos de 400 ms; o IBI do praticante existe sem grade de referência
        assertEquals(2, metrics.transitionIntervalCount)
        assertEquals(400f, metrics.meanTransitionIntervalMs!!, 0.01f)
        assertEquals(0, metrics.judgedTransitions)
    }

    @Test
    fun `contagens e erro medio acumulam ao longo da sessao`() {
        analyzer.onBeat(audibleAtUptimeMs = 1_000, intervalMs = INTERVAL)
        analyzer.onWeightTransition(atUptimeMs = 1_000)   // no tempo, desvio 0
        analyzer.onBeat(audibleAtUptimeMs = 1_500, intervalMs = INTERVAL)
        analyzer.onWeightTransition(atUptimeMs = 1_650)   // atrasado 150
        analyzer.onBeat(audibleAtUptimeMs = 2_000, intervalMs = INTERVAL)
        analyzer.onWeightTransition(atUptimeMs = 2_350)   // adiantado 150

        val metrics = analyzer.snapshot()

        assertEquals(1, metrics.onTimeTransitions)
        assertEquals(1, metrics.lateTransitions)
        assertEquals(1, metrics.earlyTransitions)
        assertEquals(3, metrics.judgedTransitions)
        assertEquals(100f, metrics.meanAbsOffsetMs!!, 0.01f)
        assertEquals(1f / 3f, metrics.onTimeRatio!!, 0.01f)
    }

    @Test
    fun `reset descarta a grade e as contagens`() {
        analyzer.onBeat(audibleAtUptimeMs = 1_000, intervalMs = INTERVAL)
        analyzer.onWeightTransition(atUptimeMs = 1_000)

        analyzer.reset()

        assertTrue(analyzer.snapshot().isEmpty)
        // sem grade, a transferência seguinte volta a não ter veredito
        assertNull(analyzer.onWeightTransition(atUptimeMs = 1_500))
    }

    private companion object {
        const val INTERVAL = 500L // 120 BPM
    }
}
