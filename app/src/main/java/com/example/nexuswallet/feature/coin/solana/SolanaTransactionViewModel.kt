package com.example.nexuswallet.feature.coin.solana

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.nexuswallet.feature.coin.bitcoin.FeeLevel
import com.example.nexuswallet.feature.wallet.data.repository.WalletRepository
import com.example.nexuswallet.feature.wallet.domain.SolanaWallet
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.math.BigDecimal
import javax.inject.Inject
@HiltViewModel
class SolanaSendViewModel @Inject constructor(
    private val createSolanaTransactionUseCase: CreateSolanaTransactionUseCase,
    private val signSolanaTransactionUseCase: SignSolanaTransactionUseCase,
    private val broadcastSolanaTransactionUseCase: BroadcastSolanaTransactionUseCase,
    private val getSolanaRecentBlockhashUseCase: GetSolanaRecentBlockhashUseCase,
    private val getSolanaFeeEstimateUseCase: GetSolanaFeeEstimateUseCase,
    private val validateSolanaAddressUseCase: ValidateSolanaAddressUseCase,
    private val walletRepository: WalletRepository,
    private val solanaBlockchainRepository: SolanaBlockchainRepository
) : ViewModel() {

    private val _state = MutableStateFlow(SendState())
    val state: StateFlow<SendState> = _state

    fun init(walletId: String) {
        viewModelScope.launch {
            val wallet = walletRepository.getWallet(walletId) as? SolanaWallet
            wallet?.let {
                _state.update { it.copy(
                    wallet = wallet,
                    walletAddress = wallet.address
                )}
            }
        }
    }

    fun updateAddress(address: String) {
        _state.update { it.copy(toAddress = address) }
        validateAddress(address)
    }

    private fun validateAddress(address: String) {
        if (address.isNotEmpty()) {
            val isValid = validateSolanaAddressUseCase(address)
            _state.update { it.copy(
                isAddressValid = isValid,
                addressError = if (!isValid) "Invalid Solana address" else null
            )}
        } else {
            _state.update { it.copy(
                isAddressValid = false,
                addressError = null
            )}
        }
    }

    fun updateAmount(amount: String) {
        val amountValue = try {
            BigDecimal(amount)
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
            val wallet = _state.value.wallet ?: return@launch
            _state.update { it.copy(isLoading = true, error = null) }

            try {
                val result = solanaBlockchainRepository.requestAirdrop(wallet.address)
                _state.update { it.copy(
                    isLoading = false,
                    airdropSuccess = true,
                    airdropMessage = "1 SOL requested. It may take a moment."
                )}
            } catch (e: Exception) {
                _state.update { it.copy(
                    isLoading = false,
                    error = "Airdrop failed: ${e.message}"
                )}
            }
        }
    }

    fun send(onSuccess: (String) -> Unit) {
        viewModelScope.launch {
            val wallet = _state.value.wallet ?: return@launch
            val toAddress = _state.value.toAddress
            val amount = _state.value.amountValue

            if (!validateInputs(toAddress, amount)) {
                return@launch
            }

            try {
                _state.update { it.copy(isLoading = true, error = null, step = "Creating transaction...") }

                // 1. Create transaction
                val createResult = createSolanaTransactionUseCase(
                    walletId = wallet.id,
                    toAddress = toAddress,
                    amount = amount,
                    feeLevel = FeeLevel.NORMAL,
                    note = null
                )

                if (createResult.isFailure) {
                    throw createResult.exceptionOrNull() ?: Exception("Failed to create transaction")
                }
                val transaction = createResult.getOrThrow()

                _state.update { it.copy(step = "Signing transaction...") }

                // 2. Sign transaction
                val signResult = signSolanaTransactionUseCase(transaction.id)

                if (signResult.isFailure) {
                    throw signResult.exceptionOrNull() ?: Exception("Failed to sign transaction")
                }

                _state.update { it.copy(step = "Broadcasting transaction...") }

                // 3. Broadcast transaction
                val broadcastResult = broadcastSolanaTransactionUseCase(transaction.id)

                if (broadcastResult.isFailure) {
                    throw broadcastResult.exceptionOrNull() ?: Exception("Failed to broadcast transaction")
                }

                val result = broadcastResult.getOrThrow()

                if (result.success) {
                    _state.update { it.copy(step = "Transaction sent!") }
                    onSuccess(result.hash ?: "unknown")
                } else {
                    _state.update { it.copy(error = result.error ?: "Broadcast failed") }
                }

            } catch (e: Exception) {
                _state.update { it.copy(error = "Failed: ${e.message}") }
            } finally {
                _state.update { it.copy(isLoading = false, step = "") }
            }
        }
    }

    private fun validateInputs(toAddress: String, amount: BigDecimal): Boolean {
        if (toAddress.isBlank()) {
            _state.update { it.copy(error = "Please enter a recipient address") }
            return false
        }

        if (!validateSolanaAddressUseCase(toAddress)) {
            _state.update { it.copy(error = "Invalid Solana address format") }
            return false
        }

        if (amount <= BigDecimal.ZERO) {
            _state.update { it.copy(error = "Amount must be greater than 0") }
            return false
        }

        // Check if sending to self
        if (toAddress == _state.value.walletAddress) {
            _state.update { it.copy(error = "Cannot send to yourself") }
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
        _state.update { SendState(wallet = _state.value.wallet, walletAddress = _state.value.walletAddress) }
    }
}

data class SendState(
    val wallet: SolanaWallet? = null,
    val walletAddress: String = "",
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