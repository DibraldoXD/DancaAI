package com.dancaai.app.data.model

/**
 * Perfil do usuário: o que ele informou no onboarding somado ao que o histórico
 * de sessões diz sobre a prática.
 *
 * Nada aqui é digitado duas vezes — nome e nível vêm das preferências, e
 * contagem, sequência e estilo mais treinado são derivados das sessões gravadas.
 */
data class ProfileSummary(
    val name: String,
    val initial: String,
    val level: String,
    val favoriteStyle: String,
    val sessionsCount: Int,
    val streakDays: Int,
    val vibrationEnabled: Boolean,
    val voiceFeedbackEnabled: Boolean,
    /** Espaço ocupado pelo banco de sessões, em bytes. */
    val storageBytes: Long,
)

/** Resumo da Home: último treino + evolução da semana. */
data class HomeSummary(
    val greeting: String,
    val firstName: String,
    val initial: String,
    val lastSession: Session?,
    /** Média diária dos últimos 7 dias; vazio enquanto nenhum módulo tiver medido. */
    val weekScores: List<Int>,
    val weekDays: List<String>,
    val weekAverage: Int?,
    val weekDeltaPercent: Int?,
    val weekSessions: Int,
    val streakDays: Int,
)
