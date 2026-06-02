package com.dancaai.app.data.model

/** Uma sessão de treino concluída (item de histórico / último treino). */
data class Session(
    val id: Int,
    val styleName: String,
    val emoji: String,
    val date: String,
    val durationMin: Int,
    val posture: Int,
    val rhythm: Int,
)

/** Ponto a melhorar exibido na tela de Resultado. */
data class Improvement(
    val title: String,
    val description: String,
)

/** Resultado detalhado de uma sessão (tela de Resultado). */
data class SessionResult(
    val total: Int,
    val posture: Int,
    val rhythm: Int,
    val postureDelta: Int,
    val rhythmDelta: Int,
    val posturePrev: Int,
    val rhythmPrev: Int,
    val series: List<Int>,
    val summary: String,
    val highlightTitle: String,
    val highlightBody: String,
    val improvements: List<Improvement>,
)
