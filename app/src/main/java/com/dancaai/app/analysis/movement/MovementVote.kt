package com.dancaai.app.analysis.movement

import com.dancaai.app.data.model.CaptureSubject

/**
 * Os 6 passos que o classificador distingue, na ordem das saídas do modelo.
 *
 * A ordem vem de `PASSOS` em `training/movement_classifier/load_data.py` e é a
 * que o `export_tflite.py` imprime. O índice de cada passo aqui é o índice da
 * saída softmax correspondente — trocar a ordem troca os rótulos sem gerar erro.
 */
enum class DanceStep(val token: String, val display: String) {
    BALANCO("mov_balanco", "Balanço"),
    BASE_1("mov_base1", "Base 1"),
    BASE_2("mov_base2", "Base 2"),
    ABERTURA("mov_abertura", "Abertura"),
    GIRO_INVERTIDO("mov_giro_invertido", "Giro invertido"),
    GIRO_LINHA("mov_giro_linha", "Giro em linha");

    companion object {
        /**
         * Confere que esta ordem continua casando com os tokens de coleta.
         *
         * [CaptureSubject] é a fonte dos rótulos que treinaram o modelo; se
         * alguém renomear um token lá, o modelo passa a prever uma classe que
         * não existe mais e nada acusa. Chamado pelo teste unitário.
         */
        fun matchesCaptureTokens(): Boolean {
            val fromCapture = CaptureSubject.entries
                .filter { it.token.startsWith("mov_") }
                .map { it.token }
            return fromCapture == entries.map { it.token }
        }
    }
}

/** Uma classificação de janela: o passo mais provável e a distribuição completa. */
data class StepPrediction(
    val step: DanceStep,
    val confidence: Float,
    val probabilities: FloatArray,
) {
    override fun equals(other: Any?): Boolean =
        other is StepPrediction && step == other.step && confidence == other.confidence
    override fun hashCode(): Int = 31 * step.hashCode() + confidence.hashCode()
}

/** O que o app mostra depois de agregar as janelas recentes. */
sealed interface VoteResult {
    /** Ainda sem janelas suficientes, ou sem acordo que passe do mínimo. */
    data object Undecided : VoteResult

    data class Decided(
        val step: DanceStep,
        /** Fração das janelas recentes que votaram neste passo. */
        val agreement: Float,
        /** Confiança média do modelo nas janelas que votaram nele. */
        val confidence: Float,
    ) : VoteResult
}

/**
 * Agrega as classificações de janela por voto majoritário.
 *
 * Por que não decidir por uma janela só
 * ────────────────────────────────────
 * Por janela o modelo acerta 0,891 fora da amostra; por voto majoritário entre
 * as janelas de uma execução, 0,907. Mais importante que o ganho: com uma janela
 * a cada 0,5 s, decidir por janela faria o rótulo na tela piscar a cada
 * transição entre passos. O voto é o que corresponde à pergunta do usuário —
 * "que passo eu estou fazendo" — e não a "que passo estava nos últimos 3
 * segundos de um instante específico".
 *
 * [minimumAgreement] existe para a tela poder ficar calada. Durante a transição
 * de um passo para outro as janelas discordam de verdade, e mostrar o vencedor
 * de um empate de 2 contra 2 seria inventar certeza que o modelo não tem.
 */
class MovementVote(
    private val windowCount: Int = DEFAULT_WINDOW_COUNT,
    private val minimumAgreement: Float = DEFAULT_MINIMUM_AGREEMENT,
    private val minimumConfidence: Float = DEFAULT_MINIMUM_CONFIDENCE,
) {
    private val recent = ArrayDeque<StepPrediction>()

    fun reset() = recent.clear()

    /** Quantas janelas já entraram, para a tela mostrar o aquecimento. */
    val size: Int get() = recent.size

    fun add(prediction: StepPrediction): VoteResult {
        recent.addLast(prediction)
        while (recent.size > windowCount) recent.removeFirst()
        return current()
    }

    fun current(): VoteResult {
        if (recent.isEmpty()) return VoteResult.Undecided

        // Só janelas em que o modelo tem alguma convicção entram no voto: uma
        // janela de confiança 0,2 é o modelo dizendo "não sei", e contá-la como
        // voto transforma indecisão em opinião.
        val eligible = recent.filter { it.confidence >= minimumConfidence }
        if (eligible.isEmpty()) return VoteResult.Undecided

        val byStep = eligible.groupBy { it.step }
        val winner = byStep.maxByOrNull { (_, list) -> list.size } ?: return VoteResult.Undecided
        val agreement = winner.value.size.toFloat() / recent.size
        if (agreement < minimumAgreement) return VoteResult.Undecided

        return VoteResult.Decided(
            step = winner.key,
            agreement = agreement,
            confidence = winner.value.map { it.confidence }.average().toFloat(),
        )
    }

    companion object {
        /**
         * 6 janelas com passo de 0,5 s cobrem ~3 s de histórico além da própria
         * janela — tempo de um ciclo de passo, que na coleta mediu 2,5 a 4,0 s.
         */
        const val DEFAULT_WINDOW_COUNT = 6

        /** Maioria simples das janelas da memória. */
        const val DEFAULT_MINIMUM_AGREEMENT = 0.5f

        /**
         * Abaixo disto o modelo não escolheu, empatou. Ponto de partida a
         * calibrar com praticantes, como o limiar de postura.
         */
        const val DEFAULT_MINIMUM_CONFIDENCE = 0.4f
    }
}
