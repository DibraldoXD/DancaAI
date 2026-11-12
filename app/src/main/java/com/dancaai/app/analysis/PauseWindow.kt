package com.dancaai.app.analysis

/**
 * Janela em que o tempo "Pausa" do compasso (1-2-3-Pausa) acontece, projetada para
 * frente a partir da última batida de pausa ouvida.
 *
 * O callback do metrônomo chega no instante audível da batida, mas a janela
 * começa meio intervalo ANTES dela; por isso a janela é extrapolada de forma
 * periódica (a cada compasso) em vez de esperar o próximo callback.
 *
 * Todos os instantes estão na base de `SystemClock.uptimeMillis`, a mesma dos
 * frames da câmera. Classe pura: sem Android, testável na JVM.
 */
class PauseWindow {

    private var anchorMs = 0L
    private var intervalMs = 0L
    private var barMs = 0L
    private var active = false

    /**
     * Registra uma batida de pausa. [audibleAtMs] é o instante audível dela, [intervalMs]
     * o intervalo entre batidas e [beatsPerBar] quantos tempos tem o compasso.
     * Chamar a cada pausa ouvida mantém a janela alinhada se o BPM mudar.
     */
    fun mark(audibleAtMs: Long, intervalMs: Long, beatsPerBar: Int) {
        if (intervalMs <= 0 || beatsPerBar <= 0) return
        anchorMs = audibleAtMs
        this.intervalMs = intervalMs
        barMs = intervalMs * beatsPerBar
        active = true
    }

    /** Sem metrônomo, ou com um padrão sem pausa, não há janela. */
    fun clear() {
        active = false
    }

    /** `true` se [atMs] está em `[pausa − intervalo/2, pausa + intervalo/2)` de algum compasso. */
    fun contains(atMs: Long): Boolean {
        if (!active) return false
        val phase = Math.floorMod(atMs - anchorMs + intervalMs / 2, barMs)
        return phase < intervalMs
    }
}
