package com.example.nexuswallet.feature.coin.bitcoin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.nexuswallet.feature.coin.Result
import com.example.nexuswallet.feature.logging.Logger
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.BitcoinCoin
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.BitcoinNetwork
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.Wallet
import com.example.nexuswallet.feature.wallet.domain.TransactionStatus
import com.example.nexuswallet.feature.wallet.domain.WalletRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.math.RoundingMode
import javax.inject.Inject
@HiltViewModel
class BitcoinSendViewModel @Inject constructor(
    private val getBitcoinWalletUseCase: GetBitcoinWalletUseCase,
    private val getBitcoinBalanceUseCase: GetBitcoinBalanceUseCase,
    private val getBitcoinFeeEstimateUseCase: GetBitcoinFeeEstimateUseCase,
    private val validateBitcoinTransactionUseCase: ValidateBitcoinTransactionUseCase,
    private val walletRepository: WalletRepository,
    private val logger: Logger
) : ViewModel() {

    data class BtcSendUiState(
        val walletId: String = "",
        val walletName: String = "",
        val walletAddress: String = "",
        val network: BitcoinNetwork = BitcoinNetwork.Testnet,
        val availableNetworks: List<BitcoinNetwork> = emptyList(),
        val balance: BigDecimal = BigDecimal.ZERO,
        val balanceFormatted: String = "0 BTC",
        val toAddress: String = "",
        val amount: String = "",
        val amountValue: BigDecimal = BigDecimal.ZERO,
        val feeLevel: FeeLevel = FeeLevel.NORMAL,
        val feeEstimate: BitcoinFeeEstimate? = null,
        val validationResult: ValidateBitcoinTransactionUseCase.ValidationResult = ValidateBitcoinTransactionUseCase.ValidationResult(isValid = false),
        val isValid: Boolean = false,
        val isLoading: Boolean = false,
        val isInitialized: Boolean = false,
        val error: String? = null
    )

    private val _state = MutableStateFlow(BtcSendUiState())
    val state: StateFlow<BtcSendUiState> = _state.asStateFlow()

    private var wallet: Wallet? = null
    private var bitcoinCoins: Map<BitcoinNetwork, BitcoinCoin> = emptyMap()

    fun handleEvent(event: BitcoinSendEvent) {
        viewModelScope.launch {
            when (event) {
                is BitcoinSendEvent.Initialize -> initialize(event.walletId, event.network)
                is BitcoinSendEvent.UpdateAddress -> updateAddress(event.address)
                is BitcoinSendEvent.UpdateAmount -> updateAmount(event.amount)
                is BitcoinSendEvent.UpdateFeeLevel -> updateFeeLevel(event.feeLevel)
                is BitcoinSendEvent.SwitchNetwork -> switchNetwork(event.network)
                else -> {} // Ignore other events
            }
        }
    }

    private suspend fun initialize(walletId: String, network: BitcoinNetwork?) {
        _state.update { it.copy(
            walletId = walletId,
            isLoading = true,
            error = null,
            isInitialized = false
        ) }

        logger.d("BitcoinSendVM", "init started for walletId: $walletId")

        wallet = walletRepository.getWallet(walletId)

        if (wallet == null) {
            handleError("Wallet not found")
            return
        }

        bitcoinCoins = wallet!!.bitcoinCoins.associateBy { it.network }

        if (bitcoinCoins.isEmpty()) {
            handleError("Bitcoin not enabled for this wallet")
            return
        }

        val availableNetworks = bitcoinCoins.keys.toList()
        val targetNetwork = network ?: availableNetworks.firstOrNull() ?: BitcoinNetwork.Testnet
        val bitcoinCoin = bitcoinCoins[targetNetwork]

        if (bitcoinCoin == null) {
            handleError("Bitcoin not enabled for network $targetNetwork")
            return
        }

        when (val result = getBitcoinWalletUseCase(walletId, targetNetwork)) {
            is Result.Success -> {
                val walletInfo = result.data
                _state.update {
                    it.copy(
                        walletId = walletInfo.walletId,
                        walletName = walletInfo.walletName,
                        walletAddress = walletInfo.walletAddress,
                        network = walletInfo.network,
                        availableNetworks = availableNetworks,
                        isLoading = false,
                        isInitialized = true
                    )
                }

                // Load balance and fee estimate after initialization
                loadBalance(walletInfo.walletAddress, walletInfo.network)
                loadFeeEstimate(FeeLevel.NORMAL)
            }

            is Result.Error -> handleError(result.message)
            else -> {}
        }
    }

    private suspend fun loadBalance(address: String, network: BitcoinNetwork) {
        when (val result = getBitcoinBalanceUseCase(address, network)) {
            is Result.Success -> {
                val balance = result.data
                _state.update {
                    it.copy(
                        balance = balance,
                        balanceFormatted = "${balance.setScale(8, RoundingMode.HALF_UP)} BTC"
                    )
                }
                validateInputs()
            }
            is Result.Error -> handleError("Failed to load balance: ${result.message}")
            else -> {}
        }
    }

    private suspend fun loadFeeEstimate(feeLevel: FeeLevel) {
        when (val result = getBitcoinFeeEstimateUseCase(feeLevel)) {
            is Result.Success -> {
                _state.update { it.copy(feeEstimate = result.data) }
                validateInputs()
            }
            is Result.Error -> handleError("Failed to load fee: ${result.message}")
            else -> {}
        }
    }

    private suspend fun updateAddress(address: String) {
        _state.update { it.copy(toAddress = address) }
        validateInputs()
    }

    private suspend fun updateAmount(amount: String) {
        val amountValue = try {
            amount.toBigDecimalOrNull() ?: BigDecimal.ZERO
        } catch (e: Exception) {
            BigDecimal.ZERO
        }
        _state.update {
            it.copy(
                amount = amount,
                amountValue = amountValue
            )
        }
        validateInputs()
    }

    private suspend fun updateFeeLevel(feeLevel: FeeLevel) {
        _state.update { it.copy(feeLevel = feeLevel) }
        loadFeeEstimate(feeLevel)
    }

    private suspend fun switchNetwork(network: BitcoinNetwork) {
        val bitcoinCoin = bitcoinCoins[network] ?: return
        _state.update {
            it.copy(
                network = network,
                walletAddress = bitcoinCoin.address,
                toAddress = "",
                amount = "",
                amountValue = BigDecimal.ZERO,
                feeEstimate = null,
                error = null
            )
        }
        loadBalance(bitcoinCoin.address, network)
        loadFeeEstimate(FeeLevel.NORMAL)
    }

    private fun validateInputs() {
        val state = _state.value
        val currentWallet = wallet
        val bitcoinCoin = bitcoinCoins[state.network]

        val validationResult = validateBitcoinTransactionUseCase(
            walletId = state.walletId,
            wallet = currentWallet,
            toAddress = state.toAddress,
            amount = state.amountValue,
            network = state.network,
            balance = state.balance,
            feeEstimate = state.feeEstimate
        )

        _state.update { currentState ->
            currentState.copy(
                validationResult = validationResult,
                isValid = validationResult.isValid,
                error = validationResult.addressError
                    ?: validationResult.amountError
                    ?: validationResult.balanceError
                    ?: validationResult.selfSendError
                    ?: if (!validationResult.isValid) "Invalid transaction" else null
            )
        }

        logger.d("BitcoinSendVM", "Validation result: $validationResult")
    }

    private suspend fun handleError(message: String) {
        logger.e("BitcoinSendVM", message)
        _state.update { it.copy(isLoading = false, error = message) }
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }
}