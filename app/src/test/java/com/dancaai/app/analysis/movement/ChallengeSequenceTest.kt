package com.dancaai.app.analysis.movement

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChallengeSequenceTest {

    @Test
    fun `o mesmo passo repetido em janelas seguidas conta uma vez`() {
        // O classificador decide a cada 0,5 s e um passo leva 2,5 a 4,0 s: o
        // passo esperado chega repetido. Sem a trava, uma execução correta
        // avançaria a sequência inteira.
        val desafio = ChallengeSequence(listOf(DanceStep.BALANCO, DanceStep.BASE_1))

        assertTrue(desafio.onDecision(DanceStep.BALANCO) is ChallengeSequence.Outcome.Hit)
        assertEquals(ChallengeSequence.Outcome.Waiting, desafio.onDecision(DanceStep.BALANCO))
        assertEquals(ChallengeSequence.Outcome.Waiting, desafio.onDecision(DanceStep.BALANCO))

        assertEquals(1, desafio.position)
        assertEquals(1, desafio.hits)
        assertEquals(DanceStep.BASE_1, desafio.expected)
    }

    @Test
    fun `indecisao nao conta como erro`() {
        // A transição entre dois passos deixa o voto indeciso. Puni-la penalizaria
        // todo par de passos da sequência.
        val desafio = ChallengeSequence(listOf(DanceStep.BALANCO, DanceStep.BASE_1))
        assertEquals(ChallengeSequence.Outcome.Waiting, desafio.onDecision(null))

        assertEquals(0, desafio.misses)
        assertEquals(0, desafio.position)
    }

    @Test
    fun `erro conta mas nao avanca a sequencia`() {
        val desafio = ChallengeSequence(listOf(DanceStep.ABERTURA, DanceStep.BASE_1))
        val resultado = desafio.onDecision(DanceStep.GIRO_LINHA)

        val erro = resultado as ChallengeSequence.Outcome.Miss
        assertEquals(DanceStep.ABERTURA, erro.expected)
        assertEquals(DanceStep.GIRO_LINHA, erro.actual)
        // Continua devendo a abertura: avançar desalinharia o resto do desafio.
        assertEquals(DanceStep.ABERTURA, desafio.expected)
        assertEquals(1, desafio.misses)
        assertEquals(0, desafio.position)
    }

    @Test
    fun `depois de errar ainda da para acertar o passo esperado`() {
        val desafio = ChallengeSequence(listOf(DanceStep.ABERTURA, DanceStep.BASE_1))
        desafio.onDecision(DanceStep.GIRO_LINHA)
        val resultado = desafio.onDecision(DanceStep.ABERTURA)

        assertTrue(resultado is ChallengeSequence.Outcome.Hit)
        assertEquals(1, desafio.hits)
        assertEquals(1, desafio.misses)
    }

    @Test
    fun `o mesmo passo duas vezes na sequencia exige parar entre as duas`() {
        // Com indecisão entre as duas execuções, a segunda conta. É o que
        // acontece de fato: o usuário para, o voto perde a maioria.
        val desafio = ChallengeSequence(listOf(DanceStep.BALANCO, DanceStep.BALANCO))

        assertTrue(desafio.onDecision(DanceStep.BALANCO) is ChallengeSequence.Outcome.Hit)
        desafio.onDecision(null)
        val fim = desafio.onDecision(DanceStep.BALANCO)

        assertEquals(ChallengeSequence.Outcome.Finished(hits = 2, misses = 0), fim)
        assertTrue(desafio.isComplete)
    }

    @Test
    fun `sequencia encerrada para de reagir`() {
        val desafio = ChallengeSequence(listOf(DanceStep.BALANCO))
        desafio.onDecision(DanceStep.BALANCO)

        assertTrue(desafio.isComplete)
        assertEquals(null, desafio.expected)
        assertEquals(ChallengeSequence.Outcome.Waiting, desafio.onDecision(DanceStep.GIRO_LINHA))
        assertEquals(0, desafio.misses)
    }

    @Test
    fun `reset volta ao inicio`() {
        val desafio = ChallengeSequence(ChallengeSequence.DEFAULT)
        desafio.onDecision(DanceStep.BALANCO)
        desafio.onDecision(DanceStep.GIRO_LINHA)
        desafio.reset()

        assertEquals(0, desafio.position)
        assertEquals(0, desafio.hits)
        assertEquals(0, desafio.misses)
        assertFalse(desafio.isComplete)
        assertEquals(DanceStep.BALANCO, desafio.expected)
    }
}
