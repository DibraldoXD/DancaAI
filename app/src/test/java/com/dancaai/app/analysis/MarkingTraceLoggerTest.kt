package com.dancaai.app.analysis

import com.dancaai.app.WeightLeg
import com.dancaai.app.WeightTrace
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.StringWriter

class MarkingTraceLoggerTest {

    private fun trace(
        at: Long,
        raw: Float? = 0.8f,
        smooth: Float? = 0.78f,
        state: WeightLeg = WeightLeg.LEFT,
        transition: WeightLeg? = null,
        error: Boolean = false,
        pause: Boolean = false,
    ) = WeightTrace(at, raw, smooth, zDiff = 0.05f, state = state, transition = transition, isError = error, inPause = pause)

    @Test
    fun cabecalhoEDepoisUmaLinhaPorEvento() {
        val sw = StringWriter()
        val logger = MarkingTraceLogger(sw)
        logger.onFrame(trace(1_000))
        logger.onBeat(beatIndex = 3, audibleAtMs = 1_040)
        logger.close()

        val lines = sw.toString().trim().lines()
        assertEquals(MarkingTraceLogger.HEADER, lines[0])
        assertEquals("F,1000,0.8000,0.7800,0.0500,LEFT,,0,0,", lines[1])
        assertEquals("B,1040,,,,,,,,3", lines[2])
    }

    @Test
    fun colunasDaLinhaBatemComOCabecalho() {
        val cols = MarkingTraceLogger.HEADER.split(",").size
        val sw = StringWriter()
        val logger = MarkingTraceLogger(sw)
        logger.onFrame(trace(1))
        logger.onBeat(0, 2)
        logger.close()
        sw.toString().trim().lines().forEach { assertEquals(cols, it.split(",").size) }
    }

    @Test
    fun razaoNulaViraCampoVazioETransicaoEErroAparecem() {
        val logger = MarkingTraceLogger(StringWriter())
        val line = logger.frameLine(
            trace(5, raw = null, smooth = null, state = WeightLeg.LEFT, transition = WeightLeg.LEFT, error = true, pause = true),
        )
        assertEquals("F,5,,,0.0500,LEFT,LEFT,1,1,", line)
    }

    @Test
    fun resumoDoErroTrazOContextoDosFramesAnteriores() {
        val logger = MarkingTraceLogger(StringWriter())
        val before = listOf(trace(1, smooth = 0.80f), trace(2, smooth = 0.51f))
        val summary = logger.errorSummary(trace(3, transition = WeightLeg.LEFT, error = true), before)
        assertTrue(summary.contains("t=3"))
        assertTrue(summary.contains("0.8000 0.5100"))
    }

    @Test
    fun depoisDeFechadoNaoGravaMais() {
        val sw = StringWriter()
        val logger = MarkingTraceLogger(sw)
        logger.close()
        val before = sw.toString()
        logger.onFrame(trace(1))
        logger.onBeat(1, 1)
        assertEquals(before, sw.toString())
    }
}
