package com.dancaai.app.data

import android.content.Context
import com.dancaai.app.PostureIssue
import com.dancaai.app.data.local.DancaDatabase
import com.dancaai.app.data.local.SessionEntity
import com.dancaai.app.data.local.UserPreferences
import com.dancaai.app.data.local.UserSettings
import com.dancaai.app.data.model.HistorySummary
import com.dancaai.app.data.model.HomeSummary
import com.dancaai.app.data.model.ModuleScores
import com.dancaai.app.data.model.RhythmMetrics
import com.dancaai.app.data.model.Session
import com.dancaai.app.data.model.SessionConfig
import com.dancaai.app.data.model.SessionMetrics
import com.dancaai.app.data.model.SessionOutcome
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Porta única de acesso ao histórico de sessões e às preferências do usuário.
 * As telas observam os Flows daqui — nenhuma delas conhece Room ou DataStore.
 */
class SessionRepository(context: Context) {

    private val dao = DancaDatabase.get(context).sessionDao()
    private val preferences = UserPreferences(context)

    val settings: Flow<UserSettings> = preferences.settings

    val history: Flow<List<Session>> = dao.observeAll().map { entities ->
        entities.map { it.toSession() }
    }

    val historySummary: Flow<HistorySummary> = dao.observeAll().map { it.toHistorySummary() }

    val homeSummary: Flow<HomeSummary> =
        combine(dao.observeAll(), preferences.settings) { entities, user ->
            entities.toHomeSummary(user)
        }

    /** Grava a sessão recém-encerrada e devolve o id gerado. */
    suspend fun saveSession(
        config: SessionConfig,
        startedAtEpochMs: Long,
        actualDurationSec: Int,
        metrics: SessionMetrics = SessionMetrics(),
    ): Long = dao.insert(
        SessionEntity(
            styleId = config.styleId,
            startedAtEpochMs = startedAtEpochMs,
            plannedDurationSec = config.durationSec,
            actualDurationSec = actualDurationSec,
            bpm = config.bpm,
            beatPattern = config.beatPattern.name,
            poseFrames = metrics.poseFrames,
            goodPostureFrames = metrics.goodPostureFrames,
            postureIssuesCsv = encodeIssueCounts(metrics.postureIssueCounts),
            correctTransitions = metrics.correctTransitions,
            errorTransitions = metrics.errorTransitions,
            rhythmOnTime = metrics.rhythm.onTimeTransitions,
            rhythmEarly = metrics.rhythm.earlyTransitions,
            rhythmLate = metrics.rhythm.lateTransitions,
            rhythmAbsOffsetSumMs = metrics.rhythm.absOffsetSumMs,
            rhythmIntervalSumMs = metrics.rhythm.transitionIntervalSumMs,
            rhythmIntervalCount = metrics.rhythm.transitionIntervalCount,
        ),
    )

    suspend fun findSession(id: Long): Session? = dao.findById(id)?.toSession()

    /** A sessão com a anterior ao lado, para a tela de Resultado comparar as medições. */
    suspend fun findOutcome(id: Long): SessionOutcome? {
        val entity = dao.findById(id) ?: return null
        return SessionOutcome(
            session = entity.toSession(),
            previous = dao.findPrevious(entity.startedAtEpochMs)?.toSession(),
        )
    }

    suspend fun clearHistory() = dao.deleteAll()

    suspend fun completeOnboarding(name: String, levelId: String) =
        preferences.completeOnboarding(name, levelId)

    suspend fun setVibrationEnabled(enabled: Boolean) = preferences.setVibrationEnabled(enabled)

    suspend fun setVoiceFeedbackEnabled(enabled: Boolean) =
        preferences.setVoiceFeedbackEnabled(enabled)
}

// ─────────────────────────── mapeamento e agregação ───────────────────────────

private const val HISTORY_WINDOW_DAYS = 30
private const val WEEK_DAYS = 7

private val LOCALE_BR: Locale = Locale.forLanguageTag("pt-BR")

/** Inicial do dia da semana, indexada por Calendar.DAY_OF_WEEK - 1 (domingo = 0). */
private val WEEKDAY_INITIALS = listOf("D", "S", "T", "Q", "Q", "S", "S")

private fun SessionEntity.sessionMetrics() = SessionMetrics(
    poseFrames = poseFrames,
    goodPostureFrames = goodPostureFrames,
    postureIssueCounts = decodeIssueCounts(postureIssuesCsv),
    correctTransitions = correctTransitions,
    errorTransitions = errorTransitions,
    rhythm = RhythmMetrics(
        onTimeTransitions = rhythmOnTime,
        earlyTransitions = rhythmEarly,
        lateTransitions = rhythmLate,
        absOffsetSumMs = rhythmAbsOffsetSumMs,
        transitionIntervalSumMs = rhythmIntervalSumMs,
        transitionIntervalCount = rhythmIntervalCount,
    ),
)

/**
 * Pontuação derivada das medições. Vazia por enquanto — a fórmula de score de
 * cada módulo será definida após a coleta com praticantes, e é aqui que ela
 * entra, passando a valer também para as sessões já gravadas.
 */
private fun SessionEntity.moduleScores() = ModuleScores()

private fun SessionEntity.toSession(): Session {
    val style = DanceCatalog.styleById(styleId)
    return Session(
        id = id,
        styleName = style.name,
        emoji = style.emoji,
        date = formatSessionDate(startedAtEpochMs),
        durationMin = (actualDurationSec / 60f).roundToInt().coerceAtLeast(1),
        scores = moduleScores(),
        metrics = sessionMetrics(),
    )
}

/**
 * Serializa as contagens de desvio como `NOME:n;NOME:n`, usando o nome da
 * constante do enum — identificador estável, ao contrário do texto exibido, que
 * pode ser reescrito sem invalidar as sessões já gravadas.
 */
internal fun encodeIssueCounts(counts: Map<PostureIssue, Int>): String? =
    counts.takeIf { it.isNotEmpty() }
        ?.entries
        ?.joinToString(";") { "${it.key.name}:${it.value}" }

internal fun decodeIssueCounts(csv: String?): Map<PostureIssue, Int> =
    csv?.split(';')
        ?.mapNotNull { entry ->
            val name = entry.substringBeforeLast(':', "")
            val count = entry.substringAfterLast(':', "").toIntOrNull()
            // um desvio removido do enum some do histórico em vez de derrubar a leitura
            val issue = POSTURE_ISSUES_BY_NAME[name]
            if (issue == null || count == null) null else issue to count
        }
        ?.toMap()
        .orEmpty()

private val POSTURE_ISSUES_BY_NAME: Map<String, PostureIssue> =
    PostureIssue.entries.associateBy { it.name }

private fun List<SessionEntity>.averageScore(): Int? =
    mapNotNull { it.moduleScores().total }
        .takeIf { it.isNotEmpty() }
        ?.average()
        ?.roundToInt()

internal fun percentDelta(current: Int?, previous: Int?): Int? =
    if (current == null || previous == null || previous == 0) null
    else (((current - previous) / previous.toFloat()) * 100).roundToInt()

private fun List<SessionEntity>.toHistorySummary(): HistorySummary {
    val byDay = groupBy { daysAgo(it.startedAtEpochMs) }

    // barras do dia mais antigo da janela até hoje
    val bars = (HISTORY_WINDOW_DAYS - 1 downTo 0).map { day ->
        val ofDay = byDay[day].orEmpty()
        when {
            ofDay.isEmpty() -> 0
            // dia treinado mas sem módulo medido: 1 acende a barra, distinguindo-a de um dia parado
            else -> ofDay.averageScore() ?: 1
        }
    }

    val window = filter { daysAgo(it.startedAtEpochMs) in 0 until HISTORY_WINDOW_DAYS }
    val previous = filter {
        daysAgo(it.startedAtEpochMs) in HISTORY_WINDOW_DAYS until HISTORY_WINDOW_DAYS * 2
    }
    val average = window.averageScore()

    return HistorySummary(
        average = average,
        deltaPercent = percentDelta(average, previous.averageScore()),
        sessions = window.size,
        bars = bars,
    )
}

private fun List<SessionEntity>.toHomeSummary(user: UserSettings): HomeSummary {
    val byDay = groupBy { daysAgo(it.startedAtEpochMs) }
    val week = filter { daysAgo(it.startedAtEpochMs) in 0 until WEEK_DAYS }
    val previousWeek = filter { daysAgo(it.startedAtEpochMs) in WEEK_DAYS until WEEK_DAYS * 2 }
    val weekAverage = week.averageScore()

    return HomeSummary(
        greeting = greetingForNow(),
        firstName = user.firstName,
        initial = user.initial,
        // o DAO devolve da mais recente para a mais antiga
        lastSession = firstOrNull()?.toSession(),
        // um ponto por dia medido dos últimos 7, do mais antigo ao mais recente
        weekScores = (WEEK_DAYS - 1 downTo 0).mapNotNull { byDay[it]?.averageScore() },
        weekDays = lastWeekDayLabels(),
        weekAverage = weekAverage,
        weekDeltaPercent = percentDelta(weekAverage, previousWeek.averageScore()),
        weekSessions = week.size,
        streakDays = streakDays(),
    )
}

/** Dias consecutivos com pelo menos um treino. A sequência só vale se houve treino hoje ou ontem. */
internal fun List<SessionEntity>.streakDays(): Int {
    val days = map { daysAgo(it.startedAtEpochMs) }.toSet()
    var day = when {
        0 in days -> 0
        1 in days -> 1
        else -> return 0
    }
    var streak = 0
    while (day in days) {
        streak++
        day++
    }
    return streak
}

// ─────────────────────────────── datas ────────────────────────────────

private fun startOfDay(epochMs: Long): Long = Calendar.getInstance().apply {
    timeInMillis = epochMs
    set(Calendar.HOUR_OF_DAY, 0)
    set(Calendar.MINUTE, 0)
    set(Calendar.SECOND, 0)
    set(Calendar.MILLISECOND, 0)
}.timeInMillis

/**
 * Distância em dias de calendário até hoje: 0 = hoje, 1 = ontem.
 * O arredondamento absorve dias de 23 ou 25 horas em fusos com horário de verão.
 */
private fun daysAgo(epochMs: Long): Int {
    val diff = startOfDay(System.currentTimeMillis()) - startOfDay(epochMs)
    return (diff.toDouble() / 86_400_000.0).roundToInt()
}

private fun lastWeekDayLabels(): List<String> =
    (WEEK_DAYS - 1 downTo 0).map { day ->
        val calendar = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -day) }
        WEEKDAY_INITIALS[calendar.get(Calendar.DAY_OF_WEEK) - 1]
    }

private fun greetingForNow(): String =
    when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
        in 0..11 -> "Bom dia,"
        in 12..17 -> "Boa tarde,"
        else -> "Boa noite,"
    }

private fun formatSessionDate(epochMs: Long): String {
    val date = Date(epochMs)
    val time = SimpleDateFormat("HH:mm", LOCALE_BR).format(date)
    return when (daysAgo(epochMs)) {
        0 -> "Hoje · $time"
        1 -> "Ontem · $time"
        in 2..6 -> {
            val weekday = SimpleDateFormat("EEE", LOCALE_BR).format(date)
                .removeSuffix(".")
                .replaceFirstChar { it.uppercase() }
            "$weekday · $time"
        }
        else -> {
            val day = SimpleDateFormat("dd MMM", LOCALE_BR).format(date).removeSuffix(".")
            "$day · $time"
        }
    }
}
