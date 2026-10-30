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

    /** Detalhe de uma sessão já gravada, aberto a partir do Histórico. */
    const val SESSION_ID_ARG = "sessionId"
    const val SESSION_DETAIL = "session_detail/{$SESSION_ID_ARG}"

    fun sessionDetail(sessionId: Long): String = "session_detail/$sessionId"

    /** Rotas de nível superior que exibem a barra de navegação inferior. */
    val topLevel = setOf(HOME, HISTORY, PROFILE)
}
