package com.example.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.model.PrismApp
import com.example.data.prefs.PrismAppearanceState
import com.example.data.prefs.PrismPreferences
import com.example.ui.theme.ThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class AppShellViewModel(
    private val prefs: PrismPreferences,
) : ViewModel() {

    private val _state = MutableStateFlow(PrismAppearanceState())
    val state: StateFlow<PrismAppearanceState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            prefs.appearanceFlow.collect { saved ->
                _state.value = saved
            }
        }
    }

    fun selectApp(app: PrismApp) {
        _state.update { it.copy(activeApp = app) }
        viewModelScope.launch { prefs.setActiveApp(app) }
    }

    fun selectThemeMode(mode: ThemeMode) {
        _state.update { it.copy(themeMode = mode) }
        viewModelScope.launch { prefs.setThemeMode(mode) }
    }

    fun previewGlassClarity(clarity: Float) {
        _state.update { it.copy(glassClarity = clarity.coerceIn(0f, 1f)) }
    }

    fun commitGlassClarity() {
        val current = _state.value.glassClarity
        viewModelScope.launch { prefs.setGlassClarity(current) }
    }

    fun updateNotesLockPin(pin: String) {
        if (pin.length in 4..8) {
            _state.update { it.copy(notesLockPin = pin) }
            viewModelScope.launch { prefs.setNotesLockPin(pin) }
        }
    }

    class Factory(private val prefs: PrismPreferences) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = AppShellViewModel(prefs) as T
    }
}
