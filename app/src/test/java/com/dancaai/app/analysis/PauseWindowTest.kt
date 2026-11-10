package com.dancaai.app.analysis

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PauseWindowTest {

    @Test
    fun semMarcacao_nuncaContem() {
        assertFalse(PauseWindow().contains(1_000))
    }

    @Test
    fun janelaVaiDeMeioIntervaloAntesAteMeioDepois() {
        val w = PauseWindow().apply { mark(audibleAtMs = 1_000, intervalMs = 500, beatsPerBar = 4) }
        assertFalse(w.contains(749))
        assertTrue(w.contains(750))
        assertTrue(w.contains(1_000))
        assertTrue(w.contains(1_249))
        assertFalse(w.contains(1_250))   // fim aberto: aqui já é o "1"
    }

    @Test
    fun repeteACadaCompassoParaFrenteEParaTras() {
        val w = PauseWindow().apply { mark(1_000, 500, 4) }   // compasso = 2000 ms
        assertTrue(w.contains(3_000))
        assertTrue(w.contains(5_100))
        assertFalse(w.contains(2_000))    // "2" do compasso seguinte
        assertTrue(w.contains(-1_000))    // compasso anterior à marcação
    }

    @Test
    fun clearFechaAJanela() {
        val w = PauseWindow().apply { mark(1_000, 500, 4) }
        w.clear()
        assertFalse(w.contains(1_000))
    }

    @Test
    fun marcacaoInvalidaEhIgnorada() {
        val w = PauseWindow().apply { mark(1_000, 0, 4) }
        assertFalse(w.contains(1_000))
    }
}
