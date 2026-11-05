package com.dancaai.app.analysis.movement

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * Amarra as 182 features do Kotlin às do Python.
 *
 * O modelo TFLite recebe um vetor de floats sem nome. Uma permutação de colunas,
 * ou desvio amostral onde o treino usou populacional, **não produz erro**: o
 * modelo responde normalmente, com valores errados, e o sintoma é um passo
 * errado na tela. Este teste é o que transforma esse erro silencioso em teste
 * vermelho.
 *
 * O vetor esperado vem de `app/src/test/resources/golden_vector_movimento.txt`,
 * gerado por `training/movement_classifier/vetor_ouro.py` a partir do mesmo
 * `features.py` que treinou o modelo. A trajetória é sintética e definida pela
 * fórmula replicada em [amostrasSinteticas] — nenhum dado de participante entra
 * no repositório por causa deste teste.
 */
class MovementFeaturesTest {

    /**
     * Nome e valor de cada feature, na ordem do arquivo, mais os metadados.
     *
     * Formato de texto e não JSON: num teste unitário JVM o `org.json` que vem
     * no `android.jar` é só um stub que lança exceção, então ler JSON aqui
     * exigiria uma dependência nova para um arquivo de 182 linhas.
     */
    private class Golden(texto: String) {
        val meta = mutableMapOf<String, String>()
        val nomes = mutableListOf<String>()
        val valores = mutableListOf<Double>()

        init {
            for (linha in texto.lineSequence()) {
                val limpa = linha.trim()
                when {
                    limpa.isEmpty() -> continue
                    limpa.startsWith("#meta ") -> {
                        val (chave, valor) = limpa.removePrefix("#meta ").split("=", limit = 2)
                        meta[chave] = valor
                    }
                    limpa.startsWith("#") -> continue
                    else -> {
                        val (nome, valor) = limpa.split("=", limit = 2)
                        nomes += nome
                        valores += valor.toDouble()
                    }
                }
            }
        }
    }

    private fun golden(): Golden {
        val stream = javaClass.classLoader!!
            .getResourceAsStream("golden_vector_movimento.txt")
        assertNotNull(
            "golden_vector_movimento.txt não encontrado. Gere com " +
                "`python training/movement_classifier/vetor_ouro.py`.",
            stream,
        )
        return Golden(stream!!.bufferedReader().readText())
    }

    /**
     * A mesma trajetória de `amostras_sinteticas()` do `vetor_ouro.py`.
     *
     * A contagem sai da janela do próprio [MovementWindow], mais UMA amostra
     * anterior a ela — a que dá velocidade à primeira da janela. É assim que o
     * buffer funciona e é assim que o treino derivou as velocidades.
     *
     * Derivar de WINDOW_MS em vez de fixar 31 não é elegância: a janela já mudou
     * de 3,0 s para 4,5 s quando a calibração do yaw saiu do pipeline, e um
     * número fixo aqui deixaria o teste comparando janelas de tamanhos
     * diferentes com uma mensagem de erro que não explica nada.
     */
    private val amostrasNaJanela =
        (MovementWindow.WINDOW_MS / MovementWindow.INTERVAL_MS).toInt()

    private fun amostrasSinteticas(): List<PoseSample> = (0..amostrasNaJanela).map { i ->
        val t = i * 0.1
        val giro = 0.09 * sin(2.0 * PI * t / 2.4)
        val passo = 0.18 * sin(2.0 * PI * t / 1.2 + 0.7)
        val sobe = 0.04 * cos(2.0 * PI * t / 1.2)

        val ombroMeioX = 0.50 + 0.02 * giro
        val ombroMeioY = 0.40 - sobe
        val quadrilMeioX = 0.50 + 0.01 * passo
        val quadrilMeioY = 0.60

        PoseSample(
            timeMs = (t * 1000.0).toLong(),
            leftShoulder = Point3((ombroMeioX - giro).toFloat(), (ombroMeioY + 0.01 * passo).toFloat(), -0.30f),
            rightShoulder = Point3((ombroMeioX + giro).toFloat(), (ombroMeioY - 0.01 * passo).toFloat(), -0.28f),
            leftHip = Point3((quadrilMeioX - 0.08).toFloat(), quadrilMeioY.toFloat(), -0.01f),
            rightHip = Point3((quadrilMeioX + 0.08).toFloat(), quadrilMeioY.toFloat(), 0.01f),
            leftKnee = Point3((quadrilMeioX - 0.09).toFloat(), (0.74 + 0.5 * sobe).toFloat(), -0.10f),
            rightKnee = Point3((quadrilMeioX + 0.09).toFloat(), (0.74 - 0.5 * sobe).toFloat(), 0.02f),
            leftAnkle = Point3((quadrilMeioX - 0.07 - passo).toFloat(), (0.86 + sobe).toFloat(), 0.18f),
            rightAnkle = Point3((quadrilMeioX + 0.07 + passo).toFloat(), (0.86 - sobe).toFloat(), 0.31f),
        )
    }

    /** Passa as amostras pela janela real, como a tela faz. */
    private fun janelaDoBuffer(): List<FrameSignals> {
        val window = MovementWindow()
        var ultima: List<FrameSignals>? = null
        for (sample in amostrasSinteticas()) {
            val signals = signalsOf(sample)
            assertNotNull("signalsOf devolveu null para uma amostra válida", signals)
            window.offer(signals!!)?.let { ultima = it }
        }
        // A janela corrente é a última emitida; com o passo de 0,5 s várias saem
        // pelo caminho antes dela.
        return ultima
            ?: error("nenhuma janela emitida com ${amostrasNaJanela + 1} amostras a 10 Hz")
    }

    @Test
    fun `a ordem dos nomes casa com a do Python`() {
        val esperado = golden().nomes
        assertEquals(
            "O Python gerou ${esperado.size} features e o Kotlin monta ${MovementFeatures.SIZE}",
            esperado.size,
            MovementFeatures.SIZE,
        )
        for (i in esperado.indices) {
            assertEquals("Feature $i fora de ordem", esperado[i], MovementFeatures.NAMES[i])
        }
    }

    @Test
    fun `o vetor-ouro do Python bate com o do Kotlin`() {
        val dados = golden()
        assertTrue(
            "O vetor-ouro foi gerado COM calibração do yaw, e o app não calibra. " +
                "Regere com o padrão (sem --com-calibracao).",
            dados.meta["calibracao_yaw"] == "false",
        )
        val esperado = dados.valores
        val obtido = MovementFeatures.vectorOf(janelaDoBuffer())

        assertEquals(esperado.size, obtido.size)

        // Tolerância: o Python calcula em float64 e o Kotlin em float32, então a
        // última casa difere por construção. Um erro de ORDEM — que é o que este
        // teste caça — dá diferença de ordem de grandeza, não de casa decimal.
        var piorNome = ""
        var piorErro = 0.0
        for (i in esperado.indices) {
            val alvo = esperado[i]
            val erro = abs(alvo - obtido[i]) / maxOf(1.0, abs(alvo))
            if (erro > piorErro) {
                piorErro = erro
                piorNome = dados.nomes[i]
            }
        }
        assertTrue(
            "Maior divergência em '$piorNome': ${"%.2e".format(piorErro)} (relativa). " +
                "Divergência grande costuma ser ordem das features ou fórmula de " +
                "uma estatística, não arredondamento.",
            piorErro < 1e-4,
        )
    }

    @Test
    fun `a janela do buffer casa com a janela do modelo exportado`() {
        // O vetor-ouro carrega a janela que o modelo em models/ usa. Se o
        // MovementWindow divergir dela, as features de amplitude por
        // sub-intervalo mudam de significado e o modelo embarcado deixa de
        // valer — sem erro em lugar nenhum.
        val esperadoS = golden().meta["duracao_janela_s"]!!.toDouble()
        assertEquals(
            "MovementWindow.WINDOW_MS está em ${MovementWindow.WINDOW_MS} ms e o " +
                "modelo exportado usa ${esperadoS} s. Reexporte o modelo ou ajuste a " +
                "janela — as duas coisas têm de mudar juntas.",
            (esperadoS * 1000).toLong(),
            MovementWindow.WINDOW_MS,
        )
        assertEquals(amostrasNaJanela, janelaDoBuffer().size)
    }

    @Test
    fun `tronco colapsado e descartado em vez de virar feature`() {
        // Pessoa fora de quadro ou landmarks colapsados: toda razão estouraria, e
        // uma janela montada com esses valores seria ruído com cara de feature.
        val ponto = Point3(0.5f, 0.5f, 0f)
        val degenerada = PoseSample(
            timeMs = 0, leftShoulder = ponto, rightShoulder = ponto,
            leftHip = ponto, rightHip = ponto, leftKnee = ponto, rightKnee = ponto,
            leftAnkle = ponto, rightAnkle = ponto,
        )
        assertEquals(null, signalsOf(degenerada))
    }

    @Test
    fun `o yaw e assinado e inverte junto com os ombros`() {
        // A regressão que custou a abertura (f1 0,47) foi exatamente tomar o
        // módulo aqui. Se alguém "simplificar" para abs(), este teste cai.
        // Nao a primeira amostra: em t=0 o seno da rotacao e zero, e um teste de
        // sinal sobre zero nao testa nada.
        val base = amostrasSinteticas()[6]
        val espelhada = base.copy(
            leftShoulder = base.rightShoulder,
            rightShoulder = base.leftShoulder,
        )
        val direto = signalsOf(base)!!.yaw
        val invertido = signalsOf(espelhada)!!.yaw

        assertEquals(-direto, invertido, 1e-6f)
        assertTrue("o yaw da amostra base não deveria ser zero", abs(direto) > 1e-3f)
    }
}
