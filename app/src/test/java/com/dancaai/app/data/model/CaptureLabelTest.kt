package com.dancaai.app.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * O token do rótulo é o contrato com a planilha de coleta e com os scripts de
 * treino (`modelo_postura/treinar_multilabel.py` compara `Rotulo` com
 * `post_boa` / `post_ruim_<desvio>`). Estes testes travam o formato: quebrá-lo
 * desalinha silenciosamente o dataset, e o erro só aparece no treino.
 */
class CaptureLabelTest {

    @Test
    fun `token simples junta assunto e angulo`() {
        val label = CaptureLabel(CaptureSubject.POSTURE_GOOD, CaptureView.FRONT)

        assertEquals("post_boa@frente", label.token())
    }

    @Test
    fun `token de captura continua carrega ciclo e tempo`() {
        val label = CaptureLabel(CaptureSubject.POSTURE_SHOULDERS_FORWARD, CaptureView.SIDE_RIGHT)

        assertEquals("post_ruim_OF@lado_D|c3t2", label.token(CaptureMeta.Beat(cycle = 3, beat = "2")))
    }

    @Test
    fun `tempo de pausa vira slug sem acento nem caixa alta`() {
        val label = CaptureLabel(CaptureSubject.MOVE_BASE_1, CaptureView.FRONT)

        assertEquals("mov_base1@frente|c1tpausa", label.token(CaptureMeta.Beat(cycle = 1, beat = "Pausa")))
    }

    @Test
    fun `token de gravacao de movimento carrega gravacao amostra e tempo`() {
        val label = CaptureLabel(CaptureSubject.MOVE_GIRO_LINHA, CaptureView.FRONT)

        assertEquals(
            "mov_giro_linha@frente|r2s014t0467",
            label.token(CaptureMeta.Frame(recording = 2, sample = 14, elapsedMs = 467)),
        )
    }

    @Test
    fun `indice e tempo tem largura fixa para ordenar como texto na planilha`() {
        val label = CaptureLabel(CaptureSubject.MOVE_BALANCO, CaptureView.FRONT)

        // sem o zero à esquerda, s10 viria antes de s2 numa ordenação alfabética
        assertEquals(
            "mov_balanco@frente|r1s002t0100",
            label.token(CaptureMeta.Frame(recording = 1, sample = 2, elapsedMs = 100)),
        )
    }

    @Test
    fun `prefixo do token permite filtrar por assunto no treino`() {
        CaptureView.entries.forEach { view ->
            val token = CaptureLabel(CaptureSubject.POSTURE_LEFT_SHOULDER_DROP, view).token()

            assertTrue("token inesperado: $token", token.startsWith("post_ruim_OE"))
        }
    }

    @Test
    fun `tokens de postura usam exatamente os valores que os scripts de treino esperam`() {
        val postureTokens = CaptureSubject.entries
            .filter { it.group == CaptureGroup.POSTURE }
            .map { it.token }

        assertEquals(
            listOf("post_boa", "post_ruim_OE", "post_ruim_OD", "post_ruim_OF"),
            postureTokens,
        )
    }

    @Test
    fun `rotulo de teste fica fora dos grupos de coleta`() {
        // os scripts de treino descartam `teste`; se ele virasse um rótulo de
        // postura ou movimento, a verificação de envio entraria no dataset
        assertEquals(CaptureGroup.CHECK, CaptureSubject.CHECK_UPLOAD.group)
        assertEquals("teste", CaptureSubject.CHECK_UPLOAD.token)
    }

    @Test
    fun `pilula do HUD usa o nome sem o parentese de desambiguacao`() {
        val label = CaptureLabel(CaptureSubject.MOVE_GIRO_LINHA, CaptureView.FRONT)

        assertEquals("Giro em linha · Frente", label.display)
    }

    @Test
    fun `nome sem parentese nao altera rotulos que nao tem um`() {
        assertEquals("Postura boa", CaptureSubject.POSTURE_GOOD.shortDisplay)
        assertEquals("Ombro esq. caído", CaptureSubject.POSTURE_LEFT_SHOULDER_DROP.shortDisplay)
    }

    @Test
    fun `nenhum token se repete`() {
        val tokens = CaptureSubject.entries.map { it.token }

        assertEquals(tokens.size, tokens.distinct().size)
    }
}
