package com.example.nexuswallet

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.nexuswallet.feature.settings.domain.model.ThemeMode
import com.example.nexuswallet.feature.settings.domain.repository.SettingsRepository
import com.example.nexuswallet.feature.settings.domain.usecase.IsBiometricEnabledUseCase
import com.example.nexuswallet.feature.settings.domain.usecase.IsPinSetUseCase
import com.example.nexuswallet.feature.wallet.domain.model.Wallet
import com.example.nexuswallet.feature.wallet.domain.repository.WalletRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val walletRepository: WalletRepository,
    private val isPinSetUseCase: IsPinSetUseCase,
    private val isBiometricEnabledUseCase: IsBiometricEnabledUseCase
) : ViewModel() {

    // Theme state
    val themeMode: StateFlow<ThemeMode> = settingsRepository.observeThemeMode()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = ThemeMode.SYSTEM
        )

    // Wallet state
    private val _wallets = MutableStateFlow<List<Wallet>>(emptyList())
    val wallets: StateFlow<List<Wallet>> = _wallets.asStateFlow()

    private val _isWalletsLoading = MutableStateFlow(true)
    val isWalletsLoading: StateFlow<Boolean> = _isWalletsLoading.asStateFlow()

    // Security state
    val isAuthenticationRequired: StateFlow<Boolean> = combine(
        isPinSetUseCase(),
        isBiometricEnabledUseCase()
    ) { isPinSet, isBiometricEnabled ->
        isPinSet || isBiometricEnabled
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = false
    )

    val isPrivacyModeEnabled: StateFlow<Boolean> = settingsRepository.observePrivacyModeEnabled()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = false
        )

    val isRequireAuthForSendEnabled: StateFlow<Boolean> = settingsRepository.observeRequireAuthForSend()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = false
        )

    init {
        observeWallets()
    }

    private fun observeWallets() {
        viewModelScope.launch {
            walletRepository.observeWallets()
                .catch { _isWalletsLoading.value = false }
                .collect { walletsList ->
                    _wallets.value = walletsList
                    _isWalletsLoading.value = false
                }
        }
    }
}
