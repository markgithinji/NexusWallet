package com.example.nexuswallet.feature.wallet.ui

import android.util.Log
import com.example.nexuswallet.feature.wallet.data.model.SendTransaction
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.nexuswallet.feature.wallet.data.model.FeeEstimate
import com.example.nexuswallet.feature.coin.bitcoin.FeeLevel
import com.example.nexuswallet.feature.coin.ethereum.EthereumBlockchainRepository
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
import com.example.nexuswallet.feature.coin.Result
import com.example.nexuswallet.feature.coin.ethereum.CreateSendTransactionUseCase
import com.example.nexuswallet.feature.coin.ethereum.EthereumTransaction
import com.example.nexuswallet.feature.coin.ethereum.GetFeeEstimateUseCase
import com.example.nexuswallet.feature.coin.ethereum.GetTransactionUseCase
import com.example.nexuswallet.feature.coin.ethereum.ValidateAddressUseCase
@HiltViewModel
class EthereumSendViewModel @Inject constructor(
    private val createSendTransactionUseCase: CreateSendTransactionUseCase,
    private val validateAddressUseCase: ValidateAddressUseCase,
    private val getFeeEstimateUseCase: GetFeeEstimateUseCase,
    private val getTransactionUseCase: GetTransactionUseCase,
    private val walletRepository: WalletRepository,
    private val ethereumBlockchainRepository: EthereumBlockchainRepository
) : ViewModel() {

    data class SendUiState(
        val walletId: String = "",
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
        val network: String = ""
    )

    sealed class TransactionState {
        object Idle : TransactionState()
        object Loading : TransactionState()
        data class Created(val transaction: EthereumTransaction) : TransactionState()
        data class Error(val message: String) : TransactionState()
    }

    private val _uiState = MutableStateFlow(SendUiState())
    val uiState: StateFlow<SendUiState> = _uiState.asStateFlow()

    sealed class SendEvent {
        data class ToAddressChanged(val address: String) : SendEvent()
        data class AmountChanged(val amount: String) : SendEvent()
        data class NoteChanged(val note: String) : SendEvent()
        data class FeeLevelChanged(val feeLevel: FeeLevel) : SendEvent()
        object Validate : SendEvent()
        object CreateTransaction : SendEvent()
        object ClearError : SendEvent()
        object ResetTransactionState : SendEvent()
        object FetchTransaction : SendEvent()
    }

    fun initialize(walletId: String) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoading = true,
                    transactionState = TransactionState.Idle
                )
            }

            val wallet = walletRepository.getWallet(walletId)
            if (wallet == null) {
                _uiState.update {
                    it.copy(
                        error = "Wallet not found",
                        isLoading = false,
                        transactionState = TransactionState.Error("Wallet not found")
                    )
                }
                return@launch
            }

            val ethereumCoin = wallet.ethereum
            if (ethereumCoin == null) {
                _uiState.update {
                    it.copy(
                        error = "Ethereum not enabled for this wallet",
                        isLoading = false,
                        transactionState = TransactionState.Error("Ethereum not enabled")
                    )
                }
                return@launch
            }

            val walletBalance = walletRepository.getWalletBalance(walletId)

            // Get ETH balance
            val ethBalance = if (walletBalance?.ethereum != null) {
                walletBalance.ethereum.eth.toBigDecimalOrNull() ?: BigDecimal.ZERO
            } else {
                val balanceResult = ethereumBlockchainRepository.getEthereumBalance(
                    ethereumCoin.address,
                    ethereumCoin.network
                )
                when (balanceResult) {
                    is Result.Success -> balanceResult.data
                    is Result.Error -> {
                        Log.e("EthereumSendVM", "Error getting ETH balance: ${balanceResult.message}")
                        BigDecimal.ZERO
                    }
                    Result.Loading -> BigDecimal.ZERO
                }
            }

            val feeEstimateResult = getFeeEstimateUseCase(FeeLevel.NORMAL)
            val feeEstimate = when (feeEstimateResult) {
                is Result.Success -> feeEstimateResult.data
                is Result.Error -> {
                    Log.e("EthereumSendVM", "Error getting fee estimate: ${feeEstimateResult.message}")
                    null
                }
                Result.Loading -> null
            }

            _uiState.update {
                it.copy(
                    walletId = walletId,
                    fromAddress = ethereumCoin.address,
                    balance = ethBalance,
                    feeEstimate = feeEstimate,
                    network = ethereumCoin.network.name,
                    isLoading = false,
                    transactionState = TransactionState.Idle
                )
            }

            validateInputs()
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

                SendEvent.Validate -> {
                    validateInputs()
                }

                SendEvent.CreateTransaction -> {
                    createTransaction()
                }

                SendEvent.FetchTransaction -> {
                    fetchCreatedTransaction()
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

        Log.d("EthereumSendVM", "Creating Ethereum transaction...")

        try {
            val amount = BigDecimal(state.amount)
            Log.d("EthereumSendVM", "Amount: $amount ETH, To: ${state.toAddress}")

            createEthTransaction(state, amount)
        } catch (e: Exception) {
            Log.e("EthereumSendVM", " Error: ${e.message}", e)
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
        val result = createSendTransactionUseCase(
            walletId = state.walletId,
            toAddress = state.toAddress,
            amount = amount,
            feeLevel = state.feeLevel,
            note = state.note.takeIf { it.isNotEmpty() }
        )

        when (result) {
            is Result.Success -> {
                val transaction = result.data
                Log.d("EthereumSendVM", "ETH Transaction created: ${transaction.id}")
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        transactionState = TransactionState.Created(transaction)
                    )
                }
            }
            is Result.Error -> {
                val error = result.message
                Log.e("EthereumSendVM", "Transaction failed: $error")
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = error,
                        transactionState = TransactionState.Error(error)
                    )
                }
            }
            Result.Loading -> {}
        }
    }

    private suspend fun fetchCreatedTransaction() {
        val transactionState = _uiState.value.transactionState
        if (transactionState is TransactionState.Created) {
            val transactionId = transactionState.transaction.id
            val result = getTransactionUseCase(transactionId)
            when (result) {
                is Result.Success -> {
                    val transaction = result.data
                    Log.d("EthereumSendVM", "Fetched transaction: ${transaction.id}, status: ${transaction.status}")
                    _uiState.update {
                        it.copy(
                            transactionState = TransactionState.Created(transaction)
                        )
                    }
                }
                is Result.Error -> {
                    Log.e("EthereumSendVM", "Error fetching transaction: ${result.message}")
                }
                Result.Loading -> {}
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
            val addressValidationResult = validateAddressUseCase(state.toAddress)
            if (!addressValidationResult) {
                _uiState.update {
                    it.copy(
                        isValid = false,
                        validationError = "Invalid Ethereum address"
                    )
                }
                return
            }

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

            val feeEstimateResult = getFeeEstimateUseCase(state.feeLevel)

            when (feeEstimateResult) {
                is Result.Success -> {
                    val feeEstimate = feeEstimateResult.data
                    val totalRequired = amount + BigDecimal(feeEstimate.totalFeeDecimal)

                    if (totalRequired > state.balance) {
                        _uiState.update {
                            it.copy(
                                isValid = false,
                                validationError = "Insufficient ETH balance",
                                feeEstimate = feeEstimate
                            )
                        }
                        return
                    }

                    _uiState.update {
                        it.copy(
                            isValid = true,
                            validationError = null,
                            feeEstimate = feeEstimate
                        )
                    }
                }
                is Result.Error -> {
                    _uiState.update {
                        it.copy(
                            isValid = false,
                            validationError = "Failed to get fee estimate: ${feeEstimateResult.message}"
                        )
                    }
                    return
                }
                Result.Loading -> return
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
        val feeEstimateResult = getFeeEstimateUseCase(state.feeLevel)
        when (feeEstimateResult) {
            is Result.Success -> {
                _uiState.update { it.copy(feeEstimate = feeEstimateResult.data) }
                validateInputs() // Re-validate with new fee
            }
            is Result.Error -> {
                Log.e("EthereumSendVM", "Error updating fee estimate: ${feeEstimateResult.message}")
            }
            Result.Loading -> {}
        }
    }

    fun getCurrentBalance(): BigDecimal {
        return _uiState.value.balance
    }

    fun getBalanceDisplay(): String {
        val network = if (_uiState.value.network.equals("SEPOLIA", ignoreCase = true)) {
            " (Sepolia)"
        } else {
            ""
        }
        return "${_uiState.value.balance.setScale(6, RoundingMode.HALF_UP)} ETH$network"
    }

    fun getCreatedTransaction(): EthereumTransaction? {
        return when (val state = _uiState.value.transactionState) {
            is TransactionState.Created -> state.transaction
            else -> null
        }
    }
}