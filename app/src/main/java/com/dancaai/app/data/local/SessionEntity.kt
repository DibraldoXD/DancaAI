package com.dancaai.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Uma sessão de treino gravada.
 *
 * Não há coluna de pontuação: a tabela guarda as medições brutas dos módulos de
 * análise, e o score é derivado delas na leitura. Assim, quando a fórmula for
 * definida a partir da coleta com praticantes, ela vale também para as sessões
 * já gravadas — o que não aconteceria se o número tivesse sido congelado aqui.
 */
@Entity(tableName = "sessions")
data class SessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val styleId: String,
    val startedAtEpochMs: Long,
    val plannedDurationSec: Int,
    /** Tempo efetivamente treinado, que difere do planejado quando o usuário encerra antes. */
    val actualDurationSec: Int,
    val bpm: Int,
    /** Nome do BeatPattern usado; guardado como texto para não amarrar o banco ao enum. */
    val beatPattern: String = "",

    // ── módulo de postura ──
    /** Frames com pose válida avaliados; é o denominador das razões de postura. */
    val poseFrames: Int = 0,
    val goodPostureFrames: Int = 0,
    /** Contagem por tipo de desvio, no formato `ROTULO:n;ROTULO:n`. */
    val postureIssuesCsv: String? = null,

    // ── módulo de transferência de peso ──
    val correctTransitions: Int = 0,
    val errorTransitions: Int = 0,

    // ── módulo de ritmo ──
    val rhythmOnTime: Int = 0,
    val rhythmEarly: Int = 0,
    val rhythmLate: Int = 0,
    val rhythmAbsOffsetSumMs: Long = 0,
    val rhythmIntervalSumMs: Long = 0,
    val rhythmIntervalCount: Int = 0,
)
