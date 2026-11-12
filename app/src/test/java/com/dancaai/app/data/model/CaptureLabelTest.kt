package com.dancaai.app.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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

    // ── participante ──────────────────────────────────────────────
    // O código entra como segmento próprio depois do ângulo. O que estes testes
    // travam é a POSIÇÃO: o ângulo continua sendo lido por
    // `split("@")[1].split("|")[0]` e a trajetória continua sendo detectada por
    // `\|r\d` nos scripts de treino. Mover o segmento quebra as duas leituras.

    @Test
    fun `token sem participante continua no formato ja coletado`() {
        val label = CaptureLabel(CaptureSubject.POSTURE_GOOD, CaptureView.FRONT, participant = null)

        // as 815 capturas de postura e as 44 gravações já na planilha não têm o
        // campo; o formato sem ele tem de permanecer byte a byte o mesmo
        assertEquals("post_boa@frente", label.token())
        assertEquals(
            "post_ruim_OF@lado_D|c3t2",
            CaptureLabel(CaptureSubject.POSTURE_SHOULDERS_FORWARD, CaptureView.SIDE_RIGHT)
                .token(CaptureMeta.Beat(cycle = 3, beat = "2")),
        )
    }

    @Test
    fun `participante entra depois do angulo`() {
        val label = CaptureLabel(
            CaptureSubject.POSTURE_GOOD,
            CaptureView.FRONT,
            ParticipantId(3),
        )

        assertEquals("post_boa@frente|p03", label.token())
    }

    @Test
    fun `participante vem antes do metadado de compasso`() {
        val label = CaptureLabel(
            CaptureSubject.POSTURE_SHOULDERS_FORWARD,
            CaptureView.SIDE_RIGHT,
            ParticipantId(3),
        )

        assertEquals(
            "post_ruim_OF@lado_D|p03|c3t2",
            label.token(CaptureMeta.Beat(cycle = 3, beat = "2")),
        )
    }

    @Test
    fun `participante vem antes do metadado de gravacao de movimento`() {
        val label = CaptureLabel(
            CaptureSubject.MOVE_GIRO_LINHA,
            CaptureView.FRONT,
            ParticipantId(7),
        )

        assertEquals(
            "mov_giro_linha@frente|p07|r2s014t0467",
            label.token(CaptureMeta.Frame(recording = 2, sample = 14, elapsedMs = 467)),
        )
    }

    @Test
    fun `angulo continua legivel pela separacao que os scripts de treino usam`() {
        val token = CaptureLabel(
            CaptureSubject.MOVE_BALANCO,
            CaptureView.SIDE_LEFT,
            ParticipantId(12),
        ).token(CaptureMeta.Frame(recording = 1, sample = 0, elapsedMs = 0))

        // df["Rotulo"].str.split("@").str[1].str.split("|").str[0]
        assertEquals("mov_balanco", token.substringBefore("@"))
        assertEquals("lado_E", token.substringAfter("@").substringBefore("|"))
    }

    @Test
    fun `sufixo de trajetoria continua detectavel com participante no token`() {
        val token = CaptureLabel(
            CaptureSubject.MOVE_BASE_2,
            CaptureView.FRONT,
            ParticipantId(5),
        ).token(CaptureMeta.Frame(recording = 3, sample = 7, elapsedMs = 712))

        // os scripts de postura descartam amostras de trajetória por este padrão
        assertTrue("token inesperado: $token", Regex("\\|r\\d").containsMatchIn(token))
    }

    @Test
    fun `codigo do participante tem dois digitos para ordenar como texto`() {
        // sem o zero à esquerda, p10 viria antes de p2 numa ordenação alfabética
        assertEquals("p01", ParticipantId(1).token)
        assertEquals("p09", ParticipantId(9).token)
        assertEquals("p10", ParticipantId(10).token)
        // acima de 99 a largura cresce sozinha; o `\|p(\d+)` dos scripts segue casando
        assertEquals("p100", ParticipantId(100).token)
    }

    @Test
    fun `participante persistido em zero significa nenhum armado`() {
        assertNull(ParticipantId.ofPersisted(0))
        assertEquals(ParticipantId(1), ParticipantId.ofPersisted(1))
    }

    @Test
    fun `participante anda para frente e para tras sem passar do primeiro`() {
        assertEquals(ParticipantId(2), ParticipantId(1).next())
        assertEquals(ParticipantId(1), ParticipantId(2).previous())
        assertNull(ParticipantId(ParticipantId.FIRST).previous())
    }

    @Test
    fun `nenhum token se repete`() {
        val tokens = CaptureSubject.entries.map { it.token }

        assertEquals(tokens.size, tokens.distinct().size)
    }
}
