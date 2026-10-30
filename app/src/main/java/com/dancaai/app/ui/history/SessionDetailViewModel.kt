package com.dancaai.app.ui.history

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.dancaai.app.data.SessionRepository
import com.dancaai.app.data.model.SessionOutcome
import com.dancaai.app.navigation.Routes
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SessionDetailUiState(
    val outcome: SessionOutcome? = null,
    val loading: Boolean = true,
)

/**
 * Uma sessão do histórico, com a anterior ao lado para a comparação — o mesmo
 * [SessionOutcome] que a tela de Resultado recebe ao encerrar o treino, e por
 * isso as duas telas exibem os mesmos cartões.
 */
class SessionDetailViewModel(
    application: Application,
    savedStateHandle: SavedStateHandle,
) : AndroidViewModel(application) {

    private val repository = SessionRepository(application)
    private val sessionId: Long = checkNotNull(savedStateHandle[Routes.SESSION_ID_ARG]) {
        "Rota de detalhe de sessão sem ${Routes.SESSION_ID_ARG}"
    }

    private val _state = MutableStateFlow(SessionDetailUiState())

    /** `loading` distingue "ainda buscando" de "sessão não existe mais". */
    val state: StateFlow<SessionDetailUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            _state.value = SessionDetailUiState(
                outcome = repository.findOutcome(sessionId),
                loading = false,
            )
        }
    }
}
