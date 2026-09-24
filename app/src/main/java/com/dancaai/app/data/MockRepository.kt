package com.dancaai.app.data

import com.dancaai.app.data.model.UserProfile

/**
 * Último resíduo de dados mockados do protótipo de design.
 *
 * Onboarding, Home, Nova sessão, Treino, Resultado e Histórico já leem dados
 * reais via [SessionRepository]. Só a tela de Perfil ainda depende daqui, e
 * migra quando as preferências passarem a alimentá-la.
 */
object MockRepository {

    val profile = UserProfile(
        fullName = "Lucas Andrade",
        firstName = "Lucas",
        initial = "L",
        level = "Intermediário",
        favoriteStyle = "Forró",
        sessionsCount = 22,
        streakDays = 4,
    )
}
