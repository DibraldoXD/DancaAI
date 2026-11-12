package com.dancaai.app.analysis

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PerformanceMeterTest {

    /** [frames] quadros espaçados de [stepMs] a partir de [startMs], todos com a mesma latência. */
    private fun PerformanceMeter.steady(frames: Int, stepMs: Long, latencyMs: Long, startMs: Long = 0) {
        repeat(frames) { onFrame(startMs + it * stepMs, latencyMs) }
    }

    @Test
    fun semFrames_naoHaResumo() {
        assertNull(PerformanceMeter().summary())
    }

    @Test
    fun menosDeUmaJanelaCompleta_naoHaResumo() {
        val m = PerformanceMeter()
        m.steady(frames = 20, stepMs = 33, latencyMs = 40)   // ~0,66 s
        assertNull(m.summary())
    }

    @Test
    fun trintaQuadrosPorSegundoConstantes_dao30EmTodasAsEstatisticas() {
        val m = PerformanceMeter()
        // 33 ms entre frames (~30,3 fps); 181 frames cobrem ~6 s, ou seja, 5 janelas completas
        m.steady(frames = 181, stepMs = 33, latencyMs = 25)
        val s = m.summary()!!
        assertEquals(30.0, s.fpsMean, 1.0)
        assertEquals(s.fpsMean, s.fpsMedian, 1.0)
        assertEquals(s.fpsMax, s.fpsMin, 1.0)
    }

    @Test
    fun taxaExataDe10Fps() {
        val m = PerformanceMeter()
        m.steady(frames = 31, stepMs = 100, latencyMs = 25)   // 3 s => 3 janelas de 10 frames
        val s = m.summary()!!
        assertEquals(10.0, s.fpsMean, 1e-9)
        assertEquals(10.0, s.fpsMedian, 1e-9)
        assertEquals(10.0, s.fpsMax, 1e-9)
        assertEquals(10.0, s.fpsMin, 1e-9)
    }

    @Test
    fun estatisticasDeLatencia_comQuantidadePar() {
        val m = PerformanceMeter()
        m.onFrame(0, 20); m.onFrame(500, 30); m.onFrame(1_000, 40); m.onFrame(1_500, 100)
        val s = m.summary()!!
        assertEquals(4, s.frames)
        assertEquals(47.5, s.latencyMeanMs, 1e-9)
        assertEquals(35.0, s.latencyMedianMs, 1e-9)           // (30 + 40) / 2
        assertEquals(100L, s.latencyMaxMs)
        assertEquals(20L, s.latencyMinMs)
    }

    @Test
    fun estatisticasDeLatencia_comQuantidadeImpar_mediana() {
        val m = PerformanceMeter()
        m.onFrame(0, 20); m.onFrame(500, 30); m.onFrame(1_000, 100)
        val s = m.summary()!!
        assertEquals(30.0, s.latencyMedianMs, 1e-9)
    }

    @Test
    fun medianaIgnoraOutlierEMaximoOMostra() {
        val m = PerformanceMeter()
        // quase tudo 30 ms, um pico de 400 ms: mediana ~30, máximo 400
        for (i in 0 until 30) m.onFrame(i * 100L, if (i == 7) 400 else 30)
        val s = m.summary()!!
        assertEquals(30.0, s.latencyMedianMs, 1e-9)
        assertEquals(400L, s.latencyMaxMs)
        assertTrue(s.latencyMeanMs > s.latencyMedianMs)
    }

    @Test
    fun janelaDescartadaNaoGeraAmostraDeFps() {
        val m = PerformanceMeter()
        m.steady(frames = 11, stepMs = 100, latencyMs = 25)   // 1 amostra de 10 fps
        m.discardWindow()
        // buraco de 10 s (pausa) e depois frames normais: o buraco NÃO pode virar 0,1 fps
        m.steady(frames = 11, stepMs = 100, latencyMs = 25, startMs = 11_000)
        val s = m.summary()!!
        assertEquals(10.0, s.fpsMin, 1e-9)
    }

    @Test
    fun reset_limpaTudo() {
        val m = PerformanceMeter()
        m.steady(frames = 31, stepMs = 100, latencyMs = 25)
        m.reset()
        assertNull(m.summary())
    }

    @Test
    fun linhaDaPlanilha_tem47ColunasComOsNovosAoLado() {
        val m = PerformanceMeter()
        m.steady(frames = 31, stepMs = 100, latencyMs = 25)
        val row = m.summary()!!.toSheetRow("2026-11-09 21:00:00")

        assertEquals(47, row.size)
        assertEquals(PerformanceSummary.COORDINATE_COLUMNS + PerformanceSummary.SHEET_HEADER.size, row.size)
        assertEquals("", row[0])                              // Timestamp das capturas fica vazio
        assertEquals("metricas_sessao", row[1])               // Rotulo
        assertTrue(row.subList(2, 38).all { it == "" })
        assertEquals("2026-11-09 21:00:00", row[38])
        assertEquals(10.0, row[39] as Double, 1e-9)           // FPS_Media
        assertEquals(25.0, row[43] as Double, 1e-9)           // Latencia_Media_ms
        assertEquals(25L, row[45])                            // Latencia_Max_ms
        assertEquals(25L, row[46])                            // Latencia_Min_ms
    }
}
