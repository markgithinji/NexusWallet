package com.example.nexuswallet.feature.settings.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.nexuswallet.feature.authentication.domain.repository.SecurityPreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val securityPreferencesRepository: SecurityPreferencesRepository
) : ViewModel() {

    val selectedCurrency: StateFlow<String> = securityPreferencesRepository.observeSelectedCurrency()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = "USD"
        )

    fun setSelectedCurrency(currencyCode: String) {
        viewModelScope.launch {
            securityPreferencesRepository.setSelectedCurrency(currencyCode)
        }
    }
}
