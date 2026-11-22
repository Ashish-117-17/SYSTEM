package com.example.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.data.model.PrismApp
import com.example.ui.theme.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.prismDataStore by preferencesDataStore(name = "prism_appearance_prefs")

data class PrismAppearanceState(
    val activeApp: PrismApp = PrismApp.MUSIC,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val glassClarity: Float = 0.45f,
    val notesLockPin: String = "1234",
)

class PrismPreferences(private val context: Context) {
    private val keyActiveApp = stringPreferencesKey("active_app")
    private val keyThemeMode = stringPreferencesKey("theme_mode")
    private val keyGlassClarity = floatPreferencesKey("glass_clarity")
    private val keyNotesPin = stringPreferencesKey("notes_lock_pin")

    val appearanceFlow: Flow<PrismAppearanceState> = context.prismDataStore.data.map { prefs ->
        val app = PrismApp.entries.firstOrNull { it.name == prefs[keyActiveApp] } ?: PrismApp.MUSIC
        val theme = ThemeMode.entries.firstOrNull { it.name == prefs[keyThemeMode] } ?: ThemeMode.SYSTEM
        val clarity = (prefs[keyGlassClarity] ?: 0.45f).coerceIn(0f, 1f)
        val pin = prefs[keyNotesPin] ?: "1234"
        PrismAppearanceState(
            activeApp = app,
            themeMode = theme,
            glassClarity = clarity,
            notesLockPin = pin,
        )
    }

    suspend fun setActiveApp(app: PrismApp) {
        context.prismDataStore.edit { it[keyActiveApp] = app.name }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.prismDataStore.edit { it[keyThemeMode] = mode.name }
    }

    suspend fun setGlassClarity(clarity: Float) {
        context.prismDataStore.edit { it[keyGlassClarity] = clarity.coerceIn(0f, 1f) }
    }

    suspend fun setNotesLockPin(pin: String) {
        if (pin.length in 4..8) {
            context.prismDataStore.edit { it[keyNotesPin] = pin }
        }
    }
}
