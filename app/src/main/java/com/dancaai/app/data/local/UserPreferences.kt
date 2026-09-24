package com.dancaai.app.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.preferencesDataStore: DataStore<Preferences> by preferencesDataStore(name = "user_prefs")

/** Preferências e identificação do usuário, escolhidas no onboarding e no perfil. */
data class UserSettings(
    val name: String = "",
    val levelId: String = DEFAULT_LEVEL_ID,
    val onboardingDone: Boolean = false,
    val vibrationEnabled: Boolean = true,
    val voiceFeedbackEnabled: Boolean = false,
) {
    val firstName: String get() = name.trim().substringBefore(' ')

    val initial: String get() = firstName.take(1).uppercase()

    companion object {
        const val DEFAULT_LEVEL_ID = "intermediario"
    }
}

class UserPreferences(context: Context) {

    private val store = context.applicationContext.preferencesDataStore

    val settings: Flow<UserSettings> = store.data.map { prefs ->
        UserSettings(
            name = prefs[KEY_NAME].orEmpty(),
            levelId = prefs[KEY_LEVEL] ?: UserSettings.DEFAULT_LEVEL_ID,
            onboardingDone = prefs[KEY_ONBOARDING_DONE] ?: false,
            vibrationEnabled = prefs[KEY_VIBRATION] ?: true,
            voiceFeedbackEnabled = prefs[KEY_VOICE] ?: false,
        )
    }

    suspend fun completeOnboarding(name: String, levelId: String) {
        store.edit { prefs ->
            prefs[KEY_NAME] = name.trim()
            prefs[KEY_LEVEL] = levelId
            prefs[KEY_ONBOARDING_DONE] = true
        }
    }

    suspend fun setVibrationEnabled(enabled: Boolean) {
        store.edit { it[KEY_VIBRATION] = enabled }
    }

    suspend fun setVoiceFeedbackEnabled(enabled: Boolean) {
        store.edit { it[KEY_VOICE] = enabled }
    }

    private companion object {
        val KEY_NAME = stringPreferencesKey("name")
        val KEY_LEVEL = stringPreferencesKey("level")
        val KEY_ONBOARDING_DONE = booleanPreferencesKey("onboarding_done")
        val KEY_VIBRATION = booleanPreferencesKey("vibration")
        val KEY_VOICE = booleanPreferencesKey("voice_feedback")
    }
}
