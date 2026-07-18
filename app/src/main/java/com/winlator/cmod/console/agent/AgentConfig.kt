package com.winlator.cmod.console.agent

import android.content.Context
import androidx.preference.PreferenceManager

/**
 * OpenRouter / OpenAI-compatible API configuration stored in default SharedPreferences.
 */
data class AgentConfig(
    val enabled: Boolean = true,
    val baseUrl: String = DEFAULT_BASE_URL,
    val apiKey: String = "",
    val model: String = DEFAULT_MODEL,
) {
    val isReady: Boolean
        get() = enabled && apiKey.isNotBlank() && model.isNotBlank() && baseUrl.isNotBlank()

    /** Normalized base without trailing slash, e.g. https://openrouter.ai/api/v1 */
    val normalizedBaseUrl: String
        get() = baseUrl.trim().trimEnd('/')

    companion object {
        const val PREF_ENABLED = "ai_enabled"
        const val PREF_BASE_URL = "ai_api_base_url"
        const val PREF_API_KEY = "ai_api_key"
        const val PREF_MODEL = "ai_model"

        const val DEFAULT_BASE_URL = "https://openrouter.ai/api/v1"
        const val DEFAULT_MODEL = "openai/gpt-4.1-mini"

        @JvmStatic
        fun load(context: Context): AgentConfig {
            val prefs = PreferenceManager.getDefaultSharedPreferences(context)
            return AgentConfig(
                enabled = prefs.getBoolean(PREF_ENABLED, true),
                baseUrl = prefs.getString(PREF_BASE_URL, DEFAULT_BASE_URL) ?: DEFAULT_BASE_URL,
                apiKey = prefs.getString(PREF_API_KEY, "") ?: "",
                model = prefs.getString(PREF_MODEL, DEFAULT_MODEL) ?: DEFAULT_MODEL,
            )
        }

        @JvmStatic
        fun save(context: Context, config: AgentConfig) {
            PreferenceManager.getDefaultSharedPreferences(context).edit()
                .putBoolean(PREF_ENABLED, config.enabled)
                .putString(PREF_BASE_URL, config.baseUrl.trim())
                .putString(PREF_API_KEY, config.apiKey.trim())
                .putString(PREF_MODEL, config.model.trim())
                .apply()
        }
    }
}
