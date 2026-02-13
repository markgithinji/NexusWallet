package com.example.nexuswallet.feature.wallet.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.nexuswallet.feature.coin.ethereum.BroadcastTransactionUseCase
import com.example.nexuswallet.feature.wallet.data.model.BroadcastResult
import com.example.nexuswallet.feature.wallet.data.model.SendTransaction
import com.example.nexuswallet.feature.coin.ethereum.EthereumBlockchainRepository
import com.example.nexuswallet.feature.coin.ethereum.GetTransactionUseCase
import com.example.nexuswallet.feature.coin.ethereum.SignEthereumTransactionUseCase
import com.example.nexuswallet.feature.wallet.domain.ChainType
import com.example.nexuswallet.feature.wallet.domain.EthereumNetwork
import com.example.nexuswallet.feature.wallet.domain.TransactionStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.example.nexuswallet.feature.coin.Result
import com.example.nexuswallet.feature.coin.bitcoin.BitcoinBlockchainRepository
import com.example.nexuswallet.feature.coin.bitcoin.BroadcastBitcoinTransactionUseCase
import com.example.nexuswallet.feature.coin.bitcoin.SignBitcoinTransactionUseCase
import com.example.nexuswallet.feature.coin.solana.BroadcastSolanaTransactionUseCase
import com.example.nexuswallet.feature.coin.solana.SignSolanaTransactionUseCase
import com.example.nexuswallet.feature.coin.solana.SolanaBlockchainRepository

@HiltViewModel
class TransactionReviewViewModel @Inject constructor(
    private val getTransactionUseCase: GetTransactionUseCase,
    // Ethereum
    private val signEthereumTransactionUseCase: SignEthereumTransactionUseCase,
    private val broadcastTransactionUseCase: BroadcastTransactionUseCase,
    private val ethereumBlockchainRepository: EthereumBlockchainRepository,
    // Solana
    private val signSolanaTransactionUseCase: SignSolanaTransactionUseCase,
    private val broadcastSolanaTransactionUseCase: BroadcastSolanaTransactionUseCase,
    // Bitcoin
    private val signBitcoinTransactionUseCase: SignBitcoinTransactionUseCase,
    private val broadcastBitcoinTransactionUseCase: BroadcastBitcoinTransactionUseCase,
) : ViewModel() {

    data class ReviewUiState(
        val transaction: SendTransaction? = null,
        val isLoading: Boolean = false,
        val error: String? = null,
        val currentStep: TransactionStep = TransactionStep.LOADING,
        val broadcastResult: BroadcastResult? = null,
        val isApproved: Boolean = false,
        val transactionConfirmed: Boolean = false,
        val chainType: ChainType = ChainType.ETHEREUM
    )

    private val _uiState = MutableStateFlow(ReviewUiState())
    val uiState: StateFlow<ReviewUiState> = _uiState.asStateFlow()

    sealed class TransactionStep {
        data object LOADING : TransactionStep()
        data object REVIEWING : TransactionStep()
        data object APPROVING : TransactionStep()
        data object SIGNING : TransactionStep()
        data object BROADCASTING : TransactionStep()
        data object SUCCESS : TransactionStep()
        data class ERROR(val message: String) : TransactionStep()
        data object CHECKING_STATUS : TransactionStep()
    }

    sealed class ReviewEvent {
        object ApproveTransaction : ReviewEvent()
        object ClearError : ReviewEvent()
    }

    fun initialize(transactionId: String) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoading = true,
                    currentStep = TransactionStep.LOADING,
                    isApproved = false
                )
            }

            val transactionResult = getTransactionUseCase(transactionId)

            when (transactionResult) {
                is Result.Success -> {
                    _uiState.update {
                        it.copy(
                            transaction = transactionResult.data,
                            chainType = transactionResult.data.chain,
                            isLoading = false,
                            currentStep = TransactionStep.REVIEWING
                        )
                    }
                }
                is Result.Error -> {
                    _uiState.update {
                        it.copy(
                            error = "Failed to load transaction: ${transactionResult.message}",
                            isLoading = false,
                            currentStep = TransactionStep.ERROR("Failed to load transaction")
                        )
                    }
                }
                Result.Loading -> {}
            }
        }
    }

    private suspend fun approveTransaction() {
        val transaction = _uiState.value.transaction ?: return

        _uiState.update {
            it.copy(
                currentStep = TransactionStep.APPROVING,
                isApproved = true,
                error = null
            )
        }

        when (transaction.chain) {
            ChainType.ETHEREUM, ChainType.ETHEREUM_SEPOLIA -> {
                autoSignAndBroadcastEthereum(transaction.id)
            }
            ChainType.SOLANA -> {
                autoSignAndBroadcastSolana(transaction.id)
            }
            ChainType.BITCOIN -> {
                autoSignAndBroadcastBitcoin(transaction.id)
            }
            else -> {
                _uiState.update {
                    it.copy(
                        error = "Unsupported chain: ${transaction.chain}",
                        currentStep = TransactionStep.ERROR("Unsupported chain")
                    )
                }
            }
        }
    }

    // ===== ETHEREUM =====
    private suspend fun autoSignAndBroadcastEthereum(transactionId: String) {
        Log.d("TransactionReview", "START Ethereum autoSignAndBroadcast for: $transactionId")

        _uiState.update { it.copy(currentStep = TransactionStep.SIGNING) }

        val signResult = signEthereumTransactionUseCase(transactionId)

        when (signResult) {
            is Result.Success -> {
                val updatedTransactionResult = getTransactionUseCase(transactionId)

                when (updatedTransactionResult) {
                    is Result.Success -> {
                        _uiState.update {
                            it.copy(
                                transaction = updatedTransactionResult.data,
                                currentStep = TransactionStep.BROADCASTING
                            )
                        }

                        val broadcastResult = broadcastTransactionUseCase(transactionId)

                        when (broadcastResult) {
                            is Result.Success -> {
                                val result = broadcastResult.data
                                _uiState.update {
                                    it.copy(
                                        broadcastResult = result,
                                        currentStep = TransactionStep.CHECKING_STATUS
                                    )
                                }
                                checkEthereumTransactionStatus(result.hash, transactionId)
                            }
                            is Result.Error -> {
                                _uiState.update {
                                    it.copy(
                                        error = "Broadcast failed: ${broadcastResult.message}",
                                        currentStep = TransactionStep.ERROR("Broadcast failed")
                                    )
                                }
                            }
                            Result.Loading -> {}
                        }
                    }
                    is Result.Error -> {
                        _uiState.update {
                            it.copy(
                                error = "Failed to get updated transaction: ${updatedTransactionResult.message}",
                                currentStep = TransactionStep.ERROR("Failed to update transaction")
                            )
                        }
                    }
                    Result.Loading -> {}
                }
            }
            is Result.Error -> {
                _uiState.update {
                    it.copy(
                        error = "Signing failed: ${signResult.message}",
                        currentStep = TransactionStep.ERROR("Signing failed")
                    )
                }
            }
            Result.Loading -> {}
        }
    }

    private fun checkEthereumTransactionStatus(txHash: String?, transactionId: String) {
        viewModelScope.launch {
            if (txHash == null) {
                _uiState.update {
                    it.copy(
                        currentStep = TransactionStep.SUCCESS,
                        transactionConfirmed = false
                    )
                }
                return@launch
            }

            val transactionResult = getTransactionUseCase(transactionId)
            val network = when (transactionResult) {
                is Result.Success -> when (transactionResult.data.chain) {
                    ChainType.ETHEREUM_SEPOLIA -> EthereumNetwork.SEPOLIA
                    else -> EthereumNetwork.MAINNET
                }
                else -> EthereumNetwork.SEPOLIA
            }

            for (attempt in 1..5) {
                delay(3000)
                val statusResult = ethereumBlockchainRepository.checkTransactionStatus(txHash, network)

                when (statusResult) {
                    is Result.Success -> {
                        when (statusResult.data) {
                            TransactionStatus.SUCCESS -> {
                                _uiState.update {
                                    it.copy(
                                        currentStep = TransactionStep.SUCCESS,
                                        transactionConfirmed = true
                                    )
                                }
                                return@launch
                            }
                            TransactionStatus.FAILED -> {
                                _uiState.update {
                                    it.copy(
                                        currentStep = TransactionStep.ERROR("Transaction failed"),
                                        error = "Transaction failed on chain"
                                    )
                                }
                                return@launch
                            }
                            else -> {}
                        }
                    }
                    else -> {}
                }
            }

            _uiState.update {
                it.copy(
                    currentStep = TransactionStep.SUCCESS,
                    transactionConfirmed = false
                )
            }
        }
    }

    // ===== SOLANA =====
    private suspend fun autoSignAndBroadcastSolana(transactionId: String) {
        Log.d("TransactionReview", "START Solana autoSignAndBroadcast for: $transactionId")

        _uiState.update { it.copy(currentStep = TransactionStep.SIGNING) }

        val signResult = signSolanaTransactionUseCase(transactionId)

        when (signResult) {
            is Result.Success -> {
                val updatedTransactionResult = getTransactionUseCase(transactionId)

                when (updatedTransactionResult) {
                    is Result.Success -> {
                        _uiState.update {
                            it.copy(
                                transaction = updatedTransactionResult.data,
                                currentStep = TransactionStep.BROADCASTING
                            )
                        }

                        val broadcastResult = broadcastSolanaTransactionUseCase(transactionId)

                        when (broadcastResult) {
                            is Result.Success -> {
                                val result = broadcastResult.data
                                _uiState.update {
                                    it.copy(
                                        broadcastResult = result,
                                        currentStep = TransactionStep.SUCCESS,
                                        transactionConfirmed = result.success
                                    )
                                }
                            }
                            is Result.Error -> {
                                _uiState.update {
                                    it.copy(
                                        error = "Broadcast failed: ${broadcastResult.message}",
                                        currentStep = TransactionStep.ERROR("Broadcast failed")
                                    )
                                }
                            }
                            Result.Loading -> {}
                        }
                    }
                    is Result.Error -> {
                        _uiState.update {
                            it.copy(
                                error = "Failed to get updated transaction: ${updatedTransactionResult.message}",
                                currentStep = TransactionStep.ERROR("Failed to update transaction")
                            )
                        }
                    }
                    Result.Loading -> {}
                }
            }
            is Result.Error -> {
                _uiState.update {
                    it.copy(
                        error = "Signing failed: ${signResult.message}",
                        currentStep = TransactionStep.ERROR("Signing failed")
                    )
                }
            }
            Result.Loading -> {}
        }
    }

    // ===== BITCOIN =====
    private suspend fun autoSignAndBroadcastBitcoin(transactionId: String) {
        Log.d("TransactionReview", "START Bitcoin autoSignAndBroadcast for: $transactionId")

        _uiState.update { it.copy(currentStep = TransactionStep.SIGNING) }

        val signResult = signBitcoinTransactionUseCase(transactionId)

        when (signResult) {
            is Result.Success -> {
                val updatedTransactionResult = getTransactionUseCase(transactionId)

                when (updatedTransactionResult) {
                    is Result.Success -> {
                        _uiState.update {
                            it.copy(
                                transaction = updatedTransactionResult.data,
                                currentStep = TransactionStep.BROADCASTING
                            )
                        }

                        val broadcastResult = broadcastBitcoinTransactionUseCase(transactionId)

                        when (broadcastResult) {
                            is Result.Success -> {
                                val result = broadcastResult.data
                                _uiState.update {
                                    it.copy(
                                        broadcastResult = result,
                                        currentStep = TransactionStep.SUCCESS,
                                        transactionConfirmed = result.success
                                    )
                                }
                            }
                            is Result.Error -> {
                                _uiState.update {
                                    it.copy(
                                        error = "Broadcast failed: ${broadcastResult.message}",
                                        currentStep = TransactionStep.ERROR("Broadcast failed")
                                    )
                                }
                            }
                            Result.Loading -> {}
                        }
                    }
                    is Result.Error -> {
                        _uiState.update {
                            it.copy(
                                error = "Failed to get updated transaction: ${updatedTransactionResult.message}",
                                currentStep = TransactionStep.ERROR("Failed to update transaction")
                            )
                        }
                    }
                    Result.Loading -> {}
                }
            }
            is Result.Error -> {
                _uiState.update {
                    it.copy(
                        error = "Signing failed: ${signResult.message}",
                        currentStep = TransactionStep.ERROR("Signing failed")
                    )
                }
            }
            Result.Loading -> {}
        }
    }

    fun onEvent(event: ReviewEvent) {
        viewModelScope.launch {
            when (event) {
                ReviewEvent.ApproveTransaction -> approveTransaction()
                ReviewEvent.ClearError -> {
                    _uiState.update { it.copy(error = null) }
                }
            }
        }
    }
}