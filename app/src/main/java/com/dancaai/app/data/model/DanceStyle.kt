package com.dancaai.app.data.model

/** Estilo de dança suportado (id, nome, emoji e faixa de BPM ideal). */
data class DanceStyle(
    val id: String,
    val name: String,
    val emoji: String,
    val bpmRange: String,
)

/** Nível do dançarino, escolhido no onboarding. */
data class DanceLevel(
    val id: String,
    val label: String,
    val description: String,
)
