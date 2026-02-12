package com.example.nexuswallet.feature.wallet.ui

import android.util.Log
import com.example.nexuswallet.feature.wallet.data.model.SendTransaction
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.nexuswallet.feature.wallet.data.model.FeeEstimate
import com.example.nexuswallet.feature.coin.bitcoin.FeeLevel
import com.example.nexuswallet.feature.coin.ethereum.EthereumBlockchainRepository
import com.example.nexuswallet.feature.coin.ethereum.EthereumTransactionRepository
import com.example.nexuswallet.feature.coin.ethereum.TransactionState
import com.example.nexuswallet.feature.wallet.data.repository.WalletRepository
import com.example.nexuswallet.feature.wallet.domain.BitcoinWallet
import com.example.nexuswallet.feature.wallet.domain.ChainType
import com.example.nexuswallet.feature.wallet.domain.EthereumWallet
import com.example.nexuswallet.feature.wallet.domain.WalletType
import com.example.nexuswallet.feature.coin.usdc.domain.GetUSDCBalanceUseCase
import com.example.nexuswallet.feature.coin.usdc.domain.SendUSDCUseCase
import com.example.nexuswallet.feature.wallet.domain.CryptoWallet
import com.example.nexuswallet.feature.wallet.domain.EthereumNetwork
import com.example.nexuswallet.feature.wallet.domain.TransactionStatus
import com.example.nexuswallet.feature.wallet.domain.USDCWallet
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
class SendViewModel @Inject constructor(
    private val ethereumTransactionRepository: EthereumTransactionRepository,
    private val walletRepository: WalletRepository,
    private val ethereumBlockchainRepository: EthereumBlockchainRepository,
    private val sendUSDCUseCase: SendUSDCUseCase,
    private val getUSDCBalanceUseCase: GetUSDCBalanceUseCase
) : ViewModel() {

    data class SendUiState(
        val walletId: String = "",
        val walletType: WalletType = WalletType.BITCOIN,
        val fromAddress: String = "",
        val toAddress: String = "",
        val amount: String = "",
        val note: String = "",
        val feeLevel: FeeLevel = FeeLevel.NORMAL,
        val isLoading: Boolean = false,
        val error: String? = null,
        val feeEstimate: FeeEstimate? = null,
        val balance: BigDecimal = BigDecimal.ZERO,
        val usdcBalance: BigDecimal = BigDecimal.ZERO,
        val isValid: Boolean = false,
        val validationError: String? = null,
        val transactionState: TransactionState = TransactionState.Idle,
        val selectedToken: TokenType = TokenType.ETH
    )

    private val _uiState = MutableStateFlow(SendUiState())
    val uiState: StateFlow<SendUiState> = _uiState.asStateFlow()

    sealed class SendEvent {
        data class ToAddressChanged(val address: String) : SendEvent()
        data class AmountChanged(val amount: String) : SendEvent()
        data class NoteChanged(val note: String) : SendEvent()
        data class FeeLevelChanged(val feeLevel: FeeLevel) : SendEvent()
        data class TokenChanged(val token: TokenType) : SendEvent()
        object Validate : SendEvent()
        object CreateTransaction : SendEvent()
        object ClearError : SendEvent()
        object ResetTransactionState : SendEvent()
    }

    fun initialize(walletId: String) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoading = true,
                    transactionState = TransactionState.Idle
                )
            }

            try {
                val wallet = walletRepository.getWallet(walletId)
                if (wallet != null) {
                    val walletBalance = walletRepository.getWalletBalance(walletId)

                    // Get ETH balance (for all Ethereum-based wallets)
                    val ethBalance = if (walletBalance != null) {
                        walletBalance.nativeBalanceDecimal.toBigDecimalOrNull() ?: BigDecimal.ZERO
                    } else {
                        when (wallet) {
                            is EthereumWallet, is USDCWallet ->
                                ethereumBlockchainRepository.getEthereumBalance(wallet.address, getNetworkForWallet(wallet))
                            else -> BigDecimal.ZERO
                        }
                    }

                    // Get USDC balance if wallet supports it
                    var usdcBalance = BigDecimal.ZERO
                    if (wallet is USDCWallet || wallet is EthereumWallet) {
                        try {
                            val tokenBalance = getUSDCBalanceUseCase(walletId)
                            usdcBalance = tokenBalance.balanceDecimal.toBigDecimalOrNull() ?: BigDecimal.ZERO
                        } catch (e: Exception) {
                            Log.e("SendVM", "Error getting USDC balance: ${e.message}")
                        }
                    }

                    val chain = when (wallet) {
                        is BitcoinWallet -> ChainType.BITCOIN
                        is EthereumWallet -> if (wallet.network == EthereumNetwork.SEPOLIA) ChainType.ETHEREUM_SEPOLIA else ChainType.ETHEREUM
                        is USDCWallet -> if (wallet.network == EthereumNetwork.SEPOLIA) ChainType.ETHEREUM_SEPOLIA else ChainType.ETHEREUM
                        else -> ChainType.ETHEREUM
                    }

                    val feeEstimate = ethereumTransactionRepository.getFeeEstimate(chain, FeeLevel.NORMAL)

                    _uiState.update {
                        it.copy(
                            walletId = walletId,
                            walletType = wallet.walletType,
                            fromAddress = wallet.address,
                            balance = ethBalance, // ETH balance
                            usdcBalance = usdcBalance, // USDC balance
                            feeEstimate = feeEstimate,
                            isLoading = false,
                            transactionState = TransactionState.Idle
                        )
                    }

                    validateInputs()
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        error = "Failed to load wallet: ${e.message}",
                        isLoading = false,
                        transactionState = TransactionState.Error("Failed to load wallet: ${e.message}")
                    )
                }
            }
        }
    }

    fun onEvent(event: SendEvent) {
        viewModelScope.launch {
            when (event) {
                is SendEvent.ToAddressChanged -> {
                    _uiState.update { it.copy(toAddress = event.address) }
                    validateInputs()
                }

                is SendEvent.AmountChanged -> {
                    _uiState.update { it.copy(amount = event.amount) }
                    validateInputs()
                }

                is SendEvent.NoteChanged -> {
                    _uiState.update { it.copy(note = event.note) }
                }

                is SendEvent.FeeLevelChanged -> {
                    _uiState.update { it.copy(feeLevel = event.feeLevel) }
                    updateFeeEstimate()
                }

                is SendEvent.TokenChanged -> {
                    _uiState.update { it.copy(selectedToken = event.token) }
                    validateInputs() // Re-validate since token changed
                }

                SendEvent.Validate -> {
                    validateInputs()
                }

                SendEvent.CreateTransaction -> {
                    createTransaction()
                }

                SendEvent.ClearError -> {
                    _uiState.update {
                        it.copy(
                            error = null,
                            validationError = null,
                            transactionState = TransactionState.Idle
                        )
                    }
                }

                SendEvent.ResetTransactionState -> {
                    _uiState.update { it.copy(transactionState = TransactionState.Idle) }
                }
            }
        }
    }

    private suspend fun createTransaction() {
        val state = _uiState.value

        _uiState.update {
            it.copy(
                isLoading = true,
                transactionState = TransactionState.Loading,
                error = null,
                validationError = null
            )
        }

        Log.d("SendVM", " Creating ${state.selectedToken} transaction...")

        try {
            val amount = BigDecimal(state.amount)
            Log.d("SendVM", "Amount: $amount, To: ${state.toAddress}, Token: ${state.selectedToken}")

            when (state.selectedToken) {
                TokenType.ETH -> {
                    createEthTransaction(state, amount)
                }
                TokenType.USDC -> {
                    createUsdcTransaction(state, amount)
                }
            }
        } catch (e: Exception) {
            Log.e("SendVM", " Error: ${e.message}", e)
            _uiState.update {
                it.copy(
                    isLoading = false,
                    error = "Error: ${e.message}",
                    transactionState = TransactionState.Error("Error: ${e.message}")
                )
            }
        }
    }

    private suspend fun createEthTransaction(state: SendUiState, amount: BigDecimal) {
        val result = ethereumTransactionRepository.createSendTransaction(
            walletId = state.walletId,
            toAddress = state.toAddress,
            amount = amount,
            feeLevel = state.feeLevel,
            note = state.note.takeIf { it.isNotEmpty() }
        )

        when {
            result.isSuccess -> {
                val transaction = result.getOrThrow()
                Log.d("SendVM", " ETH Transaction created: ${transaction.hash}")
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        transactionState = TransactionState.Created(transaction)
                    )
                }
            }
            else -> {
                val error = result.exceptionOrNull()?.message ?: "Failed to create transaction"
                Log.e("SendVM", " Transaction failed: $error")
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = error,
                        transactionState = TransactionState.Error(error)
                    )
                }
            }
        }
    }

    private suspend fun createUsdcTransaction(state: SendUiState, amount: BigDecimal) {
        // Use the SendUSDCUseCase instead of USDCTransactionRepository
        val result = sendUSDCUseCase(
            walletId = state.walletId,
            toAddress = state.toAddress,
            amount = amount
        )

        when {
            result.isSuccess -> {
                val broadcastResult = result.getOrThrow()
                Log.d("SendVM", " USDC Transaction result: success=${broadcastResult.success}, hash=${broadcastResult.hash}")

                // Create a SendTransaction object from the broadcast result
                val transaction = SendTransaction(
                    id = "usdc_tx_${System.currentTimeMillis()}",
                    walletId = state.walletId,
                    walletType = state.walletType,
                    fromAddress = state.fromAddress,
                    toAddress = state.toAddress,
                    amount = amount.multiply(BigDecimal("1000000")).toBigInteger().toString(),
                    amountDecimal = amount.toPlainString(),
                    fee = "0",
                    feeDecimal = "0",
                    total = "0",
                    totalDecimal = "0",
                    chain = if (state.walletType == WalletType.ETHEREUM_SEPOLIA) ChainType.ETHEREUM_SEPOLIA else ChainType.ETHEREUM,
                    status = if (broadcastResult.success) TransactionStatus.PENDING else TransactionStatus.FAILED,
                    hash = broadcastResult.hash,
                    timestamp = System.currentTimeMillis(),
                    note = state.note.takeIf { it.isNotEmpty() },
                    feeLevel = state.feeLevel,
                    metadata = mapOf(
                        "token" to "USDC",
                        "broadcast_success" to broadcastResult.success.toString(),
                        "error" to (broadcastResult.error ?: "")
                    )
                )

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        transactionState = if (broadcastResult.success) {
                            TransactionState.Created(transaction)
                        } else {
                            TransactionState.Error(broadcastResult.error ?: "USDC transaction failed")
                        }
                    )
                }
            }
            else -> {
                val error = result.exceptionOrNull()?.message ?: "Failed to create USDC transaction"
                Log.e("SendVM", " USDC Transaction failed: $error")
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = error,
                        transactionState = TransactionState.Error(error)
                    )
                }
            }
        }
    }

    private suspend fun validateInputs() {
        val state = _uiState.value

        if (state.toAddress.isEmpty() || state.amount.isEmpty()) {
            _uiState.update {
                it.copy(
                    isValid = false,
                    validationError = "Please enter address and amount"
                )
            }
            return
        }

        try {
            // Validate address based on wallet type
            val chain = when (state.walletType) {
                WalletType.BITCOIN -> ChainType.BITCOIN
                WalletType.ETHEREUM -> ChainType.ETHEREUM
                else -> ChainType.ETHEREUM
            }

            val isAddressValid = ethereumTransactionRepository.validateAddress(state.toAddress, chain)
            if (!isAddressValid) {
                _uiState.update {
                    it.copy(
                        isValid = false,
                        validationError = "Invalid wallet address"
                    )
                }
                return
            }

            // Validate amount
            val amount = try {
                BigDecimal(state.amount)
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isValid = false,
                        validationError = "Invalid amount"
                    )
                }
                return
            }

            if (amount <= BigDecimal.ZERO) {
                _uiState.update {
                    it.copy(
                        isValid = false,
                        validationError = "Amount must be greater than 0"
                    )
                }
                return
            }

            // Validate balance based on selected token
            when (state.selectedToken) {
                TokenType.ETH -> {
                    // Check ETH balance
                    val feeEstimate = ethereumTransactionRepository.getFeeEstimate(
                        if (state.walletType == WalletType.ETHEREUM_SEPOLIA) ChainType.ETHEREUM_SEPOLIA else ChainType.ETHEREUM,
                        state.feeLevel
                    )
                    val totalRequired = amount + BigDecimal(feeEstimate.totalFeeDecimal)

                    if (totalRequired > state.balance) {
                        _uiState.update {
                            it.copy(
                                isValid = false,
                                validationError = "Insufficient ETH balance"
                            )
                        }
                        return
                    }
                }
                TokenType.USDC -> {
                    // Check USDC balance
                    if (amount > state.usdcBalance) {
                        _uiState.update {
                            it.copy(
                                isValid = false,
                                validationError = "Insufficient USDC balance"
                            )
                        }
                        return
                    }

                    // Also check if there's ETH for gas fees
                    val minEthForGas = BigDecimal("0.001")
                    if (state.balance < minEthForGas) {
                        _uiState.update {
                            it.copy(
                                isValid = false,
                                validationError = "Insufficient ETH for gas fees"
                            )
                        }
                        return
                    }
                }
            }

            _uiState.update {
                it.copy(
                    isValid = true,
                    validationError = null
                )
            }

        } catch (e: Exception) {
            _uiState.update {
                it.copy(
                    isValid = false,
                    validationError = "Validation error: ${e.message}"
                )
            }
        }
    }

    private suspend fun updateFeeEstimate() {
        val state = _uiState.value
        val chain = when (state.walletType) {
            WalletType.BITCOIN -> ChainType.BITCOIN
            WalletType.ETHEREUM -> ChainType.ETHEREUM
            WalletType.ETHEREUM_SEPOLIA -> ChainType.ETHEREUM_SEPOLIA
            else -> ChainType.ETHEREUM
        }

        try {
            val feeEstimate = ethereumTransactionRepository.getFeeEstimate(chain, state.feeLevel)
            _uiState.update { it.copy(feeEstimate = feeEstimate) }
        } catch (e: Exception) {
            // Keep existing fee estimate on error
        }
    }

    // Helper method to get network for wallet
    private fun getNetworkForWallet(wallet: CryptoWallet): EthereumNetwork {
        return when (wallet) {
            is EthereumWallet -> wallet.network
            is USDCWallet -> wallet.network
            else -> EthereumNetwork.SEPOLIA // default
        }
    }

    // Helper to get current balance based on selected token
    fun getCurrentBalance(): BigDecimal {
        return when (_uiState.value.selectedToken) {
            TokenType.ETH -> _uiState.value.balance
            TokenType.USDC -> _uiState.value.usdcBalance
        }
    }

    // Helper to get balance display text
    fun getBalanceDisplay(): String {
        val balance = getCurrentBalance()
        val token = when (_uiState.value.selectedToken) {
            TokenType.ETH -> "ETH"
            TokenType.USDC -> "USDC"
        }
        return "${balance.setScale(6, RoundingMode.HALF_UP)} $token"
    }

    fun getCreatedTransaction(): SendTransaction? {
        return when (val state = _uiState.value.transactionState) {
            is TransactionState.Created -> state.transaction
            else -> null
        }
    }
}


enum class TokenType {
    ETH, USDC
}