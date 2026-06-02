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

/** Faixa musical selecionada para a sessão (tela de Configurar Sessão). */
data class Music(
    val title: String,
    val artist: String,
    val duration: String,
    val bpm: Int,
    val key: String,
)

/** Resumo da Home: último treino + evolução da semana. */
data class HomeSummary(
    val greeting: String,
    val lastSession: Session,
    val weekScores: List<Int>,
    val weekDays: List<String>,
    val weekAverage: Int,
    val weekDeltaPercent: Int,
    val weekSessions: Int,
)
