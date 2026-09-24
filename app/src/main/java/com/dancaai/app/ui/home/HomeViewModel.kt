package com.dancaai.app.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dancaai.app.data.SessionRepository
import com.dancaai.app.data.model.HomeSummary
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = SessionRepository(application)

    /** Nulo enquanto a primeira leitura do banco não chegou. */
    val summary: StateFlow<HomeSummary?> = repository.homeSummary
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}
