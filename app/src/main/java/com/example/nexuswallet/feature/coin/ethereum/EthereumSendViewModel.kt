package com.example.nexuswallet.feature.coin.ethereum

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.nexuswallet.feature.coin.Result
import com.example.nexuswallet.feature.coin.bitcoin.FeeLevel
import com.example.nexuswallet.feature.wallet.data.model.FeeEstimate
import com.example.nexuswallet.feature.wallet.data.repository.WalletRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.math.RoundingMode
import javax.inject.Inject
import kotlin.plus

@HiltViewModel
class EthereumSendViewModel @Inject constructor(
    private val createSendTransactionUseCase: CreateSendTransactionUseCase,
    private val signEthereumTransactionUseCase: SignEthereumTransactionUseCase,
    private val broadcastTransactionUseCase: BroadcastTransactionUseCase,
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
        val amountValue: BigDecimal = BigDecimal.ZERO,
        val note: String = "",
        val feeLevel: FeeLevel = FeeLevel.NORMAL,
        val isLoading: Boolean = false,
        val error: String? = null,
        val feeEstimate: FeeEstimate? = null,
        val balance: BigDecimal = BigDecimal.ZERO,
        val isValid: Boolean = false,
        val validationError: String? = null,
        val network: String = "",
        val step: String = "",
        val createdTransaction: EthereumTransaction? = null
    )

    private val _uiState = MutableStateFlow(SendUiState())
    val uiState: StateFlow<SendUiState> = _uiState.asStateFlow()

    sealed class SendEvent {
        data class ToAddressChanged(val address: String) : SendEvent()
        data class AmountChanged(val amount: String) : SendEvent()
        data class NoteChanged(val note: String) : SendEvent()
        data class FeeLevelChanged(val feeLevel: FeeLevel) : SendEvent()
        object Validate : SendEvent()
        object ClearError : SendEvent()
    }

    fun initialize(walletId: String) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoading = true,
                    error = null
                )
            }

            val wallet = walletRepository.getWallet(walletId)
            if (wallet == null) {
                _uiState.update {
                    it.copy(
                        error = "Wallet not found",
                        isLoading = false
                    )
                }
                return@launch
            }

            val ethereumCoin = wallet.ethereum
            if (ethereumCoin == null) {
                _uiState.update {
                    it.copy(
                        error = "Ethereum not enabled for this wallet",
                        isLoading = false
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
                    isLoading = false
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
                    val amountValue = try {
                        event.amount.toBigDecimalOrNull() ?: BigDecimal.ZERO
                    } catch (e: Exception) {
                        BigDecimal.ZERO
                    }
                    _uiState.update {
                        it.copy(
                            amount = event.amount,
                            amountValue = amountValue
                        )
                    }
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

                SendEvent.ClearError -> {
                    _uiState.update {
                        it.copy(
                            error = null,
                            validationError = null
                        )
                    }
                }
            }
        }
    }

    fun send(onSuccess: (String) -> Unit) {
        viewModelScope.launch {
            val state = _uiState.value
            val walletId = state.walletId
            if (walletId.isEmpty()) {
                Log.e("EthereumSendVM", " send failed: Wallet not loaded")
                _uiState.update { it.copy(error = "Wallet not loaded") }
                return@launch
            }

            val toAddress = state.toAddress
            val amount = state.amountValue

            Log.d("EthereumSendVM", "========== SEND TRANSACTION START ==========")
            Log.d("EthereumSendVM", "Wallet ID: $walletId")
            Log.d("EthereumSendVM", "To Address: $toAddress")
            Log.d("EthereumSendVM", "Amount: $amount ETH")
            Log.d("EthereumSendVM", "Fee Level: ${state.feeLevel}")

            if (!validateInputs()) {
                Log.e("EthereumSendVM", " send failed: Input validation failed")
                return@launch
            }

            _uiState.update {
                it.copy(
                    isLoading = true,
                    error = null,
                    step = "Creating transaction..."
                )
            }
            Log.d("EthereumSendVM", "Step 1: Creating transaction...")

            // 1. Create transaction
            Log.d("EthereumSendVM", "Calling createSendTransactionUseCase...")
            val createResult = createSendTransactionUseCase(
                walletId = walletId,
                toAddress = toAddress,
                amount = amount,
                feeLevel = state.feeLevel,
                note = state.note.takeIf { it.isNotEmpty() }
            )

            when (createResult) {
                is Result.Success -> {
                    val transaction = createResult.data
                    Log.d("EthereumSendVM", " Transaction created: ${transaction.id}")
                    Log.d("EthereumSendVM", "   From: ${transaction.fromAddress}")
                    Log.d("EthereumSendVM", "   Amount: ${transaction.amountEth} ETH")
                    Log.d("EthereumSendVM", "   Fee: ${transaction.feeEth} ETH")

                    _uiState.update {
                        it.copy(
                            step = "Signing transaction...",
                            createdTransaction = transaction
                        )
                    }
                    Log.d("EthereumSendVM", "Step 2: Signing transaction...")

                    // 2. Sign transaction
                    Log.d("EthereumSendVM", "Calling signEthereumTransactionUseCase for: ${transaction.id}")
                    val signResult = signEthereumTransactionUseCase(transaction.id)

                    when (signResult) {
                        is Result.Success -> {
                            val signedTransaction = signResult.data
                            Log.d("EthereumSendVM", " Transaction signed successfully")
                            Log.d("EthereumSendVM", "   Signed Hash: ${signedTransaction.txHash}")

                            _uiState.update {
                                it.copy(
                                    step = "Broadcasting transaction...",
                                    createdTransaction = signedTransaction
                                )
                            }
                            Log.d("EthereumSendVM", "Step 3: Broadcasting transaction...")

                            // 3. Broadcast transaction
                            Log.d("EthereumSendVM", "Calling broadcastTransactionUseCase for: ${transaction.id}")
                            val broadcastResult = broadcastTransactionUseCase(transaction.id)

                            when (broadcastResult) {
                                is Result.Success -> {
                                    val result = broadcastResult.data
                                    Log.d("EthereumSendVM", " Transaction broadcast result:")
                                    Log.d("EthereumSendVM", "   Success: ${result.success}")
                                    Log.d("EthereumSendVM", "   Hash: ${result.hash}")
                                    Log.d("EthereumSendVM", "   Error: ${result.error}")

                                    if (result.success) {
                                        _uiState.update {
                                            it.copy(
                                                step = "Transaction sent!",
                                                isLoading = false
                                            )
                                        }
                                        Log.d("EthereumSendVM", "🎉 Transaction sent successfully!")
                                        Log.d("EthereumSendVM", "========== SEND COMPLETE ==========")
                                        onSuccess(result.hash ?: "unknown")
                                    } else {
                                        Log.e("EthereumSendVM", " Broadcast failed: ${result.error}")
                                        _uiState.update {
                                            it.copy(
                                                error = result.error ?: "Broadcast failed",
                                                isLoading = false
                                            )
                                        }
                                    }
                                }
                                is Result.Error -> {
                                    Log.e("EthereumSendVM", " Broadcast error: ${broadcastResult.message}")
                                    broadcastResult.throwable?.let {
                                        Log.e("EthereumSendVM", "   Exception: ${it.message}")
                                        it.printStackTrace()
                                    }
                                    _uiState.update {
                                        it.copy(
                                            error = "Broadcast failed: ${broadcastResult.message}",
                                            isLoading = false
                                        )
                                    }
                                }
                                Result.Loading -> {
                                    Log.d("EthereumSendVM", " Broadcast loading...")
                                }
                            }
                        }
                        is Result.Error -> {
                            Log.e("EthereumSendVM", " Signing error: ${signResult.message}")
                            signResult.throwable?.let {
                                Log.e("EthereumSendVM", "   Exception: ${it.message}")
                                it.printStackTrace()
                            }
                            _uiState.update {
                                it.copy(
                                    error = "Signing failed: ${signResult.message}",
                                    isLoading = false
                                )
                            }
                        }
                        Result.Loading -> {
                            Log.d("EthereumSendVM", " Signing loading...")
                        }
                    }
                }
                is Result.Error -> {
                    Log.e("EthereumSendVM", " Create transaction error: ${createResult.message}")
                    createResult.throwable?.let {
                        Log.e("EthereumSendVM", "   Exception: ${it.message}")
                        it.printStackTrace()
                    }
                    _uiState.update {
                        it.copy(
                            error = "Failed to create transaction: ${createResult.message}",
                            isLoading = false
                        )
                    }
                }
                Result.Loading -> {
                    Log.d("EthereumSendVM", " Create transaction loading...")
                }
            }
        }
    }

    private suspend fun validateInputs(): Boolean {
        val state = _uiState.value
        val toAddress = state.toAddress
        val amount = state.amountValue

        if (toAddress.isEmpty() || amount == BigDecimal.ZERO) {
            _uiState.update {
                it.copy(
                    isValid = false,
                    validationError = if (toAddress.isEmpty()) "Please enter address" else "Please enter amount"
                )
            }
            return false
        }

        try {
            val addressValidationResult = validateAddressUseCase(toAddress)
            if (!addressValidationResult) {
                _uiState.update {
                    it.copy(
                        isValid = false,
                        validationError = "Invalid Ethereum address"
                    )
                }
                return false
            }

            if (amount <= BigDecimal.ZERO) {
                _uiState.update {
                    it.copy(
                        isValid = false,
                        validationError = "Amount must be greater than 0"
                    )
                }
                return false
            }

            if (toAddress == state.fromAddress) {
                _uiState.update {
                    it.copy(
                        isValid = false,
                        validationError = "Cannot send to yourself"
                    )
                }
                return false
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
                                validationError = "Insufficient balance (including fees)",
                                feeEstimate = feeEstimate
                            )
                        }
                        return false
                    }

                    _uiState.update {
                        it.copy(
                            isValid = true,
                            validationError = null,
                            feeEstimate = feeEstimate
                        )
                    }
                    return true
                }
                is Result.Error -> {
                    _uiState.update {
                        it.copy(
                            isValid = false,
                            validationError = "Failed to get fee estimate: ${feeEstimateResult.message}"
                        )
                    }
                    return false
                }
                Result.Loading -> return false
            }

        } catch (e: Exception) {
            _uiState.update {
                it.copy(
                    isValid = false,
                    validationError = "Validation error: ${e.message}"
                )
            }
            return false
        }
    }

    private suspend fun updateFeeEstimate() {
        val state = _uiState.value
        val feeEstimateResult = getFeeEstimateUseCase(state.feeLevel)
        when (feeEstimateResult) {
            is Result.Success -> {
                _uiState.update { it.copy(feeEstimate = feeEstimateResult.data) }
                validateInputs()
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
        return _uiState.value.createdTransaction
    }

    fun clearError() {
        _uiState.update { it.copy(error = null, validationError = null) }
    }
}