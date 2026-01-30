package com.example.nexuswallet.feature.wallet.ui

import com.example.nexuswallet.feature.wallet.data.model.SendTransaction
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.nexuswallet.NexusWalletApplication
import com.example.nexuswallet.feature.wallet.data.model.FeeEstimate
import com.example.nexuswallet.feature.wallet.data.model.FeeLevel
import com.example.nexuswallet.feature.wallet.data.model.ValidationResult
import com.example.nexuswallet.feature.wallet.data.repository.BlockchainRepository
import com.example.nexuswallet.feature.wallet.data.repository.TransactionRepository
import com.example.nexuswallet.feature.wallet.data.repository.WalletRepository
import com.example.nexuswallet.feature.wallet.domain.BitcoinWallet
import com.example.nexuswallet.feature.wallet.domain.ChainType
import com.example.nexuswallet.feature.wallet.domain.CryptoWallet
import com.example.nexuswallet.feature.wallet.domain.EthereumWallet
import com.example.nexuswallet.feature.wallet.domain.WalletType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.math.BigDecimal
import javax.inject.Inject


@HiltViewModel
class SendViewModel @Inject constructor(
    private val transactionRepository: TransactionRepository,
    private val walletRepository: WalletRepository,
    private val blockchainRepository: BlockchainRepository
) : ViewModel() {

    data class SendUiState(
        val walletId: String = "",
        val walletType: WalletType = WalletType.BITCOIN,
        val fromAddress: String = "",
        val toAddress: String = "",
        val amount: String = "",
        val amountDecimal: String = "",
        val note: String = "",
        val feeLevel: FeeLevel = FeeLevel.NORMAL,
        val isLoading: Boolean = false,
        val error: String? = null,
        val feeEstimate: FeeEstimate? = null,
        val balance: BigDecimal = BigDecimal.ZERO,
        val isValid: Boolean = false,
        val validationError: String? = null
    )

    private val _uiState = MutableStateFlow(SendUiState())
    val uiState: StateFlow<SendUiState> = _uiState.asStateFlow()

    private val _createdTransaction = MutableStateFlow<SendTransaction?>(null)
    val createdTransaction: StateFlow<SendTransaction?> = _createdTransaction.asStateFlow()

    sealed class SendEvent {
        data class ToAddressChanged(val address: String) : SendEvent()
        data class AmountChanged(val amount: String) : SendEvent()
        data class NoteChanged(val note: String) : SendEvent()
        data class FeeLevelChanged(val feeLevel: FeeLevel) : SendEvent()
        object Validate : SendEvent()
        object CreateTransaction : SendEvent()
        object ClearError : SendEvent()
    }

    fun initialize(walletId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            try {
                val wallet = walletRepository.getWallet(walletId)
                if (wallet != null) {
                    // Get REAL balance from wallet
                    val walletBalance = walletRepository.getWalletBalance(walletId)

                    // Convert to BigDecimal for calculations
                    val balanceValue = if (walletBalance != null) {
                        // Use the native balance decimal from database
                        walletBalance.nativeBalanceDecimal.toBigDecimalOrNull() ?: BigDecimal.ZERO
                    } else {
                        // If no balance in database, try blockchain
                        when (wallet) {
                            is BitcoinWallet -> blockchainRepository.getBitcoinBalance(wallet.address)
                            is EthereumWallet -> blockchainRepository.getEthereumBalance(wallet.address)
                            else -> BigDecimal.ZERO
                        }
                    }

                    // Get fee estimate
                    val chain = when (wallet) {
                        is BitcoinWallet -> ChainType.BITCOIN
                        is EthereumWallet -> ChainType.ETHEREUM
                        else -> ChainType.ETHEREUM
                    }

                    val feeEstimate = transactionRepository.getFeeEstimate(chain, FeeLevel.NORMAL)

                    _uiState.update {
                        it.copy(
                            walletId = walletId,
                            walletType = wallet.walletType,
                            fromAddress = wallet.address,
                            balance = balanceValue,
                            feeEstimate = feeEstimate,
                            isLoading = false
                        )
                    }

                    // Validate current state
                    validateInputs()
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        error = "Failed to load wallet: ${e.message}",
                        isLoading = false
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
                    _uiState.update {
                        it.copy(
                            amount = event.amount,
                            amountDecimal = if (event.amount.isNotEmpty()) {
                                try {
                                    BigDecimal(event.amount).toPlainString()
                                } catch (e: Exception) {
                                    ""
                                }
                            } else {
                                ""
                            }
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

                SendEvent.CreateTransaction -> {
                    createTransaction()
                }

                SendEvent.ClearError -> {
                    _uiState.update { it.copy(error = null) }
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
            // Validate address
            val chain = when (state.walletType) {
                WalletType.BITCOIN -> ChainType.BITCOIN
                WalletType.ETHEREUM -> ChainType.ETHEREUM
                else -> ChainType.ETHEREUM
            }

            val isAddressValid = transactionRepository.validateAddress(state.toAddress, chain)
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

            // Check if amount + fee <= balance
            val feeEstimate = transactionRepository.getFeeEstimate(chain, state.feeLevel)
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
            else -> ChainType.ETHEREUM
        }

        try {
            val feeEstimate = transactionRepository.getFeeEstimate(chain, state.feeLevel)
            _uiState.update { it.copy(feeEstimate = feeEstimate) }
        } catch (e: Exception) {
            // Keep existing fee estimate
        }
    }

    private suspend fun createTransaction() {
        val state = _uiState.value

        _uiState.update { it.copy(isLoading = true, error = null) }

        try {
            val amount = BigDecimal(state.amount)
            val result = transactionRepository.createSendTransaction(
                walletId = state.walletId,
                toAddress = state.toAddress,
                amount = amount,
                feeLevel = state.feeLevel,
                note = state.note.takeIf { it.isNotEmpty() }
            )

            if (result.isSuccess) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = null
                    )
                }
                // Success - navigate to review screen
                // This will be handled by the UI
            } else {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = result.exceptionOrNull()?.message ?: "Failed to create transaction"
                    )
                }
            }
        } catch (e: Exception) {
            _uiState.update {
                it.copy(
                    isLoading = false,
                    error = "Error: ${e.message}"
                )
            }
        }
    }

    fun getCreatedTransaction(): SendTransaction? {
        // This would be called after successful creation
        // For now, return null - UI will handle navigation
        return null
    }
}