package com.dancaai.app.analysis

import com.dancaai.app.WeightLeg
import com.dancaai.app.analysis.WeightStateMachine.Transition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Sequências sintéticas (razão, tempo) para a regra de apoio e de erro de marcação.
 *
 * O cenário que motivou a máquina: em execuções perfeitas o app acusava erro. As causas
 * eram oscilação na borda da zona (E→N→E contado como o mesmo pé duas vezes) e a faixa
 * neutra larga demais. Os testes de oscilação e de neutro curto travam isso.
 */
class WeightStateMachineTest {

    private val left = 0.80f
    private val right = 0.20f
    private val neutral = 0.50f

    /** Alimenta [ratioAt] de [fromMs] a [toMs) a cada [stepMs] e devolve as transições. */
    private fun WeightStateMachine.run(
        fromMs: Long,
        toMs: Long,
        stepMs: Long = 33,
        inPause: Boolean = false,
        ratioAt: (Long) -> Float?,
    ): List<Transition> {
        val out = mutableListOf<Transition>()
        var t = fromMs
        while (t < toMs) {
            update(ratioAt(t), zDiff = 0f, atMs = t, inPause = inPause)?.let { out += it }
            t += stepMs
        }
        return out
    }

    @Test
    fun alternanciaLimpa_doisAcertosSemErro() {
        val m = WeightStateMachine()
        val out = m.run(0, 1_000) { t ->
            when { t < 300 -> left; t < 600 -> right; else -> left }
        }
        assertEquals(listOf(WeightLeg.LEFT, WeightLeg.RIGHT, WeightLeg.LEFT), out.map { it.leg })
        assertTrue(out.none { it.isError })
    }

    @Test
    fun oscilacaoNaBorda_naoGeraTransicaoNemErro() {
        val m = WeightStateMachine()
        m.run(0, 300) { left }
        // 0,56 / 0,54 cruza o limiar de entrada (0,55) mas fica dentro da histerese (0,52)
        val out = m.run(300, 2_000) { t -> if ((t / 33) % 2 == 0L) 0.56f else 0.54f }
        assertTrue(out.isEmpty())
        assertEquals(WeightLeg.LEFT, m.state)
    }

    @Test
    fun neutroCurtoEntreMesmaPerna_naoEErro() {
        val m = WeightStateMachine()
        m.run(0, 300) { left }
        // ~100 ms em neutro, abaixo dos 150 ms de permanência
        val out = m.run(300, 1_000) { t -> if (t in 300..400) neutral else left }
        assertTrue(out.isEmpty())
    }

    @Test
    fun neutroEstavelEDepoisMesmaPerna_eErro() {
        val m = WeightStateMachine()
        val out = m.run(0, 1_500) { t ->
            when { t < 300 -> left; t < 800 -> neutral; else -> left }
        }
        assertEquals(listOf(WeightLeg.LEFT, WeightLeg.NEUTRAL, WeightLeg.LEFT), out.map { it.leg })
        assertEquals(listOf(false, false, true), out.map { it.isError })
    }

    @Test
    fun depoisDoErro_aPernaOpostaVoltaAAcertar() {
        val m = WeightStateMachine()
        val out = m.run(0, 2_000) { t ->
            when { t < 300 -> left; t < 800 -> neutral; t < 1_100 -> left; else -> right }
        }
        val supports = out.filter { it.leg != WeightLeg.NEUTRAL }
        assertEquals(listOf(WeightLeg.LEFT, WeightLeg.LEFT, WeightLeg.RIGHT), supports.map { it.leg })
        assertEquals(listOf(false, true, false), supports.map { it.isError })
    }

    @Test
    fun picoDeUmQuadro_naoConfirma() {
        val m = WeightStateMachine()
        m.run(0, 300) { left }
        // um único frame em DIREITA: 0 ms de permanência, abaixo dos 30 ms
        val out = m.run(300, 1_000) { t -> if (t < 333) right else left }
        assertTrue(out.isEmpty())
        assertEquals(WeightLeg.LEFT, m.state)
    }

    @Test
    fun doisQuadrosSeguidos_confirmam() {
        val m = WeightStateMachine()
        m.run(0, 300) { left }
        // 2 frames a 33 ms = 33 ms de permanência, já acima dos 30 ms
        val out = m.run(300, 1_000) { t -> if (t < 366) right else left }
        assertEquals(listOf(WeightLeg.RIGHT, WeightLeg.LEFT), out.map { it.leg })
    }

    @Test
    fun mesmoResultadoA10HzEA30Hz() {
        fun legs(stepMs: Long): List<Pair<WeightLeg, Boolean>> {
            val m = WeightStateMachine()
            return m.run(0, 2_000, stepMs = stepMs) { t ->
                when { t < 500 -> left; t < 1_000 -> right; t < 1_500 -> left; else -> right }
            }.map { it.leg to it.isError }
        }
        val a10 = legs(100)
        val a30 = legs(33)
        assertEquals(a30, a10)
        assertEquals(4, a10.size)
        assertTrue(a10.none { it.second })
    }

    @Test
    fun instanteDaTransicaoEOInicioDoCandidato() {
        val m = WeightStateMachine()
        m.update(neutral, 0f, 0)
        val out = mutableListOf<Transition>()
        for (t in listOf(33L, 66L, 100L, 133L)) m.update(left, 0f, t)?.let { out += it }
        // confirma em 133 ms (100 ms depois do início), mas carimba 33
        assertEquals(1, out.size)
        assertEquals(33L, out[0].startedAtMs)
    }

    @Test
    fun pausa_adiaOCommitSemPerderOInstanteDeInicio() {
        val m = WeightStateMachine()
        m.run(0, 300) { left }
        // durante a pausa a razão vai para DIREITA e fica lá
        val inside = m.run(300, 800, inPause = true) { right }
        assertTrue(inside.isEmpty())
        val after = m.run(800, 1_000) { right }
        assertEquals(1, after.size)
        assertEquals(WeightLeg.RIGHT, after[0].leg)
        assertFalse(after[0].isError)          // oposta ao "3" anterior: está tudo certo
        assertEquals(300L, after[0].startedAtMs)
    }

    @Test
    fun pausa_oscilacaoQueVoltaAntesDoFimNaoGeraNada() {
        val m = WeightStateMachine()
        m.run(0, 300) { left }
        val during = m.run(300, 600, inPause = true) { t -> if (t < 450) right else left }
        val after = m.run(600, 1_200) { left }
        assertTrue(during.isEmpty())
        assertTrue(after.isEmpty())
    }

    @Test
    fun pausa_neutroEmTodaAPausaEMesmaPernaNoUm_eErro() {
        val m = WeightStateMachine()
        m.run(0, 300) { left }
        val out = mutableListOf<Transition>()
        out += m.run(300, 800, inPause = true) { neutral }
        out += m.run(800, 1_500) { t -> if (t < 1_000) neutral else left }
        assertEquals(listOf(WeightLeg.NEUTRAL, WeightLeg.LEFT), out.map { it.leg })
        assertTrue(out.last().isError)
    }

    @Test
    fun lacunaDeFrames_descartaCandidatoEmAndamento() {
        val m = WeightStateMachine()
        m.update(left, 0f, 0)
        // candidato LEFT começou em 0; o próximo frame só chega 1 s depois
        assertNull(m.update(left, 0f, 1_000))
        val t = m.update(left, 0f, 1_100)
        assertEquals(WeightLeg.LEFT, t?.leg)
        assertEquals(1_000L, t?.startedAtMs)
    }

    @Test
    fun tornozelosJuntos_contaComoNeutro() {
        val m = WeightStateMachine()
        m.run(0, 300) { left }
        val out = m.run(300, 800) { null }
        assertEquals(listOf(WeightLeg.NEUTRAL), out.map { it.leg })
    }

    @Test
    fun desempatePorZ_soQuandoLigado() {
        val off = WeightStateMachine()
        val on = WeightStateMachine(WeightStateMachine.Config(zTieBreak = true))
        var offOut: Transition? = null
        var onOut: Transition? = null
        for (t in 0L..200L step 33) {
            offOut = off.update(neutral, zDiff = 0.3f, atMs = t) ?: offOut
            onOut = on.update(neutral, zDiff = 0.3f, atMs = t) ?: onOut
        }
        assertNull(offOut)
        assertEquals(WeightLeg.LEFT, onOut?.leg)
    }

    @Test
    fun reset_limpaHistoricoDePernas() {
        val m = WeightStateMachine()
        m.run(0, 300) { left }
        m.reset()
        val out = m.run(0, 300) { left }
        assertEquals(1, out.size)
        assertFalse(out[0].isError)             // sem histórico, repetir LEFT não é erro
    }
}
