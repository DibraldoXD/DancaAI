package com.dancaai.app.audio

import org.junit.Assert.assertEquals
import org.junit.Test

class MetronomeTest {

    private val sampleRate = 44_100
    private val metronome = Metronome(sampleRate = sampleRate)

    @Test
    fun `samplesPerBeat corresponde ao periodo esperado do BPM, com tolerancia sub-milissegundo`() {
        listOf(60, 80, 100, 112, 120, 128, 148, 180, 200).forEach { bpm ->
            val samples = metronome.samplesPerBeat(bpm)
            val expectedSeconds = 60.0 / bpm
            val actualSeconds = samples / sampleRate.toDouble()
            assertEquals("bpm=$bpm", expectedSeconds, actualSeconds, 0.001)
        }
    }

    @Test
    fun `bpm e restrito a faixa MIN_BPM ate MAX_BPM`() {
        metronome.bpm = Metronome.MIN_BPM - 20
        assertEquals(Metronome.MIN_BPM, metronome.bpm)

        metronome.bpm = Metronome.MAX_BPM + 50
        assertEquals(Metronome.MAX_BPM, metronome.bpm)

        metronome.bpm = 128
        assertEquals(128, metronome.bpm)
    }
}
