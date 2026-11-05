package com.dancaai.app.analysis.movement

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MovementVoteTest {

    private fun prediction(step: DanceStep, confidence: Float = 0.9f): StepPrediction {
        val probabilities = FloatArray(DanceStep.entries.size)
        probabilities[step.ordinal] = confidence
        return StepPrediction(step, confidence, probabilities)
    }

    @Test
    fun `ordem das classes casa com os tokens de coleta`() {
        // Se alguém renomear ou reordenar um token `mov_*` em CaptureSubject, o
        // modelo passa a prever uma classe que não existe mais e nada acusa.
        assertTrue(
            "A ordem de DanceStep divergiu dos tokens mov_* de CaptureSubject",
            DanceStep.matchesCaptureTokens(),
        )
    }

    @Test
    fun `sem janelas o voto fica indeciso`() {
        assertEquals(VoteResult.Undecided, MovementVote().current())
    }

    @Test
    fun `maioria decide`() {
        val vote = MovementVote(windowCount = 4, minimumAgreement = 0.5f)
        vote.add(prediction(DanceStep.BALANCO))
        vote.add(prediction(DanceStep.BALANCO))
        val result = vote.add(prediction(DanceStep.BASE_1))

        val decided = result as VoteResult.Decided
        assertEquals(DanceStep.BALANCO, decided.step)
        assertEquals(2f / 3f, decided.agreement, 1e-4f)
    }

    @Test
    fun `empate abaixo da concordancia minima nao decide`() {
        // Durante a transição entre passos as janelas discordam de verdade;
        // mostrar o vencedor de um empate seria inventar certeza.
        val vote = MovementVote(windowCount = 4, minimumAgreement = 0.6f)
        vote.add(prediction(DanceStep.ABERTURA))
        vote.add(prediction(DanceStep.ABERTURA))
        vote.add(prediction(DanceStep.GIRO_LINHA))
        val result = vote.add(prediction(DanceStep.GIRO_LINHA))

        assertEquals(VoteResult.Undecided, result)
    }

    @Test
    fun `janelas de baixa confianca nao viram voto`() {
        val vote = MovementVote(minimumConfidence = 0.4f)
        vote.add(prediction(DanceStep.ABERTURA, confidence = 0.25f))
        val result = vote.add(prediction(DanceStep.ABERTURA, confidence = 0.3f))

        assertEquals(VoteResult.Undecided, result)
    }

    @Test
    fun `janela antiga sai da memoria`() {
        val vote = MovementVote(windowCount = 2, minimumAgreement = 0.5f)
        vote.add(prediction(DanceStep.BALANCO))
        vote.add(prediction(DanceStep.GIRO_LINHA))
        val result = vote.add(prediction(DanceStep.GIRO_LINHA))

        // O balanço já saiu: a memória tem duas janelas, as duas de giro em linha.
        assertEquals(DanceStep.GIRO_LINHA, (result as VoteResult.Decided).step)
        assertEquals(1f, result.agreement, 1e-4f)
        assertEquals(2, vote.size)
    }
}
