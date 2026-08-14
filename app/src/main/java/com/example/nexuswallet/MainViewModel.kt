package com.example.nexuswallet

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.nexuswallet.feature.core.util.Result
import com.example.nexuswallet.feature.market.domain.usecase.GetSimplePricesUseCase
import com.example.nexuswallet.feature.settings.domain.model.SupportedCurrency
import com.example.nexuswallet.feature.settings.domain.model.ThemeMode
import com.example.nexuswallet.feature.settings.domain.repository.SettingsRepository
import com.example.nexuswallet.feature.settings.domain.usecase.IsBiometricEnabledUseCase
import com.example.nexuswallet.feature.settings.domain.usecase.IsPinSetUseCase
import com.example.nexuswallet.feature.wallet.domain.model.Wallet
import com.example.nexuswallet.feature.wallet.domain.repository.WalletRepository
import com.example.nexuswallet.feature.core.ui.CurrencyState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val walletRepository: WalletRepository,
    private val isPinSetUseCase: IsPinSetUseCase,
    private val isBiometricEnabledUseCase: IsBiometricEnabledUseCase,
    private val getSimplePricesUseCase: GetSimplePricesUseCase
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

    val currencyState: StateFlow<CurrencyState> = combine(
        settingsRepository.observeSelectedCurrency(),
        settingsRepository.observeUsdToRate()
    ) { currency, rate ->
        CurrencyState(currency, rate)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = CurrencyState()
    )

    init {
        observeWallets()
        observeCurrencyChanges()
    }

    private fun observeCurrencyChanges() {
        viewModelScope.launch {
            settingsRepository.observeSelectedCurrency().collect { currency ->
                updateExchangeRate(currency)
            }
        }
    }

    private fun updateExchangeRate(currency: SupportedCurrency) {
        if (currency == SupportedCurrency.USD) {
            viewModelScope.launch {
                settingsRepository.setUsdToRate(1.0)
            }
            return
        }

        viewModelScope.launch {
            // 1. Try fetching BTC price in both USD and target currency to derive the rate
            // This is more reliable for fiat currencies like KES
            val btcInTargetResult = getSimplePricesUseCase(listOf("BTC"), currency)
            val btcInUsdResult = getSimplePricesUseCase(listOf("BTC"), SupportedCurrency.USD)
            
            var rate = 0.0
            
            if (btcInTargetResult is Result.Success && btcInUsdResult is Result.Success) {
                val btcInTarget = btcInTargetResult.data["BTC"] ?: 0.0
                val btcInUsd = btcInUsdResult.data["BTC"] ?: 0.0
                
                if (btcInUsd > 0) {
                    rate = btcInTarget / btcInUsd
                }
            }

            // 2. Fallback to USDC price if BTC failed
            if (rate <= 0.0) {
                val usdcResult = getSimplePricesUseCase(listOf("USDC"), currency)
                if (usdcResult is Result.Success) {
                    rate = usdcResult.data["USDC"] ?: 0.0
                }
            }
            
            // 3. Last resort fallback to ETH
            if (rate <= 0.0) {
                val ethResult = getSimplePricesUseCase(listOf("ETH"), currency)
                val ethUsdResult = getSimplePricesUseCase(listOf("ETH"), SupportedCurrency.USD)
                if (ethResult is Result.Success && ethUsdResult is Result.Success) {
                    val ethInTarget = ethResult.data["ETH"] ?: 0.0
                    val ethInUsd = ethUsdResult.data["ETH"] ?: 0.0
                    if (ethInUsd > 0) {
                        rate = ethInTarget / ethInUsd
                    }
                }
            }

            if (rate > 0) {
                settingsRepository.setUsdToRate(rate)
            } else {
                // If dynamic fetch failed, check for specific hardcoded fallbacks for unsupported fiat
                val fallbackRate = when (currency) {
                    SupportedCurrency.KES -> 129.40 // Current approximate rate for KSh
                    else -> 0.0
                }

                if (fallbackRate > 0) {
                    settingsRepository.setUsdToRate(fallbackRate)
                } else {
                    val currentRate = settingsRepository.getUsdToRate()
                    if (currentRate <= 0.0) {
                        settingsRepository.setUsdToRate(1.0)
                    }
                }
            }
        }
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
