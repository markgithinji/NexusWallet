package com.example.nexuswallet

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.nexuswallet.feature.coin.Result
import com.example.nexuswallet.feature.wallet.data.securityrefactor.IsSessionValidUseCase
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.Wallet
import com.example.nexuswallet.feature.wallet.domain.WalletRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
@HiltViewModel
class NavigationViewModel @Inject constructor(
    private val walletRepository: WalletRepository,
    private val isSessionValidUseCase: IsSessionValidUseCase
) : ViewModel() {

    // Separate loading state
    private val _isWalletsLoading = MutableStateFlow(true)
    val isWalletsLoading: StateFlow<Boolean> = _isWalletsLoading.asStateFlow()

    // Observe wallets
    val wallets: StateFlow<List<Wallet>> = walletRepository.observeWallets()
        .onStart {
            Log.d("NavigationVM", "Flow started - setting loading = true")
            _isWalletsLoading.value = true
        }
        .onEach { walletsList ->
            Log.d("NavigationVM", "Flow emitted ${walletsList.size} wallets - setting loading = false")
            _isWalletsLoading.value = false
        }
        .catch { e ->
            Log.e("NavigationVM", "Error in wallet flow: ${e.message}", e)
            _isWalletsLoading.value = false
            emit(emptyList())
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    // Add a timeout to prevent infinite loading
    init {
        viewModelScope.launch {
            delay(5000) // 5 second timeout
            if (_isWalletsLoading.value) {
                Log.e("NavigationVM", "⚠️ Loading timeout reached - forcing loading to false")
                _isWalletsLoading.value = false
            }
        }
    }

    // Track if we should navigate to auth screen
    private val _shouldNavigateToAuth = MutableStateFlow<Pair<String, String>?>(null)
    val shouldNavigateToAuth = _shouldNavigateToAuth.asStateFlow()

    /**
     * Check if authentication is needed before navigating to wallet detail
     */
    fun navigateToWalletDetail(walletId: String) {
        viewModelScope.launch {
            Log.d("NavigationVM", "navigateToWalletDetail called for walletId: $walletId")
            val result = isSessionValidUseCase()

            val requiresAuth = when (result) {
                is Result.Success -> {
                    Log.d("NavigationVM", "Session valid check: ${result.data}")
                    !result.data  // If session is NOT valid, require auth
                }
                is Result.Error -> {
                    Log.e("NavigationVM", "Failed to check session validity: ${result.message}")
                    true // Default to requiring auth on error
                }
                Result.Loading -> {
                    Log.d("NavigationVM", "Session check loading")
                    true // Default to requiring auth
                }
            }

            if (requiresAuth) {
                Log.d("NavigationVM", "Auth required for wallet detail")
                _shouldNavigateToAuth.value = Pair("walletDetail", walletId)
            } else {
                Log.d("NavigationVM", "No auth required, clearing navigation")
                _shouldNavigateToAuth.value = null
            }
        }
    }

    fun clearAuthNavigation() {
        viewModelScope.launch {
            Log.d("NavigationVM", "Clearing auth navigation")
            _shouldNavigateToAuth.value = null
        }
    }

    suspend fun getWalletById(walletId: String): Wallet? {
        return walletRepository.getWallet(walletId)
    }

    fun findWalletById(walletId: String): Wallet? {
        return wallets.value.find { it.id == walletId }
    }
}