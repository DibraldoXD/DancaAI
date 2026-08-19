package com.dancaai.app.data.model

/**
 * Medições brutas coletadas durante uma sessão de treino.
 *
 * São contagens, não pontuações. A fórmula de score de cada módulo será definida
 * depois da coleta com praticantes; guardar os dados crus permite calculá-la
 * retroativamente sobre as sessões já gravadas, em vez de congelar agora um
 * número cujo significado mudaria junto com a fórmula.
 */
data class SessionMetrics(
    /** Frames em que o MediaPipe entregou pose suficiente para avaliar a postura. */
    val poseFrames: Int = 0,
    val goodPostureFrames: Int = 0,
    /** Quantas vezes cada desvio postural foi detectado, por rótulo do PostureValidator. */
    val postureIssueCounts: Map<String, Int> = emptyMap(),
    val correctTransitions: Int = 0,
    val errorTransitions: Int = 0,
    val rhythm: RhythmMetrics = RhythmMetrics(),
) {
    /** Fração dos frames avaliados em que a postura estava conforme; nula se nada foi avaliado. */
    val goodPostureRatio: Float?
        get() = if (poseFrames > 0) goodPostureFrames / poseFrames.toFloat() else null

    val totalTransitions: Int get() = correctTransitions + errorTransitions

    /** Fração de transferências de peso corretas; nula se não houve transição. */
    val correctTransitionRatio: Float?
        get() = if (totalTransitions > 0) correctTransitions / totalTransitions.toFloat() else null

    /** Desvios do mais frequente ao menos frequente — base dos "pontos a melhorar". */
    val topPostureIssues: List<Pair<String, Int>>
        get() = postureIssueCounts.entries
            .sortedByDescending { it.value }
            .map { it.key to it.value }

    /** Sessão sem nenhuma medição — câmera nunca viu uma pose válida. */
    val isEmpty: Boolean get() = poseFrames == 0 && totalTransitions == 0 && rhythm.isEmpty
}
