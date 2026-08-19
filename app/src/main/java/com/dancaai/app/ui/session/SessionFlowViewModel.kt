package com.dancaai.app.ui.session

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dancaai.app.audio.MetronomeBpmStore
import com.dancaai.app.data.SessionRepository
import com.dancaai.app.data.model.SessionConfig
import com.dancaai.app.data.model.SessionMetrics
import kotlinx.coroutines.launch

/**
 * Estado que atravessa o fluxo Nova sessão → Treino → Resultado.
 *
 * É escopado ao subgrafo de navegação da sessão: a configuração escolhida
 * sobrevive à troca de telas dentro do fluxo e é descartada quando o usuário sai
 * dele. Antes disso cada tela guardava o próprio estado em `remember`, e nada
 * atravessava a navegação.
 */
class SessionFlowViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = SessionRepository(application)
    private val bpmStore = MetronomeBpmStore(application)

    /** Abre já no último BPM usado, para o usuário não reconfigurar o metrônomo a cada treino. */
    var config by mutableStateOf(SessionConfig(bpm = bpmStore.load()))
        private set

    /** Id da sessão gravada ao encerrar o treino. Alimenta a tela de Resultado. */
    var savedSessionId by mutableStateOf<Long?>(null)
        private set

    private var startedAtEpochMs = 0L

    fun updateConfig(update: (SessionConfig) -> SessionConfig) {
        config = update(config)
        bpmStore.save(config.bpm)
    }

    fun startSession() {
        startedAtEpochMs = System.currentTimeMillis()
        savedSessionId = null
    }

    /**
     * Grava a sessão encerrada com as medições brutas coletadas durante o treino.
     * A pontuação é derivada delas na leitura, quando a fórmula existir.
     */
    fun finishSession(elapsedSec: Int, metrics: SessionMetrics = SessionMetrics()) {
        val startedAt = startedAtEpochMs.takeIf { it > 0L } ?: System.currentTimeMillis()
        viewModelScope.launch {
            savedSessionId = repository.saveSession(
                config = config,
                startedAtEpochMs = startedAt,
                actualDurationSec = elapsedSec,
                metrics = metrics,
            )
        }
    }
}
