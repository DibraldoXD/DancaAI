package com.dancaai.app.analysis.movement

/**
 * Reamostra os quadros da câmera na grade de 10 Hz do treino e mantém a janela
 * deslizante que alimenta o classificador.
 *
 * Por que reamostrar
 * ─────────────────
 * O modelo foi treinado sobre gravações a 10 Hz — o intervalo do
 * [com.dancaai.app.analysis.MovementRecorder], que é o que a coleta usou. A
 * câmera do app entrega cerca de 30 quadros por segundo. Alimentar o modelo a
 * 30 Hz mudaria toda feature de velocidade e toda amplitude por sub-intervalo,
 * porque as duas dependem de quantas amostras existem no intervalo. Não daria
 * erro: daria resposta errada.
 *
 * A grade avança por múltiplos de [INTERVAL_MS] a partir do primeiro quadro, e
 * não a partir do último aceito, pela mesma razão que no `MovementRecorder`: um
 * quadro atrasado não deve deslocar todas as amostras seguintes.
 *
 * Por que nenhuma janela sai nos primeiros segundos
 * ────────────────────────────────────────────────
 * Uma janela só é emitida quando já existe [windowMs] de tempo decorrido para
 * fatiar. A tolerância de [MIN_OCCUPANCY] é para quadros perdidos no meio da
 * execução, não para o buffer enchendo: uma janela de aquecimento cobriria menos
 * tempo que a do treino, e amplitude por sub-intervalo medida sobre menos tempo
 * é outra grandeza. Numa versão anterior desta classe a janela saía com 42
 * amostras cobrindo 4,2 s em vez de 45 cobrindo 4,5 s, e o vetor-ouro foi o que
 * acusou.
 *
 * Por que o buffer guarda uma amostra a mais que a janela
 * ──────────────────────────────────────────────────────
 * No treino as velocidades foram derivadas sobre a GRAVAÇÃO inteira e só depois
 * fatiadas em janelas, então a primeira amostra de uma janela tem velocidade
 * calculada contra a amostra anterior a ela. Calculando por janela isolada essa
 * primeira amostra não teria predecessor. Guardar uma amostra extra reproduz o
 * treino exatamente, em vez de aproximar.
 */
class MovementWindow(
    private val windowMs: Long = WINDOW_MS,
    private val hopMs: Long = HOP_MS,
) {
    private val samples = ArrayDeque<FrameSignals>()
    private var nextSampleAtMs = Long.MIN_VALUE
    private var firstSampleAtMs = Long.MIN_VALUE
    private var lastWindowAtMs = Long.MIN_VALUE

    /** Amostras que uma janela cheia contém, na grade nominal. */
    private val samplesPerWindow = (windowMs / INTERVAL_MS).toInt()

    /**
     * Mínimo para a janela valer, igual ao `OCUPACAO_MINIMA` do `features.py`.
     *
     * A tolerância existe para QUADROS PERDIDOS — pessoa saindo do enquadramento
     * no meio da execução —, não para o buffer ainda estar enchendo. Uma janela
     * de aquecimento cobre menos tempo que [windowMs], e amplitude por
     * sub-intervalo calculada sobre menos tempo é outra grandeza.
     */
    private val minimumSamples = maxOf((MIN_OCCUPANCY * samplesPerWindow).toInt(), 4)

    /** Quadros aceitos na grade, para a UI poder mostrar o aquecimento do buffer. */
    var acceptedSamples = 0
        private set

    fun reset() {
        samples.clear()
        nextSampleAtMs = Long.MIN_VALUE
        firstSampleAtMs = Long.MIN_VALUE
        lastWindowAtMs = Long.MIN_VALUE
        acceptedSamples = 0
    }

    /**
     * Oferece um quadro. Devolve a janela pronta quando este quadro completa um
     * passo de [hopMs], e `null` nos outros casos — inclusive durante os
     * primeiros [windowMs] de sessão, quando ainda não há intervalo completo.
     *
     * Quadros sem pose no enquadramento simplesmente não chegam aqui: quem
     * chama passa só o que o MediaPipe resolveu. O buraco aparece como salto no
     * tempo, que é o que as features de velocidade usam.
     */
    fun offer(signals: FrameSignals): List<FrameSignals>? {
        if (nextSampleAtMs == Long.MIN_VALUE) {
            nextSampleAtMs = signals.timeMs
            firstSampleAtMs = signals.timeMs
        }
        if (signals.timeMs < nextSampleAtMs) return null

        samples.addLast(withVelocities(signals, samples.lastOrNull()))
        acceptedSamples++
        // Avança a grade pulando os instantes vencidos, em vez de emitir uma
        // rajada para "recuperar o atraso" — uma rajada seriam várias amostras
        // com a mesma pose.
        do {
            nextSampleAtMs += INTERVAL_MS
        } while (nextSampleAtMs <= signals.timeMs)

        val now = signals.timeMs
        val windowStart = now - windowMs

        // Guarda exatamente UMA amostra em windowStart ou antes: é ela que dá
        // velocidade à primeira amostra da janela, reproduzindo o treino, onde
        // as velocidades foram derivadas sobre a gravação inteira e só depois
        // fatiadas. Mais que uma seria memória sem uso.
        while (samples.size > 1 && samples.elementAt(1).timeMs <= windowStart) {
            samples.removeFirst()
        }

        // Só a partir daqui existe um intervalo de windowMs para fatiar.
        if (now - firstSampleAtMs < windowMs) return null
        if (lastWindowAtMs != Long.MIN_VALUE && now - lastWindowAtMs < hopMs) return null

        val window = samples.filter { it.timeMs > windowStart }
        if (window.size < minimumSamples) return null

        lastWindowAtMs = now
        return window
    }

    /**
     * Preenche as velocidades pelo tempo REAL entre amostras, não pelo índice.
     *
     * Com quadros perdidos, derivar pelo índice subestima a velocidade
     * exatamente nos trechos em que a pessoa se move rápido o bastante para sair
     * de quadro — isto é, nos trechos informativos.
     *
     * Na primeira amostra de uma série não há predecessor. O `features.py` usa
     * `bfill()`, que copia a velocidade da amostra seguinte; aqui a primeira sai
     * com zero e é corrigida quando a seguinte chega, em [patchFirstVelocity].
     */
    private fun withVelocities(current: FrameSignals, previous: FrameSignals?): FrameSignals {
        if (previous == null) return current
        val dtS = (current.timeMs - previous.timeMs) / 1000f
        if (dtS <= 1e-3f) return current
        val patched = current.copy(
            vYaw = (current.yaw - previous.yaw) / dtS,
            vFeetSpread = (current.feetSpread - previous.feetSpread) / dtS,
            vWeightLateral = (current.weightLateral - previous.weightLateral) / dtS,
            vHipHeight = (current.hipHeight - previous.hipHeight) / dtS,
        )
        patchFirstVelocity(patched)
        return patched
    }

    /** Replica o `bfill()` do Python: a primeira amostra herda a velocidade da segunda. */
    private fun patchFirstVelocity(second: FrameSignals) {
        if (samples.size != 1) return
        val first = samples.first()
        if (first.vYaw != 0f) return
        samples[0] = first.copy(
            vYaw = second.vYaw,
            vFeetSpread = second.vFeetSpread,
            vWeightLateral = second.vWeightLateral,
            vHipHeight = second.vHipHeight,
        )
    }

    companion object {
        /** 10 Hz, o mesmo do `MovementRecorder` que gravou o dataset. */
        const val INTERVAL_MS = 100L

        /**
         * Janela de 4,5 s — a configuração `C@4.5s`, a melhor medida sob LOPO
         * com o pipeline sem calibração do yaw, que é o que embarca.
         *
         * Não é constante livre: ela tem de casar com a janela do modelo em
         * `models/movement_classifier_meta.json`. Mudar aqui sem reexportar o
         * modelo (ou vice-versa) alimenta o grafo com features calculadas sobre
         * outro intervalo — sem erro, com resposta errada. O `MovementFeaturesTest`
         * compara contra o vetor-ouro, cuja janela sai desses metadados, então é
         * ele que acusa a divergência.
         */
        const val WINDOW_MS = 4_500L

        /** Passo entre janelas, igual ao `HOP_S` do `features.py`. */
        const val HOP_MS = 500L

        /** Igual ao `OCUPACAO_MINIMA` do `features.py`. */
        const val MIN_OCCUPANCY = 0.6f
    }
}
