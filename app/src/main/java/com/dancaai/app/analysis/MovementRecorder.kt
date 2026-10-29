package com.dancaai.app.analysis

import com.dancaai.app.data.model.CaptureMeta

/**
 * Grade temporal de uma gravação de movimento.
 *
 * Um passo de forró só se distingue de outro pela trajetória: balanço, giro
 * invertido e giro em linha passam pelas mesmas poses em instantes diferentes.
 * Uma captura por toque registra um instante e não separa os três — daí amostrar
 * a intervalo fixo enquanto a gravação está ativa.
 *
 * A classe não toca em câmera nem em rede: recebe o instante de cada frame e
 * responde se aquele frame vira amostra. Isso a mantém testável sem dispositivo.
 */
class MovementRecorder(private val intervalMs: Long = DEFAULT_INTERVAL_MS) {

    private var startedAtMs = 0L
    private var nextSampleAtMs = 0L
    private var sampleIndex = 0

    /** Identificador da gravação corrente; entra no rótulo como `r<n>`. */
    var recording = 0
        private set

    var isRecording = false
        private set

    /** Tempo decorrido da gravação corrente, para o rótulo do botão. */
    fun elapsedMs(nowMs: Long): Long = if (isRecording) nowMs - startedAtMs else 0L

    /** Amostras efetivamente emitidas na gravação corrente. */
    var sampleCount = 0
        private set

    fun start(nowMs: Long) {
        if (isRecording) return
        recording++
        startedAtMs = nowMs
        nextSampleAtMs = nowMs
        sampleIndex = 0
        sampleCount = 0
        isRecording = true
    }

    fun stop() {
        isRecording = false
    }

    /**
     * Aplica a trava de duração sem depender de frames.
     *
     * [onFrame] só roda quando há pose no enquadramento; se a pessoa sai de quadro
     * com a gravação ligada, nenhum frame chega e a trava nunca dispararia. Quem
     * conta o tempo na tela chama isto a cada tique.
     */
    fun stopIfExpired(nowMs: Long): Boolean {
        if (isRecording && nowMs - startedAtMs >= MAX_DURATION_MS) {
            stop()
            return true
        }
        return false
    }

    /**
     * Decide se o frame em [nowMs] vira amostra; devolve o metadado do rótulo
     * quando sim, `null` quando não.
     *
     * A grade avança por múltiplos de [intervalMs] a partir do início, e não a
     * partir do último frame aceito: assim um frame atrasado não desloca todas as
     * amostras seguintes. Quando a câmera entrega frames mais lentos que o
     * intervalo, o laço pula os instantes vencidos em vez de emitir uma rajada
     * para "recuperar o atraso" — uma rajada seriam várias linhas com a mesma pose.
     */
    fun onFrame(nowMs: Long): CaptureMeta.Frame? {
        if (!isRecording) return null
        if (stopIfExpired(nowMs)) return null
        if (nowMs < nextSampleAtMs) return null

        val meta = CaptureMeta.Frame(
            recording = recording,
            sample = sampleIndex,
            elapsedMs = nowMs - startedAtMs,
        )
        sampleIndex++
        sampleCount++
        do {
            nextSampleAtMs += intervalMs
        } while (nextSampleAtMs <= nowMs)
        return meta
    }

    companion object {
        /**
         * 10 Hz. Resolve a trajetória de um giro sem inviabilizar o envio: o Apps
         * Script recebe uma linha por POST, e 30 Hz geraria ~120 requisições num
         * passo de 4 s.
         */
        const val DEFAULT_INTERVAL_MS = 100L

        /**
         * Trava de segurança: uma gravação esquecida ligada encheria a planilha
         * de linhas inúteis e só seria notada depois da coleta.
         */
        const val MAX_DURATION_MS = 30_000L
    }
}
