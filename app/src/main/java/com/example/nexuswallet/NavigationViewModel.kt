package com.example.nexuswallet

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.nexuswallet.feature.core.util.Result
import com.example.nexuswallet.feature.settings.ui.IsBiometricEnabledUseCase
import com.example.nexuswallet.feature.settings.ui.IsPinSetUseCase
import com.example.nexuswallet.feature.wallet.domain.Wallet
import com.example.nexuswallet.feature.wallet.domain.WalletRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.collections.emptyList

@HiltViewModel
class NavigationViewModel @Inject constructor(
    private val isPinSetUseCase: IsPinSetUseCase,
    private val isBiometricEnabledUseCase: IsBiometricEnabledUseCase,
    private val walletRepository: WalletRepository
) : ViewModel() {

    private val _shouldNavigateToAuth = MutableStateFlow<Pair<String, String>?>(null)
    val shouldNavigateToAuth: StateFlow<Pair<String, String>?> = _shouldNavigateToAuth.asStateFlow()

    private val _wallets = MutableStateFlow<List<Wallet>>(emptyList())
    val wallets: StateFlow<List<Wallet>> = _wallets.asStateFlow()

    private val _isWalletsLoading = MutableStateFlow(true)
    val isWalletsLoading: StateFlow<Boolean> = _isWalletsLoading.asStateFlow()

    init {
        observeWallets()
    }

    private fun observeWallets() {
        viewModelScope.launch {
            walletRepository.observeWallets()
                .catch { e ->
                    Log.e("NavigationVM", "Error observing wallets", e)
                    _isWalletsLoading.value = false
                }
                .collect { walletsList ->
                    _wallets.value = walletsList
                    _isWalletsLoading.value = false
                    Log.d("NavigationVM", "Flow emitted ${walletsList.size} wallets - setting loading = false")
                }
        }
    }

    suspend fun isAuthenticationRequired(): Boolean {
        val isPinSet = when (val result = isPinSetUseCase()) {
            is Result.Success -> result.data
            else -> false
        }
        val isBiometricEnabled = when (val result = isBiometricEnabledUseCase()) {
            is Result.Success -> result.data
            else -> false
        }
        return isPinSet || isBiometricEnabled
    }

    fun requestAuthentication(screen: String, walletId: String) {
        viewModelScope.launch {
            if (isAuthenticationRequired()) {
                _shouldNavigateToAuth.value = screen to walletId
            } else {
                // Navigate directly
                when (screen) {
                    "walletDetail" -> {
                        // This will be handled by the direct navigation callback
                    }
                }
            }
        }
    }

    fun clearAuthNavigation() {
        _shouldNavigateToAuth.value = null
    }
}