package com.dancaai.app.data

import com.dancaai.app.data.model.Improvement
import com.dancaai.app.data.model.SessionResult
import com.dancaai.app.data.model.UserProfile

/**
 * Resíduo de dados mockados do protótipo de design.
 *
 * Histórico, Home, onboarding e configuração de sessão já leem dados reais
 * (Room/DataStore, via [SessionRepository]). Sobraram aqui as telas de Resultado
 * e Perfil, que serão migradas quando os módulos de análise produzirem as
 * pontuações que elas exibem.
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

    val lastResult = SessionResult(
        total = 81,
        posture = 78,
        rhythm = 84,
        postureDelta = 3,
        rhythmDelta = 12,
        posturePrev = 75,
        rhythmPrev = 72,
        series = listOf(55, 62, 60, 68, 72, 76, 74, 80, 82, 78, 84, 81),
        summary = "Forró · 5 min · 21 maio · 21:42",
        highlightTitle = "Seu ritmo melhorou 12%",
        highlightBody = "Em relação à sua sessão anterior de forró. Continue assim!",
        improvements = listOf(
            Improvement(
                "Ângulo do joelho esquerdo",
                "Abaixo do ideal em 70% da sessão. Tente flexionar mais.",
            ),
            Improvement(
                "Inclinação do tronco",
                "Você se inclinou pra frente em movimentos de quebra.",
            ),
            Improvement(
                "Timing da quebra do quadril",
                "Atrás do beat em ~0.2s em médias e altas.",
            ),
        ),
    )
}
