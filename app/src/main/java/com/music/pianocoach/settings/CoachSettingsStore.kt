package com.music.pianocoach.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.coachDataStore: DataStore<Preferences> by preferencesDataStore(name = "coach_settings")

/** 出厂默认：DeepSeek；用户可在设置中改为 OpenAI / 中转等 OpenAI 兼容端点 */
object LlmApiDefaults {
    const val BASE_URL = "https://api.deepseek.com"
    const val MODEL = "deepseek-chat"
}

data class CoachSettings(
    val apiKey: String = "",
    val baseUrl: String = LlmApiDefaults.BASE_URL,
    val model: String = LlmApiDefaults.MODEL,
    val skillLevel: String = "业余初学",
)

class CoachSettingsStore(private val context: Context) {

    val settings: Flow<CoachSettings> = context.coachDataStore.data.map { prefs ->
        CoachSettings(
            apiKey = prefs[KEY_API] ?: "",
            baseUrl = prefs[KEY_BASE_URL] ?: LlmApiDefaults.BASE_URL,
            model = prefs[KEY_MODEL] ?: LlmApiDefaults.MODEL,
            skillLevel = prefs[KEY_SKILL] ?: "业余初学",
        )
    }

    suspend fun update(
        apiKey: String? = null,
        baseUrl: String? = null,
        model: String? = null,
        skillLevel: String? = null,
    ) {
        context.coachDataStore.edit { prefs ->
            apiKey?.let { prefs[KEY_API] = it.trim() }
            baseUrl?.let { prefs[KEY_BASE_URL] = it.trim() }
            model?.let { prefs[KEY_MODEL] = it.trim() }
            skillLevel?.let { prefs[KEY_SKILL] = it.trim() }
        }
    }

    companion object {
        private val KEY_API = stringPreferencesKey("api_key")
        private val KEY_BASE_URL = stringPreferencesKey("base_url")
        private val KEY_MODEL = stringPreferencesKey("model")
        private val KEY_SKILL = stringPreferencesKey("skill_level")
    }
}
