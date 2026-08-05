package com.dancaai.app.audio

import android.content.Context

/** Persiste o último BPM configurado pelo usuário no metrônomo, entre sessões. */
class MetronomeBpmStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun load(): Int =
        prefs.getInt(KEY_BPM, Metronome.DEFAULT_BPM).coerceIn(Metronome.MIN_BPM, Metronome.MAX_BPM)

    fun save(bpm: Int) {
        prefs.edit().putInt(KEY_BPM, bpm).apply()
    }

    private companion object {
        const val PREFS_NAME = "metronome_prefs"
        const val KEY_BPM = "bpm"
    }
}
