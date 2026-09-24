package com.dancaai.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dancaai.app.data.SessionRepository
import com.dancaai.app.data.local.UserSettings
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Preferências do usuário no nível da aplicação. Decide se o app abre no
 * onboarding ou direto na Home, e registra a conclusão do onboarding.
 */
class RootViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = SessionRepository(application)

    /** Nulo enquanto o DataStore não respondeu — a navegação só é montada depois disso. */
    val settings: StateFlow<UserSettings?> = repository.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun completeOnboarding(name: String, levelId: String) {
        viewModelScope.launch { repository.completeOnboarding(name, levelId) }
    }
}
