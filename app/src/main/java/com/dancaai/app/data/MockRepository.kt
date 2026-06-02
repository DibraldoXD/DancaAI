package com.dancaai.app.data

import com.dancaai.app.data.model.DanceLevel
import com.dancaai.app.data.model.DanceStyle
import com.dancaai.app.data.model.HomeSummary
import com.dancaai.app.data.model.Improvement
import com.dancaai.app.data.model.Music
import com.dancaai.app.data.model.Session
import com.dancaai.app.data.model.SessionResult
import com.dancaai.app.data.model.UserProfile

/**
 * Fonte única de dados mockados, espelhando o protótipo do design.
 * Será substituída/alimentada pelas funcionalidades reais (MediaPipe, áudio,
 * persistência local) à medida que forem implementadas.
 */
object MockRepository {

    val styles = listOf(
        DanceStyle("forro", "Forró", "💃", "112–148"),
        DanceStyle("zouk", "Zouk", "🌊", "84–96"),
        DanceStyle("samba", "Samba de Gafieira", "🎺", "108–132"),
        DanceStyle("bolero", "Bolero", "🌙", "60–80"),
    )

    val levels = listOf(
        DanceLevel("iniciante", "Iniciante", "Estou começando agora"),
        DanceLevel("intermediario", "Intermediário", "Já danço há algum tempo"),
    )

    val profile = UserProfile(
        fullName = "Lucas Andrade",
        firstName = "Lucas",
        initial = "L",
        level = "Intermediário",
        favoriteStyle = "Forró",
        sessionsCount = 22,
        streakDays = 4,
    )

    val defaultMusic = Music(
        title = "Esperando na Janela",
        artist = "Gilberto Gil",
        duration = "3:42",
        bpm = 128,
        key = "D",
    )

    val sessions = listOf(
        Session(1, "Forró", "💃", "Hoje · 21:42", 5, 78, 84),
        Session(2, "Forró", "💃", "Ontem · 19:10", 10, 74, 80),
        Session(3, "Zouk", "🌊", "Sex · 18:34", 5, 81, 72),
        Session(4, "Bolero", "🌙", "Qui · 22:01", 3, 65, 68),
        Session(5, "Forró", "💃", "Ter · 19:55", 5, 70, 71),
        Session(6, "Samba de Gafieira", "🎺", "Seg · 20:23", 5, 62, 58),
    )

    val homeSummary = HomeSummary(
        greeting = "Boa noite,",
        lastSession = sessions.first(),
        weekScores = listOf(62, 68, 65, 72, 74, 81, 84),
        weekDays = listOf("S", "T", "Q", "Q", "S", "S", "D"),
        weekAverage = 74,
        weekDeltaPercent = 12,
        weekSessions = 7,
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

    // Histórico — últimos 30 dias (0 = dia sem treino).
    val monthlyAverage = 76
    val monthlyDeltaPercent = 18
    val monthlySessions = 22
    val monthlyBars = listOf(
        40, 0, 55, 60, 0, 0, 65, 50, 0, 70, 0, 0, 62, 68, 75,
        0, 72, 80, 0, 0, 78, 82, 0, 76, 84, 0, 0, 80, 86, 81,
    )
}
