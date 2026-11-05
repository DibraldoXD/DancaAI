package com.dancaai.app.ui.training

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dancaai.app.data.SessionRepository
import com.dancaai.app.data.model.ParticipantId
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Estado do aparato de coleta que não pode viver em `remember {}`.
 *
 * Hoje guarda só o participante corrente, que precisa sobreviver à recomposição
 * e ao fechamento do app: perder a contagem no meio de uma coleta significa
 * gravar duas pessoas com o mesmo código, que é o erro que o identificador
 * existe para evitar. É também onde o restante do painel de coleta vai aterrar
 * quando o "modo coleta" sair do `TrainingScreen` (ver CLAUDE.md).
 */
class TrainingViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = SessionRepository(application)

    /** `null` enquanto a preferência não chegou e quando nenhum está armado. */
    val participant: StateFlow<ParticipantId?> = repository.settings
        .map { ParticipantId.ofPersisted(it.collectParticipant) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Próxima pessoa da fila — o caminho normal entre participantes. */
    fun nextParticipant() {
        val atual = participant.value
        setParticipant(atual?.next() ?: ParticipantId(ParticipantId.FIRST))
    }

    /** Corrige um avanço acidental; no primeiro participante, não faz nada. */
    fun previousParticipant() {
        val anterior = participant.value?.previous() ?: return
        setParticipant(anterior)
    }

    private fun setParticipant(id: ParticipantId) {
        viewModelScope.launch { repository.setCollectParticipant(id.number) }
    }
}
