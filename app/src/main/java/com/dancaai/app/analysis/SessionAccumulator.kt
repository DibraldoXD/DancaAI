package com.dancaai.app.analysis

import com.dancaai.app.PostureResult
import com.dancaai.app.WeightInfo
import com.dancaai.app.data.model.RhythmJudgement
import com.dancaai.app.data.model.SessionMetrics

/**
 * Acumula as saídas dos módulos de análise ao longo de uma sessão.
 *
 * Recebe um [PostureResult] por frame, o estado corrente do StepCounter e os
 * eventos de ritmo, e entrega as contagens brutas gravadas ao encerrar o treino.
 * Não calcula pontuação: a fórmula de score entra depois da coleta com
 * praticantes.
 */
class SessionAccumulator(
    private val rhythm: RhythmAnalyzer = RhythmAnalyzer(),
) {
    private var poseFrames = 0
    private var goodPostureFrames = 0
    private val postureIssueCounts = mutableMapOf<String, Int>()

    // O StepCounter mantém os contadores acumulados da sessão e reemite os totais
    // a cada frame — aqui o valor é substituído, nunca somado, senão contaria
    // a mesma transição uma vez por frame.
    private var correctTransitions = 0
    private var errorTransitions = 0

    fun onPosture(result: PostureResult) {
        when (result) {
            // Sem pose válida não há o que avaliar; o frame fica fora do denominador
            // para não penalizar o usuário por ter saído do enquadramento.
            PostureResult.Unknown -> return
            PostureResult.Good -> goodPostureFrames++
            is PostureResult.Bad -> result.issues.forEach { issue ->
                postureIssueCounts[issue] = (postureIssueCounts[issue] ?: 0) + 1
            }
        }
        poseFrames++
    }

    fun onWeight(info: WeightInfo) {
        correctTransitions = info.correctCount
        errorTransitions = info.errorCount
    }

    fun onBeat(audibleAtUptimeMs: Long, intervalMs: Long) {
        rhythm.onBeat(audibleAtUptimeMs, intervalMs)
    }

    /** Julga a transferência contra a grade de batidas; nulo se o metrônomo não está tocando. */
    fun onWeightTransition(atUptimeMs: Long): RhythmJudgement? =
        rhythm.onWeightTransition(atUptimeMs)

    fun snapshot(): SessionMetrics = SessionMetrics(
        poseFrames = poseFrames,
        goodPostureFrames = goodPostureFrames,
        postureIssueCounts = postureIssueCounts.toMap(),
        correctTransitions = correctTransitions,
        errorTransitions = errorTransitions,
        rhythm = rhythm.snapshot(),
    )

    fun reset() {
        poseFrames = 0
        goodPostureFrames = 0
        postureIssueCounts.clear()
        correctTransitions = 0
        errorTransitions = 0
        rhythm.reset()
    }
}
