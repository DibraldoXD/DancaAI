package com.dancaai.app.analysis

import android.util.Log
import com.dancaai.app.WeightTrace
import java.io.Closeable
import java.io.Writer
import java.util.Locale

/**
 * Grava, por sessão, o que o módulo de peso viu e decidiu em cada frame, para analisar
 * depois os momentos de erro de marcação (falsos ou não). Só é ligado no build debug.
 *
 * Formato (CSV, uma linha por evento):
 *   tipo,t_ms,ratio_bruto,ratio_suave,zdiff,estado,transicao,erro,na_pausa,beat_idx
 *   F = frame de pose;  B = batida do metrônomo (t_ms é o instante AUDÍVEL; beat_idx o tempo).
 * `t_ms` está na base de `SystemClock.uptimeMillis`, a mesma para frames e batidas, então
 * dá para alinhar um erro com a posição no compasso.
 *
 * Cada erro também sai no Logcat com a tag [TAG], com a razão dos instantes que o
 * antecederam, que é o contexto que interessa para saber se foi oscilação ou troca real.
 */
class MarkingTraceLogger(private val out: Writer) : Closeable {

    companion object {
        const val TAG = "Marcacao"
        const val HEADER = "tipo,t_ms,ratio_bruto,ratio_suave,zdiff,estado,transicao,erro,na_pausa,beat_idx"
        private const val FLUSH_EVERY = 60
        private const val CONTEXT_FRAMES = 15
    }

    private var sinceFlush = 0
    private val recent = ArrayDeque<WeightTrace>()
    private var closed = false

    init {
        out.write(HEADER)
        out.write("\n")
    }

    fun onFrame(trace: WeightTrace) {
        if (closed) return
        out.write(frameLine(trace))
        out.write("\n")
        if (++sinceFlush >= FLUSH_EVERY) { out.flush(); sinceFlush = 0 }

        recent.addLast(trace)
        if (recent.size > CONTEXT_FRAMES) recent.removeFirst()
        if (trace.isError) Log.w(TAG, errorSummary(trace, recent.toList()))
    }

    fun onBeat(beatIndex: Int, audibleAtMs: Long) {
        if (closed) return
        out.write("B,$audibleAtMs,,,,,,,,$beatIndex\n")
    }

    override fun close() {
        if (closed) return
        closed = true
        runCatching { out.flush(); out.close() }
    }

    internal fun frameLine(t: WeightTrace): String =
        "F,${t.atMs},${num(t.rawRatio)},${num(t.smoothRatio)},${num(t.zDiff)}," +
            "${t.state},${t.transition ?: ""},${if (t.isError) 1 else 0},${if (t.inPause) 1 else 0},"

    internal fun errorSummary(error: WeightTrace, before: List<WeightTrace>): String {
        val ratios = before.joinToString(" ") { num(it.smoothRatio) }
        return "ERRO em t=${error.atMs} perna=${error.transition} pausa=${error.inPause} " +
            "ratio_suave(ultimos ${before.size} frames)=[$ratios]"
    }

    private fun num(v: Float?): String = if (v == null) "" else String.format(Locale.US, "%.4f", v)
}
