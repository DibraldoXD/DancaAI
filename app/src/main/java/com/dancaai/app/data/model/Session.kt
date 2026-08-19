package com.dancaai.app.data.model

import kotlin.math.roundToInt

/**
 * Pontuação de uma sessão nos quatro módulos de análise, em 0..100.
 *
 * Nulo significa "este módulo ainda não mediu esta sessão". Os módulos entram em
 * etapas diferentes do desenvolvimento, e o nulo explícito impede que a interface
 * exiba um zero — que o usuário leria como desempenho péssimo — no lugar de uma
 * medição que simplesmente não aconteceu.
 */
data class ModuleScores(
    val weight: Int? = null,
    val posture: Int? = null,
    val rhythm: Int? = null,
    val movement: Int? = null,
) {
    val measured: List<Int> get() = listOfNotNull(weight, posture, rhythm, movement)

    /** Média dos módulos efetivamente medidos; nulo quando nenhum mediu. */
    val total: Int? get() = measured.takeIf { it.isNotEmpty() }?.average()?.roundToInt()
}

/** Uma sessão de treino concluída (item de histórico / último treino). */
data class Session(
    val id: Long,
    val styleName: String,
    val emoji: String,
    val date: String,
    val durationMin: Int,
    /** Derivado de [metrics]; todos nulos enquanto a fórmula de score não estiver definida. */
    val scores: ModuleScores,
    val metrics: SessionMetrics,
)

/**
 * A sessão recém-encerrada junto da anterior, que serve de base de comparação na
 * tela de Resultado. [previous] é nula na primeira sessão do usuário.
 */
data class SessionOutcome(
    val session: Session,
    val previous: Session?,
)

/** Resumo do histórico dos últimos 30 dias (cabeçalho da tela de Histórico). */
data class HistorySummary(
    val average: Int?,
    val deltaPercent: Int?,
    val sessions: Int,
    /** Uma barra por dia dos últimos 30; 0 = dia sem treino. */
    val bars: List<Int>,
)

