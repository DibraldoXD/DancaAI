package com.dancaai.app.ui.history

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dancaai.app.data.SessionRepository
import com.dancaai.app.data.model.HistorySummary
import com.dancaai.app.data.model.Session
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class HistoryUiState(
    val summary: HistorySummary? = null,
    val sessions: List<Session> = emptyList(),
    val loading: Boolean = true,
)

class HistoryViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = SessionRepository(application)

    val state: StateFlow<HistoryUiState> =
        combine(repository.historySummary, repository.history) { summary, sessions ->
            HistoryUiState(summary = summary, sessions = sessions, loading = false)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HistoryUiState())
}
