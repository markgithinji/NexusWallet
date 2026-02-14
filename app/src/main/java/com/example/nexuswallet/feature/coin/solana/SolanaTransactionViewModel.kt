package com.example.nexuswallet.feature.coin.solana

import android.util.Log
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
import com.example.nexuswallet.feature.coin.Result
import java.math.RoundingMode
@HiltViewModel
class SolanaSendViewModel @Inject constructor(
    private val createSolanaTransactionUseCase: CreateSolanaTransactionUseCase,
    private val signSolanaTransactionUseCase: SignSolanaTransactionUseCase,
    private val broadcastSolanaTransactionUseCase: BroadcastSolanaTransactionUseCase,
    private val getSolanaBalanceUseCase: GetSolanaBalanceUseCase,
    private val validateSolanaAddressUseCase: ValidateSolanaAddressUseCase,
    private val requestSolanaAirdropUseCase: RequestSolanaAirdropUseCase,
    private val walletRepository: WalletRepository
) : ViewModel() {

    private val _state = MutableStateFlow(SendState())
    val state: StateFlow<SendState> = _state

    data class SendState(
        val wallet: SolanaWallet? = null,
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
        val airdropMessage: String? = null,
        val createdTransaction: SolanaTransaction? = null
    )

    fun init(walletId: String) {
        viewModelScope.launch {
            val wallet = walletRepository.getWallet(walletId) as? SolanaWallet
            wallet?.let {
                _state.update { it.copy(
                    wallet = wallet,
                    walletAddress = wallet.address
                )}
                loadBalance(wallet.address)
            }
        }
    }

    private suspend fun loadBalance(address: String) {
        val balanceResult = getSolanaBalanceUseCase(address)
        when (balanceResult) {
            is Result.Success -> {
                _state.update { it.copy(
                    balance = balanceResult.data,
                    balanceFormatted = "${balanceResult.data.setScale(4, RoundingMode.HALF_UP)} SOL"
                )}
            }
            is Result.Error -> {
                Log.e("SolanaSendVM", "Error loading balance: ${balanceResult.message}")
                _state.update { it.copy(
                    balance = BigDecimal.ZERO,
                    balanceFormatted = "0 SOL"
                )}
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

            val result = requestSolanaAirdropUseCase(wallet.address)

            when (result) {
                is Result.Success -> {
                    _state.update { it.copy(
                        isLoading = false,
                        airdropSuccess = true,
                        airdropMessage = "1 SOL requested. It may take a moment."
                    )}
                    // Reload balance after airdrop
                    loadBalance(wallet.address)
                }
                is Result.Error -> {
                    _state.update { it.copy(
                        isLoading = false,
                        error = "Airdrop failed: ${result.message}"
                    )}
                }
                Result.Loading -> {}
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

            _state.update { it.copy(isLoading = true, error = null, step = "Creating transaction...") }

            // 1. Create transaction
            val createResult = createSolanaTransactionUseCase(
                walletId = wallet.id,
                toAddress = toAddress,
                amount = amount,
                feeLevel = FeeLevel.NORMAL,
                note = null
            )

            when (createResult) {
                is Result.Success -> {
                    val transaction = createResult.data
                    _state.update { it.copy(
                        step = "Signing transaction...",
                        createdTransaction = transaction
                    )}

                    // 2. Sign transaction
                    val signResult = signSolanaTransactionUseCase(transaction.id)

                    when (signResult) {
                        is Result.Success -> {
                            val signedTransaction = signResult.data
                            _state.update { it.copy(
                                step = "Broadcasting transaction...",
                                createdTransaction = signedTransaction
                            )}

                            // 3. Broadcast transaction
                            val broadcastResult = broadcastSolanaTransactionUseCase(transaction.id)

                            when (broadcastResult) {
                                is Result.Success -> {
                                    val result = broadcastResult.data
                                    if (result.success) {
                                        _state.update { it.copy(step = "Transaction sent!") }
                                        onSuccess(result.hash ?: "unknown")
                                    } else {
                                        _state.update { it.copy(error = result.error ?: "Broadcast failed") }
                                    }
                                }
                                is Result.Error -> {
                                    _state.update { it.copy(error = "Broadcast failed: ${broadcastResult.message}") }
                                }
                                Result.Loading -> {}
                            }
                        }
                        is Result.Error -> {
                            _state.update { it.copy(error = "Signing failed: ${signResult.message}") }
                        }
                        Result.Loading -> {}
                    }
                }
                is Result.Error -> {
                    _state.update { it.copy(error = "Failed to create transaction: ${createResult.message}") }
                }
                Result.Loading -> {}
            }

            _state.update { it.copy(isLoading = false, step = "") }
        }
    }

    private fun validateInputs(toAddress: String, amount: BigDecimal): Boolean {
        if (toAddress.isBlank()) {
            _state.update { it.copy(error = "Please enter a recipient address") }
            return false
        }

        val validationResult = validateSolanaAddressUseCase(toAddress)
        when (validationResult) {
            is Result.Success -> {
                if (!validationResult.data) {
                    _state.update { it.copy(error = "Invalid Solana address format") }
                    return false
                }
            }
            is Result.Error -> {
                _state.update { it.copy(error = "Address validation failed") }
                return false
            }
            Result.Loading -> return false
        }

        if (amount <= BigDecimal.ZERO) {
            _state.update { it.copy(error = "Amount must be greater than 0") }
            return false
        }

        if (toAddress == _state.value.walletAddress) {
            _state.update { it.copy(error = "Cannot send to yourself") }
            return false
        }

        // Check balance
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
                wallet = _state.value.wallet,
                walletAddress = _state.value.walletAddress,
                balance = _state.value.balance,
                balanceFormatted = _state.value.balanceFormatted
            )
        }
    }

    fun getCreatedTransaction(): SolanaTransaction? {
        return _state.value.createdTransaction
    }
}