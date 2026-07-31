package com.example.nexuswallet.feature.settings.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.nexuswallet.feature.settings.domain.model.ThemeMode
import com.example.nexuswallet.feature.settings.domain.repository.SecurityRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val securityRepository: SecurityRepository
) : ViewModel() {

    val selectedCurrency: StateFlow<String> = securityRepository.observeSelectedCurrency()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = "USD"
        )

    val themeMode: StateFlow<ThemeMode> = securityRepository.observeThemeMode()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = ThemeMode.SYSTEM
        )

    fun setSelectedCurrency(currencyCode: String) {
        viewModelScope.launch {
            securityRepository.setSelectedCurrency(currencyCode)
        }
    }

    fun setThemeMode(themeMode: ThemeMode) {
        viewModelScope.launch {
            securityRepository.setThemeMode(themeMode)
        }
    }
}
