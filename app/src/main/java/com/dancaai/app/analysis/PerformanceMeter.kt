package com.dancaai.app.analysis

import kotlin.math.roundToLong

/**
 * Resumo de desempenho de uma sessão: FPS e latência do pipeline de visão.
 *
 * FPS são amostras de 1 s (frames entregues na janela ÷ duração real dela), não
 * 1000/Δt entre frames, que é ruidoso e produz máximos sem sentido. Latência é por
 * frame, da captura ao resultado do MediaPipe pronto.
 */
data class PerformanceSummary(
    /** Frames medidos (todos os que tiveram latência registrada). */
    val frames: Int,
    val fpsMean: Double,
    val fpsMedian: Double,
    val fpsMax: Double,
    val fpsMin: Double,
    val latencyMeanMs: Double,
    val latencyMedianMs: Double,
    val latencyMaxMs: Long,
    val latencyMinMs: Long,
) {
    companion object {
        /** Colunas de coordenadas já existentes na planilha (Timestamp, Rotulo, 33 coords, 3 métricas). */
        const val COORDINATE_COLUMNS = 38

        /** Rótulo da linha de resumo; os scripts de treino a descartam pelo filtro de rótulo. */
        const val SHEET_LABEL = "metricas_sessao"

        /** Cabeçalho das colunas novas, na ordem de [toSheetRow] (colunas 39 em diante). */
        val SHEET_HEADER = listOf(
            "Sessao_Horario",
            "FPS_Media", "FPS_Mediana", "FPS_Max", "FPS_Min",
            "Latencia_Media_ms", "Latencia_Mediana_ms", "Latencia_Max_ms", "Latencia_Min_ms",
        )
    }

    /**
     * Linha pra planilha: as [COORDINATE_COLUMNS] colunas de coordenadas vazias (só o
     * rótulo preenchido) seguidas das 9 colunas de desempenho, ao lado delas.
     * [sessionEnd] é o horário do fim da sessão, no mesmo formato da coluna Timestamp.
     */
    fun toSheetRow(sessionEnd: String): List<Any> {
        val row = MutableList<Any>(COORDINATE_COLUMNS) { "" }
        row[1] = SHEET_LABEL
        row.addAll(
            listOf(
                sessionEnd,
                round2(fpsMean), round2(fpsMedian), round2(fpsMax), round2(fpsMin),
                round2(latencyMeanMs), round2(latencyMedianMs), latencyMaxMs, latencyMinMs,
            ),
        )
        return row
    }

    private fun round2(v: Double): Double = (v * 100.0).roundToLong() / 100.0
}

/**
 * Medidor de FPS e latência. Classe pura (sem Android): quem chama passa os instantes,
 * todos na base de `SystemClock.uptimeMillis`.
 *
 * Uma única thread deve usá-lo (na app, a main, a mesma que recebe os resultados).
 */
class PerformanceMeter(private val windowMs: Long = 1_000) {

    private var latencies = LongArray(4_096)
    private var latencyCount = 0
    private val fpsSamples = ArrayList<Double>()

    private var windowStartMs: Long? = null
    private var windowFrames = 0

    /** Registra um frame entregue em [deliveredAtMs] com [latencyMs] de captura até resultado. */
    fun onFrame(deliveredAtMs: Long, latencyMs: Long) {
        if (latencyCount == latencies.size) latencies = latencies.copyOf(latencies.size * 2)
        latencies[latencyCount++] = latencyMs

        val start = windowStartMs
        if (start == null) {
            windowStartMs = deliveredAtMs
            windowFrames = 1
            return
        }
        val span = deliveredAtMs - start
        if (span >= windowMs) {
            // a janela fechou: `windowFrames` intervalos cobrem exatamente `span` ms
            fpsSamples += windowFrames * 1_000.0 / span
            windowStartMs = deliveredAtMs
            windowFrames = 1
        } else {
            windowFrames++
        }
    }

    /**
     * Descarta a janela de FPS em andamento. Pausa, retomada e troca de câmera deixam
     * um buraco sem frames que daria uma amostra de FPS artificialmente baixa.
     */
    fun discardWindow() {
        windowStartMs = null
        windowFrames = 0
    }

    /** `null` enquanto não houver ao menos uma amostra de FPS (janela de 1 s completa). */
    fun summary(): PerformanceSummary? {
        if (latencyCount == 0 || fpsSamples.isEmpty()) return null
        val lat = latencies.copyOf(latencyCount).also { it.sort() }
        val fps = fpsSamples.sorted()
        return PerformanceSummary(
            frames = latencyCount,
            fpsMean = fps.average(),
            fpsMedian = medianOfSorted(fps),
            fpsMax = fps.last(),
            fpsMin = fps.first(),
            latencyMeanMs = lat.average(),
            latencyMedianMs = medianOfSorted(lat),
            latencyMaxMs = lat.last(),
            latencyMinMs = lat.first(),
        )
    }

    fun reset() {
        latencyCount = 0
        fpsSamples.clear()
        discardWindow()
    }

    private fun medianOfSorted(v: List<Double>): Double {
        val n = v.size
        return if (n % 2 == 1) v[n / 2] else (v[n / 2 - 1] + v[n / 2]) / 2.0
    }

    private fun medianOfSorted(v: LongArray): Double {
        val n = v.size
        return if (n % 2 == 1) v[n / 2].toDouble() else (v[n / 2 - 1] + v[n / 2]) / 2.0
    }
}
