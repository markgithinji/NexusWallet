package com.example.nexuswallet

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.nexuswallet.feature.settings.domain.model.ThemeMode
import com.example.nexuswallet.feature.settings.domain.repository.SecurityRepository
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
    private val securityRepository: SecurityRepository,
    private val walletRepository: WalletRepository,
    private val isPinSetUseCase: IsPinSetUseCase,
    private val isBiometricEnabledUseCase: IsBiometricEnabledUseCase
) : ViewModel() {

    // Theme state
    val themeMode: StateFlow<ThemeMode> = securityRepository.observeThemeMode()
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
    private val _isAuthenticationRequired = MutableStateFlow(false)
    val isAuthenticationRequired: StateFlow<Boolean> = _isAuthenticationRequired.asStateFlow()

    private val _isPrivacyModeEnabled = MutableStateFlow(false)
    val isPrivacyModeEnabled: StateFlow<Boolean> = _isPrivacyModeEnabled.asStateFlow()

    private val _isRequireAuthForSendEnabled = MutableStateFlow(false)
    val isRequireAuthForSendEnabled: StateFlow<Boolean> = _isRequireAuthForSendEnabled.asStateFlow()

    init {
        observeWallets()
        observeAuthenticationStatus()
        observePrivacyMode()
        observeTransactionSecurity()
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

    private fun observeAuthenticationStatus() {
        viewModelScope.launch {
            combine(
                isPinSetUseCase(),
                isBiometricEnabledUseCase()
            ) { isPinSet, isBiometricEnabled ->
                isPinSet || isBiometricEnabled
            }.collect { isRequired ->
                _isAuthenticationRequired.value = isRequired
            }
        }
    }

    private fun observePrivacyMode() {
        viewModelScope.launch {
            securityRepository.observePrivacyModeEnabled().collect { isEnabled ->
                _isPrivacyModeEnabled.value = isEnabled
            }
        }
    }

    private fun observeTransactionSecurity() {
        viewModelScope.launch {
            securityRepository.observeRequireAuthForSend().collect { isEnabled ->
                _isRequireAuthForSendEnabled.value = isEnabled
            }
        }
    }
}
