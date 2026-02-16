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
        val isLoading: Boolean = false,
        val step: String = "",
        val error: String? = null,
        val airdropSuccess: Boolean = false,
        val airdropMessage: String? = null
    )

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
            loadBalance(solanaCoin.address)
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

    fun updateAddress(address: String) {
        _state.update { it.copy(toAddress = address) }
        validateAddress(address)
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
                feeLevel = FeeLevel.NORMAL,
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

        if (amount > _state.value.balance) {
            _state.update { it.copy(error = "Insufficient balance") }
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