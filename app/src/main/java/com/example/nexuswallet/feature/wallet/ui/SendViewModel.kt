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
import com.example.nexuswallet.feature.wallet.usdc.USDCTransactionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.math.BigDecimal
import javax.inject.Inject

@HiltViewModel
class SendViewModel @Inject constructor(
    private val ethereumTransactionRepository: EthereumTransactionRepository,
    private val walletRepository: WalletRepository,
    private val ethereumBlockchainRepository: EthereumBlockchainRepository,
    private val usdcTransactionRepository: USDCTransactionRepository
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
                    val balanceValue = if (walletBalance != null) {
                        walletBalance.nativeBalanceDecimal.toBigDecimalOrNull() ?: BigDecimal.ZERO
                    } else {
                        when (wallet) {
                            is EthereumWallet -> ethereumBlockchainRepository.getEthereumBalance(wallet.address)
                            else -> BigDecimal.ZERO
                        }
                    }

                    val chain = when (wallet) {
                        is BitcoinWallet -> ChainType.BITCOIN
                        is EthereumWallet -> ChainType.ETHEREUM
                        else -> ChainType.ETHEREUM
                    }

                    val feeEstimate = ethereumTransactionRepository.getFeeEstimate(chain, FeeLevel.NORMAL)

                    _uiState.update {
                        it.copy(
                            walletId = walletId,
                            walletType = wallet.walletType,
                            fromAddress = wallet.address,
                            balance = balanceValue,
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
        val result = usdcTransactionRepository.createUSDCTransfer(
            walletId = state.walletId,
            toAddress = state.toAddress,
            amount = amount,
            note = state.note.takeIf { it.isNotEmpty() }
        )

        when {
            result.isSuccess -> {
                val transaction = result.getOrThrow()
                Log.d("SendVM", " USDC Transaction created: ${transaction.id}")
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        transactionState = TransactionState.Created(transaction)
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

            // For USDC, we'll validate balance during transaction creation
            // For ETH, check if amount + fee <= balance
            if (state.selectedToken == TokenType.ETH) {
                val feeEstimate = ethereumTransactionRepository.getFeeEstimate(
                    if (state.walletType == WalletType.ETHEREUM_SEPOLIA) ChainType.ETHEREUM_SEPOLIA else ChainType.ETHEREUM,
                    state.feeLevel
                )
                val totalRequired = amount + BigDecimal(feeEstimate.totalFeeDecimal)

                if (totalRequired > state.balance) {
                    _uiState.update {
                        it.copy(
                            isValid = false,
                            validationError = "Insufficient balance"
                        )
                    }
                    return
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
        }
    }

    fun getCreatedTransaction(): SendTransaction? {
        return null // UI will handle navigation
    }
}


enum class TokenType {
    ETH, USDC
}