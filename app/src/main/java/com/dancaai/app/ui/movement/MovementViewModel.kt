package com.dancaai.app.ui.movement

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.dancaai.app.analysis.movement.ChallengeSequence
import com.dancaai.app.analysis.movement.DanceStep
import com.dancaai.app.analysis.movement.FrameSignals
import com.dancaai.app.analysis.movement.MovementClassifier
import com.dancaai.app.analysis.movement.MovementVote
import com.dancaai.app.analysis.movement.MovementWindow
import com.dancaai.app.analysis.movement.PoseSample
import com.dancaai.app.analysis.movement.VoteResult
import com.dancaai.app.analysis.movement.signalsOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Os dois modos previstos no plano de trabalho para execução de movimentos. */
enum class MovementMode { LIVRE, DESAFIO }

/**
 * O que a tela mostra. Imutável e completo: a UI só lê, nunca calcula.
 */
data class MovementUiState(
    val mode: MovementMode = MovementMode.LIVRE,
    /**
     * `false` quando o `.tflite` não está nos assets. A tela usa isto para
     * explicar a ausência em vez de ficar eternamente "aguardando".
     */
    val modelAvailable: Boolean = false,
    /** Passo decidido pelo voto majoritário, ou `null` enquanto não há acordo. */
    val detectedStep: DanceStep? = null,
    val confidence: Float = 0f,
    val agreement: Float = 0f,
    /** Janelas já acumuladas, para a tela mostrar o aquecimento do buffer. */
    val windowsSeen: Int = 0,
    // ── modo Desafio ──
    val expectedStep: DanceStep? = null,
    val sequence: List<DanceStep> = emptyList(),
    val sequencePosition: Int = 0,
    val hits: Int = 0,
    val misses: Int = 0,
    val challengeComplete: Boolean = false,
    /** Último veredito, para a tela piscar acerto ou erro. Consumido por [consumeFeedback]. */
    val lastOutcome: Outcome? = null,
) {
    enum class Outcome { HIT, MISS }
}

/**
 * Liga a câmera ao classificador de passos e expõe o estado da tela.
 *
 * O ViewModel não conhece Compose nem MediaPipe: recebe [PoseSample] de quem
 * observa a câmera e devolve estado. A lógica de verdade mora nas classes puras
 * (`MovementWindow`, `MovementVote`, `ChallengeSequence`), que têm teste
 * unitário; aqui só se faz a costura.
 *
 * Por que o classificador pode não existir
 * ────────────────────────────────────────
 * O `.tflite` é treinado sobre dados corporais de participantes e não vai para o
 * repositório público enquanto a publicação não estiver amparada pelo
 * consentimento e pelo CEP — ele vive no repositório privado de modelos. Quando
 * falta, [MovementUiState.modelAvailable] fica `false` e a tela diz isso, em vez
 * de a sessão quebrar.
 */
class MovementViewModel(application: Application) : AndroidViewModel(application) {

    private val classifier: MovementClassifier? = MovementClassifier.load(application)
    private val window = MovementWindow()
    private val vote = MovementVote()
    private var challenge = ChallengeSequence(ChallengeSequence.DEFAULT)

    private val _state = MutableStateFlow(
        MovementUiState(
            modelAvailable = classifier != null,
            sequence = ChallengeSequence.DEFAULT,
            expectedStep = ChallengeSequence.DEFAULT.first(),
        )
    )
    val state: StateFlow<MovementUiState> = _state.asStateFlow()

    fun setMode(mode: MovementMode) {
        if (_state.value.mode == mode) return
        // Trocar de modo zera a decisão: o histórico de janelas foi acumulado
        // sob outra pergunta, e aproveitá-lo faria o Desafio começar julgando um
        // passo que o usuário executou antes de entrar nele.
        vote.reset()
        window.reset()
        challenge.reset()
        _state.value = MovementUiState(
            mode = mode,
            modelAvailable = classifier != null,
            sequence = challenge.steps,
            expectedStep = challenge.expected,
        )
    }

    fun restartChallenge() {
        challenge.reset()
        vote.reset()
        _state.update {
            it.copy(
                hits = 0, misses = 0, sequencePosition = 0,
                challengeComplete = false, expectedStep = challenge.expected,
                lastOutcome = null,
            )
        }
    }

    /**
     * Recebe um quadro de pose. Chamado na thread de análise da câmera.
     *
     * Quadros sem pose no enquadramento não devem chegar aqui: quem observa a
     * câmera só passa adiante o que o MediaPipe resolveu. O buraco aparece como
     * salto no tempo, que é o que as features de velocidade usam.
     */
    fun onPose(sample: PoseSample) {
        val classifier = classifier ?: return
        val signals = signalsOf(sample) ?: return
        val completed: List<FrameSignals> = window.offer(signals) ?: return
        val prediction = classifier.classify(completed) ?: return

        when (val result = vote.add(prediction)) {
            VoteResult.Undecided -> publish(decided = null)
            is VoteResult.Decided -> publish(
                decided = result.step,
                confidence = result.confidence,
                agreement = result.agreement,
            )
        }
    }

    private fun publish(decided: DanceStep?, confidence: Float = 0f, agreement: Float = 0f) {
        val outcome = if (_state.value.mode == MovementMode.DESAFIO) {
            challenge.onDecision(decided)
        } else {
            ChallengeSequence.Outcome.Waiting
        }

        _state.update { current ->
            current.copy(
                detectedStep = decided,
                confidence = confidence,
                agreement = agreement,
                windowsSeen = vote.size,
                expectedStep = challenge.expected,
                sequencePosition = challenge.position,
                hits = challenge.hits,
                misses = challenge.misses,
                challengeComplete = challenge.isComplete,
                lastOutcome = when (outcome) {
                    is ChallengeSequence.Outcome.Hit -> MovementUiState.Outcome.HIT
                    is ChallengeSequence.Outcome.Finished -> MovementUiState.Outcome.HIT
                    is ChallengeSequence.Outcome.Miss -> MovementUiState.Outcome.MISS
                    ChallengeSequence.Outcome.Waiting -> current.lastOutcome
                },
            )
        }
    }

    /** A tela avisa que já mostrou o veredito, para não repetir a animação. */
    fun consumeFeedback() = _state.update { it.copy(lastOutcome = null) }

    override fun onCleared() {
        classifier?.close()
        super.onCleared()
    }
}
