package com.dancaai.app.navigation

/** Rotas de navegação do app. */
object Routes {
    const val ONBOARDING = "onboarding"
    const val HOME = "home"
    const val HISTORY = "history"
    const val PROFILE = "profile"

    /**
     * Subgrafo do fluxo de sessão. Existe para dar um escopo de ViewModel comum a
     * Nova sessão, Treino e Resultado — é ele que carrega a configuração escolhida
     * de uma tela para a outra.
     */
    const val SESSION_FLOW = "session_flow"
    const val SESSION = "session"
    const val TRAINING = "training"
    const val RESULTS = "results"

    /** Rotas de nível superior que exibem a barra de navegação inferior. */
    val topLevel = setOf(HOME, HISTORY, PROFILE)
}
