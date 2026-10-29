package com.dancaai.app.analysis

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Intervalo padrão de 100 ms (10 Hz) salvo quando o teste diz o contrário. */
class MovementRecorderTest {

    private val recorder = MovementRecorder()

    @Test
    fun `sem gravacao ativa nenhum frame vira amostra`() {
        assertNull(recorder.onFrame(nowMs = 1_000))
    }

    @Test
    fun `primeiro frame da gravacao e a amostra zero no tempo zero`() {
        recorder.start(nowMs = 1_000)

        val meta = recorder.onFrame(nowMs = 1_000)!!

        assertEquals(0, meta.sample)
        assertEquals(0L, meta.elapsedMs)
        assertEquals(1, meta.recording)
    }

    @Test
    fun `frames dentro do intervalo nao viram amostra`() {
        recorder.start(nowMs = 1_000)
        recorder.onFrame(nowMs = 1_000)

        assertNull(recorder.onFrame(nowMs = 1_030))
        assertNull(recorder.onFrame(nowMs = 1_060))
        assertNotNull(recorder.onFrame(nowMs = 1_100))
    }

    @Test
    fun `a grade nao acumula atraso quando o frame chega tarde`() {
        // 33 ms por frame (30 fps) não cai exatamente em múltiplos de 100
        recorder.start(nowMs = 0)
        val tempos = generateSequence(0L) { it + 33 }.take(31).toList()

        val amostras = tempos.mapNotNull { recorder.onFrame(it) }

        // em 990 ms a 10 Hz esperam-se 10 amostras, e nenhuma pode atrasar mais
        // que a duração de um frame em relação à grade ideal
        assertEquals(10, amostras.size)
        amostras.forEachIndexed { i, meta ->
            val ideal = i * 100L
            assertTrue(
                "amostra $i em ${meta.elapsedMs} ms, grade ideal $ideal ms",
                meta.elapsedMs - ideal in 0..33,
            )
        }
    }

    @Test
    fun `camera lenta nao gera rajada para recuperar o atraso`() {
        recorder.start(nowMs = 0)
        recorder.onFrame(nowMs = 0)

        // um engasgo de 500 ms vale uma amostra só, não cinco
        assertNotNull(recorder.onFrame(nowMs = 500))
        assertNull(recorder.onFrame(nowMs = 520))
    }

    @Test
    fun `cada gravacao recebe um identificador proprio`() {
        recorder.start(nowMs = 0)
        recorder.onFrame(nowMs = 0)
        recorder.stop()
        recorder.start(nowMs = 5_000)

        val meta = recorder.onFrame(nowMs = 5_000)!!

        assertEquals(2, meta.recording)
        assertEquals(0, meta.sample)
        assertEquals(0L, meta.elapsedMs)
    }

    @Test
    fun `gravacao esquecida para sozinha no limite de duracao`() {
        recorder.start(nowMs = 0)

        assertNull(recorder.onFrame(nowMs = MovementRecorder.MAX_DURATION_MS))
        assertFalse(recorder.isRecording)
    }

    @Test
    fun `buraco de deteccao aparece no tempo decorrido e nao no indice`() {
        recorder.start(nowMs = 0)
        recorder.onFrame(nowMs = 0)
        // 300 ms sem pessoa no enquadramento: a PoseCameraView não emite frames,
        // então o recorder nem é consultado e a grade segue correndo
        val depois = recorder.onFrame(nowMs = 400)!!

        // o índice continua denso — é o salto de 0 ms para 400 ms que denuncia o
        // buraco, e por isso o tempo decorrido vai no rótulo junto com o índice
        assertEquals(1, depois.sample)
        assertEquals(400L, depois.elapsedMs)
    }

    @Test
    fun `trava de duracao dispara mesmo sem frame nenhum`() {
        // ninguém no enquadramento: onFrame nunca é chamado, e sem isto a
        // gravação ficaria ligada indefinidamente
        recorder.start(nowMs = 0)

        assertFalse(recorder.stopIfExpired(nowMs = MovementRecorder.MAX_DURATION_MS - 1))
        assertTrue(recorder.isRecording)
        assertTrue(recorder.stopIfExpired(nowMs = MovementRecorder.MAX_DURATION_MS))
        assertFalse(recorder.isRecording)
    }

    @Test
    fun `start repetido nao reinicia a gravacao em andamento`() {
        recorder.start(nowMs = 0)
        recorder.start(nowMs = 2_000)

        val meta = recorder.onFrame(nowMs = 2_000)!!

        assertEquals(1, meta.recording)
        assertEquals(2_000L, meta.elapsedMs)
    }
}
