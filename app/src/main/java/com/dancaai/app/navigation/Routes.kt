package com.dancaai.app.navigation

/** Rotas de navegação do app. */
object Routes {
    const val ONBOARDING = "onboarding"
    const val HOME = "home"
    const val SESSION = "session"
    const val TRAINING = "training"
    const val RESULTS = "results"
    const val HISTORY = "history"
    const val PROFILE = "profile"

    /** Rotas de nível superior que exibem a barra de navegação inferior. */
    val topLevel = setOf(HOME, HISTORY, PROFILE)
}
