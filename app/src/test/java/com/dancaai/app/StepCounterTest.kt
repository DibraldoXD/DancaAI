package com.dancaai.app

import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Optional

/**
 * Teste ponta a ponta do módulo de peso (landmarks → suavização → máquina de estados).
 * O foco é o que só aparece integrado: a suavização por tempo, que faz o resultado não
 * depender da taxa de quadros, e a janela da pausa.
 */
class StepCounterTest {

    /** Quadril em x = 0,5; tornozelos posicionados para dar a razão pedida (1,0 = peso na esquerda). */
    private fun body(ratio: Float): List<NormalizedLandmark> {
        fun lm(x: Float, y: Float = 0.5f) =
            NormalizedLandmark.create(x, y, 0f, Optional.of(0.9f), Optional.of(0.9f))
        val total = 0.3f
        val list = MutableList(33) { lm(0.5f) }
        list[23] = lm(0.45f, 0.5f)
        list[24] = lm(0.55f, 0.5f)
        list[27] = lm(0.5f - total * (1f - ratio), 0.9f)   // tornozelo esquerdo
        list[28] = lm(0.5f + total * ratio, 0.9f)          // tornozelo direito
        return list
    }

    private fun StepCounter.feed(fromMs: Long, toMs: Long, stepMs: Long, ratioAt: (Long) -> Float) {
        var t = fromMs
        while (t < toMs) {
            processLandmarks(body(ratioAt(t)), t)
            t += stepMs
        }
    }

    private val squareWave: (Long) -> Float = { t ->
        when { t < 600 -> 0.8f; t < 1_200 -> 0.2f; t < 1_800 -> 0.8f; else -> 0.2f }
    }

    @Test
    fun alternanciaPerfeita_semErros() {
        val counter = StepCounter()
        counter.feed(0, 2_400, 33, squareWave)
        assertEquals(4, counter.correctCount)
        assertEquals(0, counter.errorCount)
    }

    @Test
    fun resultadoNaoDependeDaTaxaDeQuadros() {
        val a30 = StepCounter().apply { feed(0, 2_400, 33, squareWave) }
        val a10 = StepCounter().apply { feed(0, 2_400, 100, squareWave) }
        assertEquals(a30.correctCount, a10.correctCount)
        assertEquals(a30.errorCount, a10.errorCount)
    }

    @Test
    fun mesmaPernaDuasVezesComNeutroEstavel_contaErro() {
        val counter = StepCounter()
        counter.feed(0, 2_000, 33) { t ->
            when { t < 500 -> 0.8f; t < 1_100 -> 0.5f; else -> 0.8f }
        }
        assertEquals(1, counter.correctCount)
        assertEquals(1, counter.errorCount)
    }

    @Test
    fun trocaDentroDaPausa_ficaAdiadaEComInstanteDeInicio() {
        val counter = StepCounter()
        val transitions = mutableListOf<Pair<WeightLeg, Long>>()
        counter.onWeightTransition = { leg, at -> transitions += leg to at }
        // pausa em 900 ms, intervalo 500 ms: janela [650, 1150)
        counter.markPauseBeat(audibleAtMs = 900, intervalMs = 500, beatsPerBar = 4)

        counter.feed(0, 1_100, 33, squareWave)
        assertEquals(1, counter.correctCount)           // só o primeiro apoio (ESQUERDA)

        counter.feed(1_100, 1_190, 33, squareWave)
        assertEquals(2, counter.correctCount)           // a janela fechou: a troca foi confirmada
        val (leg, startedAt) = transitions.last()
        assertEquals(WeightLeg.RIGHT, leg)
        assertTrue("início do candidato, não do commit: $startedAt", startedAt in 600L..760L)
        assertEquals(0, counter.errorCount)
    }

    @Test
    fun clearPauseBeat_voltaARegraGeral() {
        val counter = StepCounter()
        counter.markPauseBeat(900, 500, 4)
        counter.clearPauseBeat()
        counter.feed(0, 1_100, 33, squareWave)
        assertEquals(2, counter.correctCount)           // a troca não é mais adiada
    }

    @Test
    fun reset_zeraContadores() {
        val counter = StepCounter()
        counter.feed(0, 2_400, 33, squareWave)
        counter.reset()
        assertEquals(0, counter.correctCount)
        assertEquals(0, counter.errorCount)
    }
}
