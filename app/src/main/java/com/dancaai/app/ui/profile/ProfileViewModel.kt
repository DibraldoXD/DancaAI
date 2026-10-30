package com.dancaai.app.ui.profile

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dancaai.app.data.SessionRepository
import com.dancaai.app.data.model.ProfileSummary
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ProfileViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = SessionRepository(application)

    /** Nulo enquanto a primeira leitura do banco e das preferências não chegou. */
    val profile: StateFlow<ProfileSummary?> = repository.profile
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun setVibrationEnabled(enabled: Boolean) {
        viewModelScope.launch { repository.setVibrationEnabled(enabled) }
    }

    fun setVoiceFeedbackEnabled(enabled: Boolean) {
        viewModelScope.launch { repository.setVoiceFeedbackEnabled(enabled) }
    }

    /**
     * Apaga todas as sessões gravadas. A tela confirma antes de chamar: é a única
     * ação destrutiva e irreversível do app.
     */
    fun clearHistory() {
        viewModelScope.launch { repository.clearHistory() }
    }
}
