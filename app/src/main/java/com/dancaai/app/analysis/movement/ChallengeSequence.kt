package com.dancaai.app.analysis.movement

/**
 * Máquina de estados do modo Desafio: uma sequência de passos a executar em
 * ordem, com veredito a cada acerto ou erro.
 *
 * A classe não conhece modelo, câmera nem tela: recebe o passo que o
 * classificador decidiu e responde o que mudou. Isso a mantém testável sem
 * dispositivo, como o `StepCounter` e o `PostureValidator`.
 *
 * O problema que ela resolve não é comparar dois rótulos
 * ────────────────────────────────────────────────────
 * O classificador emite um veredito a cada 0,5 s, e o usuário leva de 2,5 a
 * 4,0 s para completar um passo. Então o passo esperado aparece repetido em
 * várias decisões seguidas, e o modo não pode tratar cada uma como uma tentativa
 * nova — senão um único passo correto avançaria a sequência inteira.
 *
 * Daí a regra: a sequência só avança quando o passo decidido MUDA em relação ao
 * que já foi contabilizado. Enquanto o usuário continua no mesmo passo, não há
 * tentativa nova.
 */
class ChallengeSequence(val steps: List<DanceStep>) {

    init {
        require(steps.isNotEmpty()) { "a sequência do desafio não pode ser vazia" }
    }

    /** Índice do passo esperado agora; igual a `steps.size` quando terminou. */
    var position = 0
        private set

    var hits = 0
        private set

    var misses = 0
        private set

    /** Último passo já contabilizado, para não contar a mesma execução duas vezes. */
    private var lastCounted: DanceStep? = null

    val isComplete: Boolean get() = position >= steps.size

    /** O passo que o usuário deve executar agora, ou `null` se a sequência acabou. */
    val expected: DanceStep? get() = steps.getOrNull(position)

    /** O que aconteceu na última decisão entregue a [onDecision]. */
    sealed interface Outcome {
        /** Nada mudou: mesma execução de antes, ou sequência já encerrada. */
        data object Waiting : Outcome
        data class Hit(val step: DanceStep, val position: Int) : Outcome
        data class Miss(val expected: DanceStep, val actual: DanceStep) : Outcome
        data class Finished(val hits: Int, val misses: Int) : Outcome
    }

    /**
     * Entrega ao desafio o passo que o voto majoritário decidiu.
     *
     * Passar [VoteResult.Undecided] é o caso normal durante a transição entre
     * passos, e não conta como erro: o usuário não errou, o modelo ainda não
     * decidiu. Tratar indecisão como erro puniria a transição, que acontece
     * entre todo par de passos da sequência.
     */
    fun onDecision(decided: DanceStep?): Outcome {
        if (isComplete) return Outcome.Waiting
        if (decided == null) {
            // Soltar a trava aqui é o que permite repetir o MESMO passo duas
            // vezes seguidas numa sequência: o usuário para, o voto fica
            // indeciso, e a próxima execução conta como tentativa nova.
            lastCounted = null
            return Outcome.Waiting
        }
        if (decided == lastCounted) return Outcome.Waiting

        lastCounted = decided
        val target = steps[position]
        return if (decided == target) {
            hits++
            position++
            if (isComplete) Outcome.Finished(hits, misses) else Outcome.Hit(decided, position - 1)
        } else {
            // Erro não avança a sequência: o usuário continua devendo o passo
            // esperado. Avançar transformaria um erro em "passou do ponto" e o
            // resto do desafio ficaria desalinhado.
            misses++
            Outcome.Miss(expected = target, actual = decided)
        }
    }

    fun reset() {
        position = 0
        hits = 0
        misses = 0
        lastCounted = null
    }

    companion object {
        /**
         * Sequência padrão do modo Desafio.
         *
         * Começa pelos passos que o classificador separa com folga (balanço, f1
         * 0,95; giro invertido, 0,99) e só então cobra o par difícil (abertura
         * 0,77 e giro em linha 0,86). Assim um erro no começo é erro do usuário,
         * não do modelo.
         */
        val DEFAULT = listOf(
            DanceStep.BALANCO,
            DanceStep.BASE_1,
            DanceStep.BASE_2,
            DanceStep.GIRO_INVERTIDO,
            DanceStep.ABERTURA,
            DanceStep.GIRO_LINHA,
        )
    }
}
