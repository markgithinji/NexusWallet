package com.example.nexuswallet

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.nexuswallet.feature.authentication.domain.AuthAction
import com.example.nexuswallet.feature.wallet.data.repository.WalletRepository
import com.example.nexuswallet.feature.wallet.data.securityrefactor.IsAuthenticationRequiredUseCase
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.Wallet
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.collections.isNotEmpty
import com.example.nexuswallet.feature.coin.Result

@HiltViewModel
class NavigationViewModel @Inject constructor(
    walletRepository: WalletRepository,
    private val isAuthenticationRequiredUseCase: IsAuthenticationRequiredUseCase
) : ViewModel() {

    val wallets: StateFlow<List<Wallet>> = walletRepository.walletsFlow

    val hasWallets: StateFlow<Boolean> = wallets.map { it.isNotEmpty() }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = false
        )

    // Track if we should navigate to auth screen
    private val _shouldNavigateToAuth = MutableStateFlow<Pair<String, String>?>(null)
    val shouldNavigateToAuth = _shouldNavigateToAuth.asStateFlow()

    /**
     * Check if authentication is needed before navigating to wallet detail
     */
    fun navigateToWalletDetail(walletId: String) {
        viewModelScope.launch {
            val result = isAuthenticationRequiredUseCase(AuthAction.VIEW_WALLET)

            val requiresAuth = when (result) {
                is Result.Success -> result.data
                is Result.Error -> {
                    // Log error and default to requiring auth for safety
                    Log.e("NavigationVM", "Failed to check auth requirement: ${result.message}")
                    true // Default to requiring auth on error
                }
                Result.Loading -> {
                    // This shouldn't happen with our implementation
                    true // Default to requiring auth
                }
            }

            if (requiresAuth) {
                // Store that we need to go to auth screen first
                _shouldNavigateToAuth.value = Pair("walletDetail", walletId)
            } else {
                // Clear any pending auth navigation
                _shouldNavigateToAuth.value = null
            }
        }
    }

    fun clearAuthNavigation() {
        viewModelScope.launch {
            _shouldNavigateToAuth.value = null
        }
    }

    fun getWalletById(walletId: String): Wallet? {
        return wallets.value.find { it.id == walletId }
    }
}