package com.dancaai.app.analysis

import com.dancaai.app.WeightLeg

/**
 * Máquina de estados da transferência de peso: converte a razão quadril × tornozelos
 * (já suavizada) em apoio confirmado ESQUERDA / DIREITA / NEUTRO e decide quando uma
 * troca de perna é correta ou "erro de marcação".
 *
 * Existe para que a regra seja testável sem MediaPipe e para que o script de calibração
 * (`modelo_postura/calibrar_marcacao.py`) replique exatamente a mesma lógica — qualquer
 * mudança aqui precisa ser espelhada lá.
 *
 * Por que não classificar frame a frame:
 *  • **Histerese**: para sair de ESQUERDA a razão precisa cair abaixo de
 *    `upper − hysteresis`, não só de `upper`. Sem isso, uma razão oscilando na borda
 *    produzia E→N→E e o par contava como o mesmo pé duas vezes.
 *  • **Permanência mínima em ms** (não em frames): o app roda a ~30 Hz e as gravações da
 *    planilha a 10 Hz, então só o tempo torna os dois comparáveis.
 *  • **O instante da troca é o do início do candidato**, não o do commit, para a espera
 *    de permanência não atrasar o módulo de ritmo.
 *
 * Erro de marcação: a perna confirmada é a mesma do último apoio confirmado. Como só há
 * commit quando o estado muda, isso só acontece depois de um neutro estável — os passos
 * sempre alternam. Commits ficam adiados enquanto [update] recebe `inPause = true`.
 */
class WeightStateMachine(private val config: Config = Config()) {

    data class Config(
        /** Razão acima da qual o apoio é ESQUERDA. */
        val upperRatio: Float = 0.55f,
        /** Razão abaixo da qual o apoio é DIREITA. */
        val lowerRatio: Float = 0.45f,
        /** Folga para SAIR de um apoio já confirmado. */
        val hysteresis: Float = 0.03f,
        /** Tempo mínimo na zona de uma perna antes de confirmá-la. */
        val dwellMs: Long = 80,
        /** Tempo mínimo em neutro antes de confirmá-lo (mais longo: neutro curto é ruído). */
        val neutralDwellMs: Long = 150,
        /** Intervalo sem frames a partir do qual um candidato em andamento é descartado. */
        val maxGapMs: Long = 500,
        /** Desempate por z dos tornozelos na zona neutra. Desligado: z do MediaPipe é estimado. */
        val zTieBreak: Boolean = false,
        val zTieBreakThreshold: Float = 0.10f,
    )

    /** Mudança de apoio confirmada. [startedAtMs] é quando o candidato começou, não o commit. */
    data class Transition(val leg: WeightLeg, val startedAtMs: Long, val isError: Boolean)

    /** Apoio confirmado no momento. */
    var state: WeightLeg = WeightLeg.NEUTRAL
        private set

    private var lastSupport: WeightLeg? = null
    private var pendingZone: WeightLeg? = null
    private var pendingSinceMs = 0L
    private var lastUpdateMs: Long? = null

    /**
     * [ratio] é `distD / (distE + distD)` suavizado — 1,0 = peso na esquerda — ou `null`
     * quando os tornozelos estão juntos demais para medir (tratado como neutro).
     * Devolve a transição se este frame confirmou uma, senão `null`.
     */
    fun update(ratio: Float?, zDiff: Float, atMs: Long, inPause: Boolean = false): Transition? {
        val previous = lastUpdateMs
        if (previous != null && atMs - previous > config.maxGapMs) clearPending()
        lastUpdateMs = atMs

        val zone = classify(ratio, zDiff)
        if (zone == state) {
            clearPending()
            return null
        }
        if (zone != pendingZone) {
            pendingZone = zone
            pendingSinceMs = atMs
        }
        val needed = if (zone == WeightLeg.NEUTRAL) config.neutralDwellMs else config.dwellMs
        if (atMs - pendingSinceMs < needed || inPause) return null

        val isError = zone != WeightLeg.NEUTRAL && lastSupport == zone
        val transition = Transition(zone, pendingSinceMs, isError)
        state = zone
        if (zone != WeightLeg.NEUTRAL) lastSupport = zone
        clearPending()
        return transition
    }

    fun reset() {
        state = WeightLeg.NEUTRAL
        lastSupport = null
        lastUpdateMs = null
        clearPending()
    }

    private fun clearPending() {
        pendingZone = null
    }

    /** Zona do frame, com histerese em torno do apoio já confirmado. */
    private fun classify(ratio: Float?, zDiff: Float): WeightLeg {
        if (ratio == null) return WeightLeg.NEUTRAL
        val leftAbove = if (state == WeightLeg.LEFT) config.upperRatio - config.hysteresis else config.upperRatio
        val rightBelow = if (state == WeightLeg.RIGHT) config.lowerRatio + config.hysteresis else config.lowerRatio
        return when {
            ratio > leftAbove -> WeightLeg.LEFT
            ratio < rightBelow -> WeightLeg.RIGHT
            config.zTieBreak -> when {
                zDiff > config.zTieBreakThreshold -> WeightLeg.LEFT
                zDiff < -config.zTieBreakThreshold -> WeightLeg.RIGHT
                else -> WeightLeg.NEUTRAL
            }
            else -> WeightLeg.NEUTRAL
        }
    }
}
