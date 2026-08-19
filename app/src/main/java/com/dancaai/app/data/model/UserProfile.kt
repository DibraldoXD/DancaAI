package com.dancaai.app.data.model

/** Perfil do usuário (cabeçalho do Perfil e saudação da Home). */
data class UserProfile(
    val fullName: String,
    val firstName: String,
    val initial: String,
    val level: String,
    val favoriteStyle: String,
    val sessionsCount: Int,
    val streakDays: Int,
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
