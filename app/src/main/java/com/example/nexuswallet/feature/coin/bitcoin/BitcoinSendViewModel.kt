package com.example.nexuswallet.feature.coin.bitcoin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.nexuswallet.feature.coin.Result
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.BitcoinCoin
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.BitcoinNetwork
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.Wallet
import com.example.nexuswallet.feature.wallet.domain.WalletRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.math.RoundingMode
import javax.inject.Inject
@HiltViewModel
class BitcoinSendViewModel @Inject constructor(
    private val getBitcoinWalletUseCase: GetBitcoinWalletUseCase,
    private val getBitcoinBalanceUseCase: GetBitcoinBalanceUseCase,
    private val getBitcoinFeeEstimateUseCase: GetBitcoinFeeEstimateUseCase,
    private val sendBitcoinUseCase: SendBitcoinUseCase,
    private val validateBitcoinTransactionUseCase: ValidateBitcoinTransactionUseCase,
    private val walletRepository: WalletRepository
) : ViewModel() {

    data class BtcSendUiState(
        val walletId: String = "",
        val walletName: String = "",
        val walletAddress: String = "",
        val network: BitcoinNetwork = BitcoinNetwork.Testnet,
        val availableNetworks: List<BitcoinNetwork> = emptyList(),
        val balance: BigDecimal = BigDecimal.ZERO,
        val balanceFormatted: String = "0 BTC",
        val toAddress: String = "",
        val amount: String = "",
        val amountValue: BigDecimal = BigDecimal.ZERO,
        val feeLevel: FeeLevel = FeeLevel.NORMAL,
        val feeEstimate: BitcoinFeeEstimate? = null,
        val validationResult: ValidateBitcoinTransactionUseCase.ValidationResult = ValidateBitcoinTransactionUseCase.ValidationResult(
            isValid = false
        ),
        val isValid: Boolean = false,
        val isLoading: Boolean = false,
        val error: String? = null,
        val info: String? = null,
        val step: String = ""
    )

    private val _state = MutableStateFlow(BtcSendUiState())
    val state: StateFlow<BtcSendUiState> = _state.asStateFlow()

    private var wallet: Wallet? = null
    private var bitcoinCoins: Map<BitcoinNetwork, BitcoinCoin> = emptyMap()

    fun init(walletId: String, network: BitcoinNetwork? = null) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }

            // Load full wallet from repository
            wallet = walletRepository.getWallet(walletId)

            if (wallet == null) {
                _state.update { it.copy(error = "Wallet not found", isLoading = false) }
                return@launch
            }

            // Get all Bitcoin coins for this wallet
            bitcoinCoins = wallet!!.bitcoinCoins.associateBy { it.network }

            if (bitcoinCoins.isEmpty()) {
                _state.update { it.copy(error = "Bitcoin not enabled for this wallet", isLoading = false) }
                return@launch
            }

            val availableNetworks = bitcoinCoins.keys.toList()

            // Determine which network to use
            val targetNetwork = network ?: availableNetworks.firstOrNull() ?: BitcoinNetwork.Testnet
            val bitcoinCoin = bitcoinCoins[targetNetwork]

            if (bitcoinCoin == null) {
                _state.update {
                    it.copy(
                        error = "Bitcoin not enabled for network $targetNetwork",
                        isLoading = false,
                        availableNetworks = availableNetworks
                    )
                }
                return@launch
            }

            when (val result = getBitcoinWalletUseCase(walletId, targetNetwork)) {
                is Result.Success -> {
                    val walletInfo = result.data
                    _state.update {
                        it.copy(
                            walletId = walletInfo.walletId,
                            walletName = walletInfo.walletName,
                            walletAddress = walletInfo.walletAddress,
                            network = walletInfo.network,
                            availableNetworks = availableNetworks,
                            isLoading = false
                        )
                    }
                    // Load balance after we have the address
                    loadBalance(walletInfo.walletAddress, walletInfo.network)
                    // Load initial fee estimate
                    updateFeeLevel(FeeLevel.NORMAL)
                }

                is Result.Error -> {
                    _state.update {
                        it.copy(
                            error = result.message,
                            isLoading = false,
                            availableNetworks = availableNetworks
                        )
                    }
                }

                Result.Loading -> {}
            }
        }
    }

    // Helper method to set transaction data from review screen
    fun setTransactionData(
        toAddress: String,
        amount: String,
        feeLevel: FeeLevel
    ) {
        val amountValue = try {
            amount.toBigDecimalOrNull() ?: BigDecimal.ZERO
        } catch (e: Exception) {
            BigDecimal.ZERO
        }

        _state.update {
            it.copy(
                toAddress = toAddress,
                amount = amount,
                amountValue = amountValue,
                feeLevel = feeLevel
            )
        }

        viewModelScope.launch {
            // Reload fee estimate with new fee level
            when (val feeResult = getBitcoinFeeEstimateUseCase(feeLevel)) {
                is Result.Success -> {
                    _state.update { it.copy(feeEstimate = feeResult.data) }
                    validateInputs()
                }
                is Result.Error -> {
                    _state.update { it.copy(error = "Failed to load fee: ${feeResult.message}") }
                }
                Result.Loading -> {}
            }
        }
    }

    fun switchNetwork(network: BitcoinNetwork) {
        val bitcoinCoin = bitcoinCoins[network]
        if (bitcoinCoin == null) {
            _state.update { it.copy(error = "Bitcoin not available on $network") }
            return
        }

        _state.update {
            it.copy(
                network = network,
                walletAddress = bitcoinCoin.address,
                toAddress = "",
                amount = "",
                amountValue = BigDecimal.ZERO,
                feeEstimate = null,
                error = null
            )
        }

        viewModelScope.launch {
            loadBalance(bitcoinCoin.address, network)
            updateFeeLevel(FeeLevel.NORMAL)
        }
    }

    private suspend fun loadBalance(address: String, network: BitcoinNetwork) {
        when (val balanceResult = getBitcoinBalanceUseCase(address, network)) {
            is Result.Success -> {
                val balance = balanceResult.data
                _state.update {
                    it.copy(
                        balance = balance,
                        balanceFormatted = "${balance.setScale(8, RoundingMode.HALF_UP)} BTC"
                    )
                }
                validateInputs()
            }

            is Result.Error -> {
                _state.update {
                    it.copy(
                        error = "Failed to load balance: ${balanceResult.message}"
                    )
                }
            }

            Result.Loading -> {}
        }
    }

    fun updateFeeLevel(feeLevel: FeeLevel) {
        _state.update { it.copy(feeLevel = feeLevel) }
        viewModelScope.launch {
            when (val feeResult = getBitcoinFeeEstimateUseCase(feeLevel)) {
                is Result.Success -> {
                    _state.update {
                        it.copy(
                            feeEstimate = feeResult.data
                        )
                    }
                    validateInputs()
                }

                is Result.Error -> {
                    _state.update { it.copy(error = "Failed to load fee: ${feeResult.message}") }
                }

                Result.Loading -> {}
            }
        }
    }

    fun updateAddress(address: String) {
        _state.update { it.copy(toAddress = address) }
        validateInputs()
    }

    fun updateAmount(amount: String) {
        val amountValue = try {
            amount.toBigDecimalOrNull() ?: BigDecimal.ZERO
        } catch (e: Exception) {
            BigDecimal.ZERO
        }
        _state.update {
            it.copy(
                amount = amount,
                amountValue = amountValue
            )
        }
        validateInputs()
    }

    private fun validateInputs() {
        val state = _state.value
        val currentWallet = wallet
        val bitcoinCoin = bitcoinCoins[state.network]

        val validationResult = validateBitcoinTransactionUseCase(
            walletId = state.walletId,
            wallet = currentWallet,
            toAddress = state.toAddress,
            amount = state.amountValue,
            network = state.network,
            balance = state.balance,
            feeEstimate = state.feeEstimate
        )

        _state.update {
            it.copy(
                validationResult = validationResult,
                isValid = validationResult.isValid
            )
        }

        // Update error field for backward compatibility
        val firstError = validationResult.addressError
            ?: validationResult.amountError
            ?: validationResult.balanceError
            ?: validationResult.selfSendError

        if (firstError != null) {
            _state.update { it.copy(error = firstError) }
        } else if (validationResult.isValid) {
            _state.update { it.copy(error = null) }
        }
    }

    fun send(onSuccess: (String) -> Unit) {
        viewModelScope.launch {
            val state = _state.value
            val walletId = state.walletId
            val currentWallet = wallet
            val bitcoinCoin = bitcoinCoins[state.network]

            if (walletId.isEmpty() || currentWallet == null || bitcoinCoin == null) {
                _state.update { it.copy(error = "Wallet not properly loaded") }
                return@launch
            }

            if (!state.validationResult.isValid) {
                return@launch
            }

            _state.update { it.copy(isLoading = true, error = null, step = "Sending...") }

            val result = sendBitcoinUseCase(
                walletId = walletId,
                toAddress = state.toAddress,
                amount = state.amountValue,
                feeLevel = state.feeLevel,
                network = state.network,
                note = null
            )

            when (result) {
                is Result.Success -> {
                    val sendResult = result.data
                    if (sendResult.success) {
                        _state.update { it.copy(isLoading = false, step = "Sent!") }
                        onSuccess(sendResult.txHash)
                    } else {
                        _state.update {
                            it.copy(
                                isLoading = false,
                                error = sendResult.error ?: "Send failed"
                            )
                        }
                    }
                }

                is Result.Error -> {
                    _state.update {
                        it.copy(
                            isLoading = false,
                            error = result.message
                        )
                    }
                }

                Result.Loading -> {}
            }
        }
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }

    fun clearInfo() {
        _state.update { it.copy(info = null) }
    }
}