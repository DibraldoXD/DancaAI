package com.dancaai.app

import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Optional

/**
 * Testes do portão de vista frontal e dos limiares recalibrados em 05/11/2026.
 *
 * O caso que motivou estes testes: o portão antigo comparava o span de ombros cru
 * contra 0,15 absoluto, e com o praticante enquadrado de corpo inteiro — obrigatório
 * em dança — o span fica em 0,08–0,12. Resultado: as regras de ombros à frente e de
 * inclinação lateral do tronco ficavam inertes e nada no app denunciava isso. Os
 * números dos cenários abaixo vêm do dataset de postura: 8 participantes, sendo 7 da
 * coleta de 04/11/2026 mais quem gravou as duas sessões anteriores. Não confundir com
 * os 7 participantes citados no classificador de movimentos, que usa só o 04/11.
 */
class PostureValidatorTest {

    /**
     * Corpo sintético em coordenadas normalizadas MediaPipe.
     *
     * [shoulderSpan] e [torsoLength] são os dois eixos que o portão lê; os demais
     * landmarks existem só para satisfazer o tamanho mínimo da lista e a checagem de
     * visibilidade. [shoulderDropDeg] inclina a linha dos ombros, [zDiff] empurra os
     * ombros à frente do quadril em z e [trunkTiltDeg] desloca o tronco lateralmente.
     */
    private fun body(
        shoulderSpan: Float,
        torsoLength: Float = 0.14f,
        shoulderDropDeg: Float = 0f,
        zDiff: Float = 0f,
        trunkTiltDeg: Float = 0f,
        visibility: Float = 0.9f,
    ): List<NormalizedLandmark> {
        val shoulderMidY = 0.40f
        val hipMidY = shoulderMidY + torsoLength
        val tiltRad = Math.toRadians(trunkTiltDeg.toDouble())
        // dx = tronco * tan(tilt): reproduz o atan2(dx, dy) do validador.
        val hipMidX = 0.5f - (torsoLength * Math.tan(tiltRad)).toFloat()
        val dropRad = Math.toRadians(shoulderDropDeg.toDouble())
        val halfDrop = (shoulderSpan * Math.tan(dropRad) / 2.0).toFloat()

        fun lm(x: Float, y: Float, z: Float = 0f) =
            NormalizedLandmark.create(x, y, z, Optional.of(visibility), Optional.of(visibility))

        val shoulderZ = zDiff  // quadril fica em z = 0, então zDiff = ombro − quadril
        val landmarks = MutableList(33) { lm(0.5f, 0.5f) }
        landmarks[0] = lm(0.5f, shoulderMidY - 0.10f)                               // nariz
        landmarks[7] = lm(0.47f, shoulderMidY - 0.08f)                              // orelha E
        landmarks[8] = lm(0.53f, shoulderMidY - 0.08f)                              // orelha D
        landmarks[11] = lm(0.5f - shoulderSpan / 2f, shoulderMidY + halfDrop, shoulderZ)
        landmarks[12] = lm(0.5f + shoulderSpan / 2f, shoulderMidY - halfDrop, shoulderZ)
        landmarks[23] = lm(hipMidX - 0.06f, hipMidY, 0f)                            // quadril E
        landmarks[24] = lm(hipMidX + 0.06f, hipMidY, 0f)                            // quadril D
        return landmarks
    }

    private fun issues(result: PostureResult): List<PostureIssue> = when (result) {
        is PostureResult.Bad -> result.issues
        else -> emptyList()
    }

    // ---------- frontalityRatio ----------

    @Test
    fun `razao de frontalidade e invariante a distancia da camera`() {
        // Mesma pessoa, de frente, perto e longe: span e tronco encolhem juntos.
        val perto = PostureValidator.frontalityRatio(shoulderSpan = 0.24f, torsoLength = 0.28f)
        val longe = PostureValidator.frontalityRatio(shoulderSpan = 0.12f, torsoLength = 0.14f)
        assertEquals(perto, longe, 1e-5f)
    }

    @Test
    fun `razao cai quando o corpo gira e o span projetado encolhe`() {
        // Tronco igual, span pela metade: é rotação, não distância.
        val frontal = PostureValidator.frontalityRatio(0.12f, 0.14f)
        val girado = PostureValidator.frontalityRatio(0.06f, 0.14f)
        assertTrue("girar tem de reduzir a razão", girado < frontal)
    }

    @Test
    fun `tronco degenerado devolve zero em vez de estourar a divisao`() {
        assertEquals(0f, PostureValidator.frontalityRatio(0.12f, 0f), 0f)
    }

    // ---------- o defeito que o portão antigo causava ----------

    @Test
    fun `enquadramento de corpo inteiro e avaliado como frontal`() {
        // span 0,116 e tronco 0,140 são as medianas do dataset: razão 0,83, bem acima
        // do mínimo. Com o portão antigo (span >= 0,15) este corpo era ignorado, e com
        // ele a inclinação lateral — o desvio abaixo nunca disparava em campo.
        val span = 0.116f
        assertTrue("o portão antigo barrava este span", span < 0.15f)

        val result = PostureValidator.validate(
            body(shoulderSpan = span, torsoLength = 0.140f, trunkTiltDeg = 18f)
        )
        assertTrue(
            "em enquadramento de corpo inteiro o portão tem de abrir",
            issues(result).contains(PostureIssue.TRUNK_TILT_LEFT),
        )
    }

    @Test
    fun `inclinacao lateral tambem deixa de ficar inerte em corpo inteiro`() {
        val result = PostureValidator.validate(
            body(shoulderSpan = 0.116f, torsoLength = 0.140f, trunkTiltDeg = 18f)
        )
        assertTrue(
            "a regra 3 compartilhava o mesmo portão e estava igualmente inerte",
            issues(result).contains(PostureIssue.TRUNK_TILT_LEFT),
        )
    }

    // ---------- o portão continua barrando o que deve ----------

    @Test
    fun `de perfil nao avalia os desvios que dependem de vista frontal`() {
        // Span colapsado com tronco intacto = pessoa de lado. O tilt gritante não pode
        // gerar desvio: de perfil, o deslocamento em x mede profundidade, não postura.
        // O zDiff extremo entra para documentar que ombros encurvados também não sai —
        // hoje por estar fora das regras, e amanhã, quando o classificador assumir,
        // porque inferir sobre alguém de perfil continua sem sentido.
        val result = PostureValidator.validate(
            body(shoulderSpan = 0.03f, torsoLength = 0.140f, zDiff = -1.0f, trunkTiltDeg = 30f)
        )
        val encontrados = issues(result)
        assertTrue(
            "nenhum desvio dependente de vista frontal deve sair de perfil: $encontrados",
            encontrados.none {
                it == PostureIssue.SHOULDERS_FORWARD ||
                    it == PostureIssue.TRUNK_TILT_LEFT ||
                    it == PostureIssue.TRUNK_TILT_RIGHT
            },
        )
    }

    @Test
    fun `limiar de frontalidade fica abaixo do minimo observado em passos frontais`() {
        // Balanço, base 1 e base 2 não têm rotação; em 2.387 frames a razão mínima
        // medida foi 0,658. O limiar tem de aprovar isso com folga.
        assertTrue(
            "0,658 é o piso empírico de um frame comprovadamente frontal",
            0.658f >= PostureValidator.MIN_FRONTAL_RATIO,
        )
    }

    // ---------- ombros encurvados saiu das regras ----------

    @Test
    fun `ombros a frente nunca sai das regras, em nenhuma distancia`() {
        // Nenhuma regra fixa sobre o eixo z do MediaPipe sobrevive à variação de
        // distância: o limiar ótimo de Zdiff varia 3,8× dentro da faixa do dataset
        // (0,38 longe · 1,34 médio · 1,44 perto). Os dois primeiros casos abaixo são os
        // modos de falha observados em teste no aparelho, com o limiar fixo em 1,40.
        // O desvio passa a ser responsabilidade do classificador embarcado; até lá o
        // app não afirma nada sobre ele. Se este teste quebrar, alguém reativou a regra.
        val casos = listOf(
            // tronco, zDiff, descrição
            Triple(0.098f, -0.40f, "longe e muito inclinado — a regra dizia 'postura ok'"),
            Triple(0.164f, -0.09f, "perto com postura boa — a regra acusava desvio"),
            Triple(0.140f, -1.00f, "distância mediana, inclinação extrema"),
            Triple(0.140f, 0.00f, "sem inclinação alguma"),
        )
        for ((tronco, zDiff, descricao) in casos) {
            val result = PostureValidator.validate(
                body(shoulderSpan = 0.116f, torsoLength = tronco, zDiff = zDiff)
            )
            assertTrue(
                "SHOULDERS_FORWARD não pode vir das regras ($descricao)",
                !issues(result).contains(PostureIssue.SHOULDERS_FORWARD),
            )
        }
    }

    @Test
    fun `o identificador do desvio continua existindo para o historico`() {
        // O nome da constante é o identificador gravado no banco, e as sessões já
        // registradas o contêm. Desativar a regra não pode apagar o enum.
        assertEquals("Ombros encurvados", PostureIssue.SHOULDERS_FORWARD.label)
    }

    @Test
    fun `ombro esquerdo caido e detectado independente do enquadramento`() {
        // A regra 1 nunca passou pelo portão e não deve mudar de comportamento.
        val result = PostureValidator.validate(
            body(shoulderSpan = 0.08f, torsoLength = 0.140f, shoulderDropDeg = 12f)
        )
        assertTrue(issues(result).contains(PostureIssue.LEFT_SHOULDER_DROP))
    }

    @Test
    fun `corpo alinhado e sem desvios devolve Good`() {
        assertEquals(
            PostureResult.Good,
            PostureValidator.validate(body(shoulderSpan = 0.116f, torsoLength = 0.140f)),
        )
    }

    @Test
    fun `visibilidade baixa devolve Unknown`() {
        assertEquals(
            PostureResult.Unknown,
            PostureValidator.validate(body(shoulderSpan = 0.116f, visibility = 0.2f)),
        )
    }
}
