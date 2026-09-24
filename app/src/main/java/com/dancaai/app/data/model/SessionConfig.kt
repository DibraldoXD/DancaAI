package com.dancaai.app.data.model

import com.dancaai.app.audio.BeatPattern
import com.dancaai.app.audio.Metronome
import com.dancaai.app.data.DanceCatalog

/**
 * Configuração de uma sessão de treino, montada na tela de Nova sessão e
 * consumida pela tela de Treino.
 *
 * É o contrato entre as duas telas: antes disso a duração escolhida não chegava
 * ao treino, que usava uma constante fixa de 5 minutos.
 */
data class SessionConfig(
    val styleId: String = DanceCatalog.FORRO_ID,
    val durationMin: Int = DEFAULT_DURATION_MIN,
    val bpm: Int = Metronome.DEFAULT_BPM,
    val beatPattern: BeatPattern = BeatPattern.THREE_AND_PAUSE,
) {
    val durationSec: Int get() = durationMin * 60

    /** Intervalo entre batidas, referência exata do módulo de ritmo (sem jitter de callback). */
    val beatIntervalMs: Long get() = 60_000L / bpm

    companion object {
        const val DEFAULT_DURATION_MIN = 5

        /** Durações oferecidas na tela de Nova sessão, em minutos. */
        val DURATION_OPTIONS = listOf(3, 5, 10)
    }
}
