package dev.usrc.alarm

import android.content.Context
import android.content.SharedPreferences
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class SettingsRepository(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("alarm_settings", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    fun getSettings(): AppSettings {
        val jsonString = prefs.getString("settings", null)
        return if (jsonString != null) {
            try {
                json.decodeFromString(jsonString)
            } catch (e: Exception) {
                AppSettings()
            }
        } else {
            AppSettings()
        }
    }

    fun saveSettings(settings: AppSettings) {
        val jsonString = json.encodeToString(settings)
        prefs.edit().putString("settings", jsonString).apply()
    }

    fun updateSettings(transform: (AppSettings) -> AppSettings) {
        val current = getSettings()
        saveSettings(transform(current))
    }
}
