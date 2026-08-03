package com.example.nexuswallet.feature.solana.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.nexuswallet.feature.core.domain.exception.HardwareAuthRequiredException
import com.example.nexuswallet.feature.core.util.Result
import com.example.nexuswallet.feature.market.domain.repository.MarketRepository
import com.example.nexuswallet.feature.solana.domain.repository.SolanaBlockchainRepository
import com.example.nexuswallet.feature.solana.domain.usecase.GetSolanaFeeEstimateUseCase
import com.example.nexuswallet.feature.solana.domain.usecase.SendSolanaUseCase
import com.example.nexuswallet.feature.solana.domain.usecase.ValidateSolanaSendUseCase
import com.example.nexuswallet.feature.wallet.domain.model.SolanaCoin
import com.example.nexuswallet.feature.wallet.domain.model.SolanaNetwork
import com.example.nexuswallet.feature.wallet.domain.model.Wallet
import com.example.nexuswallet.feature.wallet.domain.repository.WalletRepository
import com.example.nexuswallet.feature.wallet.util.ExplorerUrlHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.math.BigDecimal
import javax.crypto.Cipher
import javax.inject.Inject

@HiltViewModel
class SolanaSendViewModel @Inject constructor(
    private val sendSolanaUseCase: SendSolanaUseCase,
    private val getFeeUseCase: GetSolanaFeeEstimateUseCase,
    private val validateSolanaSendUseCase: ValidateSolanaSendUseCase,
    private val walletRepository: WalletRepository,
    private val marketRepository: MarketRepository,
    private val solanaRepository: SolanaBlockchainRepository
) : ViewModel() {

    private val _state = MutableStateFlow(SolanaSendUIState())
    val state: StateFlow<SolanaSendUIState> = _state.asStateFlow()

    private val _effect = MutableSharedFlow<SolanaSendEffect>()
    val effect: SharedFlow<SolanaSendEffect> = _effect.asSharedFlow()

    private val _cryptoObject = MutableStateFlow<Cipher?>(null)
    val cryptoObject: StateFlow<Cipher?> = _cryptoObject.asStateFlow()

    private val _authRequest = MutableStateFlow<Long?>(null)
    val authRequest: StateFlow<Long?> = _authRequest.asStateFlow()

    private var feeJob: Job? = null
    private var currentWallet: Wallet? = null

    fun init(walletId: String, coin: SolanaCoin) {
        viewModelScope.launch {
            _state.update { 
                it.copy(
                    isLoading = true, 
                    walletId = walletId, 
                    coin = coin,
                    network = coin.network,
                    walletAddress = coin.address
                ) 
            }
            
            currentWallet = walletRepository.getWallet(walletId)
            if (currentWallet == null) {
                _effect.emit(SolanaSendEffect.ShowError("Wallet not found"))
                return@launch
            }

            // Initial balance check
            refreshBalance()
            loadFiatRate()
            loadFeeEstimate()
        }
    }

    fun onEvent(event: SolanaSendEvent) {
        when (event) {
            is SolanaSendEvent.ToAddressChanged -> {
                _state.update { it.copy(toAddress = event.address) }
                validate()
                if (event.address.length >= 32) {
                    loadFeeEstimate()
                }
            }
            is SolanaSendEvent.AmountChanged -> {
                val bigDecimalAmount = event.amount.toBigDecimalOrNull() ?: BigDecimal.ZERO
                _state.update { it.copy(amount = event.amount, amountValue = bigDecimalAmount) }
                validate()
                loadFeeEstimate()
            }
            is SolanaSendEvent.FeeLevelChanged -> {
                _state.update { it.copy(feeLevel = event.feeLevel) }
                loadFeeEstimate()
            }
            SolanaSendEvent.Validate -> validate()
            SolanaSendEvent.ClearError -> clearError()
        }
    }

    fun switchNetwork(network: SolanaNetwork) {
        _state.update { it.copy(network = network, isLoading = true, balance = BigDecimal.ZERO) }
        refreshBalance()
        loadFeeEstimate()
    }

    private fun validate() {
        val currentState = _state.value
        val validationResult = validateSolanaSendUseCase(
            toAddress = currentState.toAddress,
            amountValue = currentState.amountValue,
            walletAddress = currentState.walletAddress,
            balance = currentState.balance,
            feeEstimate = currentState.feeEstimate
        )
        
        _state.update { it.copy(validationResult = validationResult, isValid = validationResult.isValid) }
    }

    private fun loadFiatRate() {
        viewModelScope.launch {
            when (val result = marketRepository.getTokenDetails("solana")) {
                is Result.Success -> {
                    _state.update { it.copy(fiatRate = result.data.currentPrice) }
                }
                else -> {}
            }
        }
    }

    private fun refreshBalance() {
        viewModelScope.launch {
            val currentState = _state.value
            val coin = currentState.coin ?: return@launch
            val result = solanaRepository.getBalance(coin.address, currentState.network)
            if (result is Result.Success) {
                _state.update { it.copy(balance = result.data, isLoading = false) }
                validate()
            }
        }
    }

    private fun loadFeeEstimate() {
        feeJob?.cancel()
        feeJob = viewModelScope.launch {
            delay(500)
            val currentState = _state.value
            val coin = currentState.coin ?: return@launch
            
            _state.update { it.copy(isFeeLoading = true) }
            
            val result = getFeeUseCase(currentState.feeLevel, currentState.network)
            
            if (result is Result.Success) {
                _state.update { it.copy(feeEstimate = result.data, isFeeLoading = false) }
                validate()
            } else {
                _state.update { it.copy(isFeeLoading = false) }
            }
        }
    }

    fun send(cipher: Cipher? = null, onSuccess: (String) -> Unit) {
        viewModelScope.launch {
            val currentState = _state.value
            val currentCoin = currentState.coin ?: return@launch

            _state.update { it.copy(isLoading = true) }

            val result = sendSolanaUseCase(
                walletId = currentState.walletId,
                toAddress = currentState.toAddress,
                amount = currentState.amountValue,
                feeLevel = currentState.feeLevel,
                coin = currentCoin,
                note = null,
                cipher = cipher
            )

            when (result) {
                is Result.Success -> {
                    val sendResult = result.data
                    if (sendResult.success) {
                        val txHash = sendResult.txHash
                        val explorerUrl = ExplorerUrlHelper.getExplorerUrl(txHash, currentState.network)
                        _effect.emit(SolanaSendEffect.TransactionSent(txHash, explorerUrl))
                        onSuccess(txHash)
                    } else {
                        _effect.emit(SolanaSendEffect.ShowError(sendResult.error ?: "Send failed"))
                    }
                }
                is Result.Error -> {
                    val authException = result.throwable as? HardwareAuthRequiredException
                    if (authException != null) {
                        _cryptoObject.value = authException.cryptoObject?.cipher
                        _authRequest.value = System.currentTimeMillis()
                    } else {
                        _effect.emit(SolanaSendEffect.ShowError(result.message))
                    }
                }
                Result.Loading -> {}
            }
            _state.update { it.copy(isLoading = false) }
        }
    }

    fun completeSendAfterBiometric(cipher: Cipher? = null, onSuccess: (String) -> Unit) {
        _cryptoObject.value = null
        _authRequest.value = null
        send(cipher, onSuccess)
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }

    fun clearAuthRequest() {
        _authRequest.value = null
    }
}
