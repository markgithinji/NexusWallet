package com.example.nexuswallet.feature.coin.solana

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.nexuswallet.feature.coin.bitcoin.FeeLevel
import com.example.nexuswallet.feature.wallet.data.repository.WalletRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.math.BigDecimal
import javax.inject.Inject
import com.example.nexuswallet.feature.coin.Result
import kotlinx.coroutines.flow.asStateFlow
import java.math.RoundingMode
@HiltViewModel
class SolanaSendViewModel @Inject constructor(
    private val sendSolanaUseCase: SendSolanaUseCase,
    private val getSolanaBalanceUseCase: GetSolanaBalanceUseCase,
    private val getSolanaFeeEstimateUseCase: GetSolanaFeeEstimateUseCase,
    private val validateSolanaAddressUseCase: ValidateSolanaAddressUseCase,
    private val requestSolanaAirdropUseCase: RequestSolanaAirdropUseCase,
    private val walletRepository: WalletRepository
) : ViewModel() {

    private val _state = MutableStateFlow(SendState())
    val state: StateFlow<SendState> = _state.asStateFlow()

    data class SendState(
        val walletId: String = "",
        val walletName: String = "",
        val walletAddress: String = "",
        val balance: BigDecimal = BigDecimal.ZERO,
        val balanceFormatted: String = "0 SOL",
        val toAddress: String = "",
        val isAddressValid: Boolean = false,
        val addressError: String? = null,
        val amount: String = "",
        val amountValue: BigDecimal = BigDecimal.ZERO,
        val feeLevel: FeeLevel = FeeLevel.NORMAL,
        val feeEstimate: SolanaFeeEstimate? = null,
        val isLoading: Boolean = false,
        val step: String = "",
        val error: String? = null,
        val airdropSuccess: Boolean = false,
        val airdropMessage: String? = null
    )

    sealed class SendEvent {
        data class ToAddressChanged(val address: String) : SendEvent()
        data class AmountChanged(val amount: String) : SendEvent()
        data class FeeLevelChanged(val feeLevel: FeeLevel) : SendEvent()
        object Validate : SendEvent()
        object ClearError : SendEvent()
        object ClearAirdropMessage : SendEvent()
    }

    fun init(walletId: String) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }

            val wallet = walletRepository.getWallet(walletId)
            if (wallet == null) {
                _state.update { it.copy(error = "Wallet not found", isLoading = false) }
                return@launch
            }

            val solanaCoin = wallet.solana
            if (solanaCoin == null) {
                _state.update { it.copy(error = "Solana not enabled", isLoading = false) }
                return@launch
            }

            _state.update {
                it.copy(
                    walletId = wallet.id,
                    walletName = wallet.name,
                    walletAddress = solanaCoin.address
                )
            }

            // Load balance and fee estimate in parallel
            loadBalance(solanaCoin.address)
            loadFeeEstimate()
        }
    }

    private suspend fun loadBalance(address: String) {
        val balanceResult = getSolanaBalanceUseCase(address)
        when (balanceResult) {
            is Result.Success -> {
                val balance = balanceResult.data
                _state.update {
                    it.copy(
                        balance = balance,
                        balanceFormatted = "${balance.setScale(4, RoundingMode.HALF_UP)} SOL",
                        isLoading = false
                    )
                }
                Log.d("SolanaSendVM", "Balance loaded: $balance SOL")
            }
            is Result.Error -> {
                Log.e("SolanaSendVM", "Error loading balance: ${balanceResult.message}")
                _state.update {
                    it.copy(
                        balance = BigDecimal.ZERO,
                        balanceFormatted = "0 SOL",
                        error = "Failed to load balance: ${balanceResult.message}",
                        isLoading = false
                    )
                }
            }
            Result.Loading -> {}
        }
    }

    private suspend fun loadFeeEstimate() {
        val feeResult = getSolanaFeeEstimateUseCase(_state.value.feeLevel)
        when (feeResult) {
            is Result.Success -> {
                _state.update { it.copy(feeEstimate = feeResult.data) }
                Log.d("SolanaSendVM", "Fee estimate loaded: ${feeResult.data.feeSol} SOL")
            }
            is Result.Error -> {
                Log.e("SolanaSendVM", "Error loading fee: ${feeResult.message}")
            }
            Result.Loading -> {}
        }
    }

    fun onEvent(event: SendEvent) {
        when (event) {
            is SendEvent.ToAddressChanged -> {
                _state.update { it.copy(toAddress = event.address) }
                validateAddress(event.address)
                validateInputs()
            }
            is SendEvent.AmountChanged -> {
                val amountValue = event.amount.toBigDecimalOrNull() ?: BigDecimal.ZERO
                _state.update {
                    it.copy(
                        amount = event.amount,
                        amountValue = amountValue
                    )
                }
                validateInputs()
            }
            is SendEvent.FeeLevelChanged -> {
                _state.update { it.copy(feeLevel = event.feeLevel) }
                viewModelScope.launch {
                    loadFeeEstimate()
                    validateInputs()
                }
            }
            SendEvent.Validate -> validateInputs()
            SendEvent.ClearError -> clearError()
            SendEvent.ClearAirdropMessage -> clearAirdropMessage()
        }
    }

    private fun validateInputs(): Boolean {
        val currentState = _state.value
        val toAddress = currentState.toAddress
        val amount = currentState.amountValue

        // Reset error state first
        var errorMessage: String? = null
        var isValid = true

        if (toAddress.isBlank()) {
            errorMessage = "Please enter a recipient address"
            isValid = false
        } else if (!currentState.isAddressValid) {
            errorMessage = "Invalid Solana address format"
            isValid = false
        } else if (amount <= BigDecimal.ZERO) {
            errorMessage = "Amount must be greater than 0"
            isValid = false
        } else if (toAddress == currentState.walletAddress) {
            errorMessage = "Cannot send to yourself"
            isValid = false
        } else if (amount > currentState.balance) {
            errorMessage = "Insufficient balance"
            isValid = false
        } else {
            // Check balance including fees
            val fee = currentState.feeEstimate?.feeSol?.toBigDecimalOrNull() ?: BigDecimal("0.000005")
            val totalRequired = amount + fee
            if (totalRequired > currentState.balance) {
                errorMessage = "Insufficient balance (including fees)"
                isValid = false
            }
        }

        _state.update { it.copy(error = errorMessage) }
        return isValid
    }

    private fun validateAddress(address: String) {
        if (address.isNotEmpty()) {
            val validationResult = validateSolanaAddressUseCase(address)
            when (validationResult) {
                is Result.Success -> {
                    _state.update { it.copy(
                        isAddressValid = validationResult.data,
                        addressError = if (!validationResult.data) "Invalid Solana address" else null
                    )}
                }
                is Result.Error -> {
                    _state.update { it.copy(
                        isAddressValid = false,
                        addressError = "Address validation failed"
                    )}
                }
                Result.Loading -> {}
            }
        } else {
            _state.update { it.copy(
                isAddressValid = false,
                addressError = null
            )}
        }
    }

    fun updateAmount(amount: String) {
        val amountValue = try {
            amount.toBigDecimalOrNull() ?: BigDecimal.ZERO
        } catch (e: Exception) {
            BigDecimal.ZERO
        }
        _state.update { it.copy(
            amount = amount,
            amountValue = amountValue
        )}
    }

    fun requestAirdrop() {
        viewModelScope.launch {
            if (_state.value.walletAddress.isEmpty()) {
                _state.update { it.copy(error = "Wallet not loaded") }
                return@launch
            }

            _state.update { it.copy(isLoading = true, error = null, step = "Requesting airdrop...") }

            val result = requestSolanaAirdropUseCase(_state.value.walletAddress)

            when (result) {
                is Result.Success -> {
                    _state.update { it.copy(
                        isLoading = false,
                        airdropSuccess = true,
                        airdropMessage = "1 SOL requested. It may take a moment.",
                        step = ""
                    )}
                    // Reload balance after airdrop
                    loadBalance(_state.value.walletAddress)
                }
                is Result.Error -> {
                    _state.update { it.copy(
                        isLoading = false,
                        error = "Airdrop failed: ${result.message}",
                        step = ""
                    )}
                }
                Result.Loading -> {}
            }
        }
    }

    fun send(onSuccess: (String) -> Unit) {
        viewModelScope.launch {
            val state = _state.value
            if (state.walletId.isEmpty()) {
                _state.update { it.copy(error = "Wallet not loaded") }
                return@launch
            }

            val toAddress = state.toAddress
            val amount = state.amountValue

            if (!validateInputs(toAddress, amount)) {
                return@launch
            }

            _state.update { it.copy(isLoading = true, error = null, step = "Sending...") }

            val result = sendSolanaUseCase(
                walletId = state.walletId,
                toAddress = toAddress,
                amount = amount,
                feeLevel = state.feeLevel, // Pass the selected fee level
                note = null
            )

            when (result) {
                is Result.Success -> {
                    val sendResult = result.data
                    if (sendResult.success) {
                        _state.update { it.copy(isLoading = false, step = "Sent!") }
                        onSuccess(sendResult.txHash)
                    } else {
                        _state.update { it.copy(
                            isLoading = false,
                            error = sendResult.error ?: "Send failed",
                            step = ""
                        ) }
                    }
                }
                is Result.Error -> {
                    _state.update { it.copy(
                        isLoading = false,
                        error = result.message,
                        step = ""
                    ) }
                }
                Result.Loading -> {}
            }
        }
    }

    private fun validateInputs(toAddress: String, amount: BigDecimal): Boolean {
        if (toAddress.isBlank()) {
            _state.update { it.copy(error = "Please enter a recipient address") }
            return false
        }

        if (!_state.value.isAddressValid) {
            _state.update { it.copy(error = "Invalid Solana address format") }
            return false
        }

        if (amount <= BigDecimal.ZERO) {
            _state.update { it.copy(error = "Amount must be greater than 0") }
            return false
        }

        if (toAddress == _state.value.walletAddress) {
            _state.update { it.copy(error = "Cannot send to yourself") }
            return false
        }

        // Check if amount exceeds balance
        if (amount > _state.value.balance) {
            _state.update { it.copy(error = "Insufficient balance") }
            return false
        }

        // Optionally check if amount + fee exceeds balance
        val fee = _state.value.feeEstimate?.feeSol?.toBigDecimalOrNull() ?: BigDecimal("0.000005")
        val totalRequired = amount + fee
        if (totalRequired > _state.value.balance) {
            _state.update { it.copy(error = "Insufficient balance (including fees)") }
            return false
        }

        return true
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }

    fun clearAirdropMessage() {
        _state.update { it.copy(airdropSuccess = false, airdropMessage = null) }
    }

    fun resetState() {
        _state.update {
            SendState(
                walletId = _state.value.walletId,
                walletName = _state.value.walletName,
                walletAddress = _state.value.walletAddress,
                balance = _state.value.balance,
                balanceFormatted = _state.value.balanceFormatted
            )
        }
    }
}