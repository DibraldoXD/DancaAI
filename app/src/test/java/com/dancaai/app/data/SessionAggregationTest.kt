package com.dancaai.app.data

import com.dancaai.app.PostureIssue
import com.dancaai.app.data.local.SessionEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Calendar

class SessionAggregationTest {

    // ───────────────────────────── sequência ─────────────────────────────

    @Test
    fun `sem sessoes nao ha sequencia`() {
        assertEquals(0, emptyList<SessionEntity>().streakDays())
    }

    @Test
    fun `treino hoje conta um dia`() {
        assertEquals(1, sessionsAt(0).streakDays())
    }

    @Test
    fun `dias consecutivos a partir de hoje somam`() {
        assertEquals(3, sessionsAt(0, 1, 2).streakDays())
    }

    @Test
    fun `sequencia continua quando o ultimo treino foi ontem`() {
        assertEquals(2, sessionsAt(1, 2).streakDays())
    }

    @Test
    fun `mais de um treino no mesmo dia conta uma vez`() {
        assertEquals(2, sessionsAt(0, 0, 1).streakDays())
    }

    @Test
    fun `buraco interrompe a sequencia`() {
        // treinou hoje e anteontem, mas faltou ontem
        assertEquals(1, sessionsAt(0, 2).streakDays())
    }

    @Test
    fun `sequencia zera quando o ultimo treino tem mais de um dia`() {
        assertEquals(0, sessionsAt(2, 3, 4).streakDays())
    }

    // ────────────────────────── variação percentual ──────────────────────────

    @Test
    fun `variacao percentual compara com o periodo anterior`() {
        assertEquals(14, percentDelta(80, 70))
        assertEquals(-20, percentDelta(40, 50))
    }

    @Test
    fun `variacao percentual e nula sem base de comparacao`() {
        assertNull(percentDelta(80, null))
        assertNull(percentDelta(null, 70))
        assertNull(percentDelta(80, 0))
    }

    // ─────────────────────── serialização dos desvios ───────────────────────

    @Test
    fun `contagens de desvio sobrevivem ao round-trip`() {
        val counts = mapOf(
            PostureIssue.SHOULDERS_FORWARD to 120,
            PostureIssue.TRUNK_TILT_RIGHT to 45,
        )

        assertEquals(counts, decodeIssueCounts(encodeIssueCounts(counts)))
    }

    @Test
    fun `mapa vazio nao gera coluna`() {
        assertNull(encodeIssueCounts(emptyMap()))
        assertEquals(emptyMap<PostureIssue, Int>(), decodeIssueCounts(null))
    }

    @Test
    fun `desvio desconhecido e descartado sem quebrar a leitura`() {
        // uma constante removida do enum não pode derrubar a leitura do histórico
        assertEquals(
            mapOf(PostureIssue.SHOULDERS_FORWARD to 12),
            decodeIssueCounts("SHOULDERS_FORWARD:12;DESVIO_REMOVIDO:9;lixo;TRUNK_TILT_LEFT:x"),
        )
    }

    @Test
    fun `serializacao usa o nome da constante e nao o texto exibido`() {
        // o rótulo pode ser reescrito sem invalidar sessões já gravadas
        assertEquals(
            "SHOULDERS_FORWARD:7",
            encodeIssueCounts(mapOf(PostureIssue.SHOULDERS_FORWARD to 7)),
        )
    }

    // ──────────────────────────────── helpers ────────────────────────────────

    private fun sessionsAt(vararg daysAgo: Int): List<SessionEntity> =
        daysAgo.mapIndexed { index, days ->
            SessionEntity(
                id = index.toLong() + 1,
                styleId = DanceCatalog.FORRO_ID,
                startedAtEpochMs = middayDaysAgo(days),
                plannedDurationSec = 300,
                actualDurationSec = 300,
                bpm = 120,
            )
        }

    /** Meio-dia do dia indicado, para os testes não esbarrarem na virada da meia-noite. */
    private fun middayDaysAgo(days: Int): Long = Calendar.getInstance().apply {
        add(Calendar.DAY_OF_YEAR, -days)
        set(Calendar.HOUR_OF_DAY, 12)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
}
