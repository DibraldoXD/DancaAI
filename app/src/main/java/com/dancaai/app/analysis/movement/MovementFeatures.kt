package com.dancaai.app.analysis.movement

import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Monta o vetor de 182 floats que o modelo TFLite espera, na ordem exata.
 *
 * Este arquivo é a tradução literal de `_ordenado()` e `_estatisticas()` em
 * `training/movement_classifier/features.py`. A ordem **não é convenção**: o
 * modelo recebe floats sem nome, então uma permutação aqui não dá erro em lugar
 * nenhum — o modelo responde normalmente, com valores errados. É por isso que
 * existe o teste de vetor-ouro (`MovementFeaturesTest`), que compara estes
 * números com os que o Python produz para a mesma janela.
 *
 * Layout (estratégia `C` do módulo de treino):
 *
 *   5 sub-intervalos × 13 sinais × {média, amplitude}   = 130
 *   13 sinais × {média, desvio, amplitude, velocidade}  =  52
 *                                                        ---
 *                                                         182
 *
 * Os 5 sub-intervalos em ORDEM são o que distingue abertura de giro em linha:
 * as 3 primeiras pisadas dos dois passos são praticamente iguais, e o que separa
 * é a rotação continuar depois da 3ª. Uma média da janela inteira apaga
 * exatamente isso.
 */
object MovementFeatures {

    /** Sub-intervalos da janela; 5 espelha os 5 tempos do giro em linha. */
    const val SUBINTERVALS = 5

    const val SIZE = SUBINTERVALS * FrameSignals.COUNT * 2 + FrameSignals.COUNT * 4

    /**
     * Nomes na mesma ordem de [vectorOf], para conferir contra a lista que o
     * `export_tflite.py` imprime.
     */
    val NAMES: List<String> = buildList {
        for (i in 0 until SUBINTERVALS) {
            for (name in FrameSignals.NAMES) {
                add("${name}_s${i}_media")
                add("${name}_s${i}_amplitude")
            }
        }
        for (name in FrameSignals.NAMES) {
            add("${name}_media")
            add("${name}_desvio")
            add("${name}_amplitude")
            add("${name}_velocidade")
        }
    }

    /**
     * Converte uma janela de sinais no vetor de entrada do modelo.
     *
     * A janela tem de vir ordenada no tempo, como [MovementWindow] entrega.
     */
    fun vectorOf(window: List<FrameSignals>): FloatArray {
        require(window.size >= 2) { "janela precisa de ao menos 2 amostras, veio ${window.size}" }

        val times = FloatArray(window.size) { window[it].timeMs / 1000f }
        // Transpõe percorrendo as amostras UMA vez: `asList()` aloca um array por
        // chamada, e chamá-lo dentro do laço das colunas custaria 13x mais
        // alocações por janela, duas vezes por segundo, durante toda a sessão.
        val columns = Array(FrameSignals.COUNT) { FloatArray(window.size) }
        for (row in window.indices) {
            val values = window[row].asList()
            for (column in 0 until FrameSignals.COUNT) columns[column][row] = values[column]
        }

        val out = FloatArray(SIZE)
        var at = 0

        // ── 5 sub-intervalos, em ordem ──
        //
        // As bordas vêm de um linspace sobre o intervalo da janela, e a
        // pertinência usa `>=` no início E `<=` no fim, como no Python. Isso faz
        // a amostra que cai exatamente numa borda contar nos DOIS
        // sub-intervalos vizinhos — comportamento do `features.py`, replicado de
        // propósito: "corrigir" aqui desalinharia do treino.
        val first = times.first()
        val last = times.last()
        val edges = FloatArray(SUBINTERVALS + 1) { i ->
            first + (last + 1e-9f - first) * i / SUBINTERVALS
        }
        for (i in 0 until SUBINTERVALS) {
            for (column in columns) {
                val slice = ArrayList<Float>(window.size)
                for (row in times.indices) {
                    if (times[row] >= edges[i] && times[row] <= edges[i + 1]) {
                        val value = column[row]
                        if (value.isFinite()) slice.add(value)
                    }
                }
                if (slice.isEmpty()) {
                    out[at++] = 0f   // média
                    out[at++] = 0f   // amplitude
                } else {
                    out[at++] = slice.average().toFloat()
                    out[at++] = (slice.max() - slice.min())
                }
            }
        }

        // ── estatísticas da janela inteira ──
        val durationS = maxOf(last - first, 1e-3f)
        for (column in columns) {
            val values = column.filter { it.isFinite() }
            if (values.isEmpty()) {
                repeat(4) { out[at++] = 0f }
                continue
            }
            val mean = values.average().toFloat()
            out[at++] = mean
            // Desvio populacional (ddof = 0), que é o padrão do numpy `std()`.
            // O desvio amostral do Kotlin daria outro número e desalinharia do
            // treino de forma invisível.
            out[at++] = sqrt(values.sumOf { v -> ((v - mean) * (v - mean)).toDouble() } / values.size).toFloat()
            out[at++] = values.max() - values.min()
            // "Velocidade" aqui é variação total por segundo, não a derivada
            // instantânea: soma dos |Δ| dividida pela duração da janela.
            var totalVariation = 0f
            for (row in 1 until values.size) totalVariation += abs(values[row] - values[row - 1])
            out[at++] = totalVariation / durationS
        }

        check(at == SIZE) { "montou $at features, esperava $SIZE" }
        return out
    }
}
