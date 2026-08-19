package com.example.nexuswallet.feature.bitcoin.ui.send

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.nexuswallet.feature.bitcoin.domain.repository.BitcoinBlockchainRepository
import com.example.nexuswallet.feature.bitcoin.domain.usecase.CalculateBitcoinMaxAmountUseCase
import com.example.nexuswallet.feature.bitcoin.domain.usecase.GetBitcoinBalanceUseCase
import com.example.nexuswallet.feature.bitcoin.domain.usecase.GetBitcoinFeeEstimateUseCase
import com.example.nexuswallet.feature.bitcoin.domain.usecase.GetBitcoinWalletUseCase
import com.example.nexuswallet.feature.bitcoin.domain.usecase.SelectBitcoinUtxosUseCase
import com.example.nexuswallet.feature.bitcoin.domain.usecase.ValidateBitcoinTransactionUseCase
import com.example.nexuswallet.feature.bitcoin.util.BitcoinConstants
import com.example.nexuswallet.feature.bitcoin.util.BitcoinConstants.DEFAULT_INPUT_COUNT
import com.example.nexuswallet.feature.bitcoin.util.BitcoinConstants.DEFAULT_OUTPUT_COUNT
import com.example.nexuswallet.feature.core.domain.model.FeeLevel
import com.example.nexuswallet.feature.core.util.Result
import com.example.nexuswallet.feature.core.util.toSatoshis
import com.example.nexuswallet.feature.market.domain.repository.MarketRepository
import com.example.nexuswallet.feature.settings.domain.model.SupportedCurrency
import com.example.nexuswallet.feature.wallet.domain.model.BitcoinCoin
import com.example.nexuswallet.feature.wallet.domain.model.BitcoinNetwork
import com.example.nexuswallet.feature.wallet.domain.model.Wallet
import com.example.nexuswallet.feature.wallet.domain.repository.WalletRepository
import com.example.nexuswallet.feature.wallet.domain.usecase.GetAddressBookEntriesUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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
    private val selectBitcoinUtxosUseCase: SelectBitcoinUtxosUseCase,
    private val validateBitcoinTransactionUseCase: ValidateBitcoinTransactionUseCase,
    private val calculateMaxAmountUseCase: CalculateBitcoinMaxAmountUseCase,
    private val walletRepository: WalletRepository,
    private val bitcoinBlockchainRepository: BitcoinBlockchainRepository,
    private val marketRepository: MarketRepository,
    private val getAddressBookEntriesUseCase: GetAddressBookEntriesUseCase
) : ViewModel() {

    private val _state = MutableStateFlow(BtcSendUiState())
    val state: StateFlow<BtcSendUiState> = _state.asStateFlow()

    private var wallet: Wallet? = null
    private var bitcoinCoins: Map<BitcoinNetwork, BitcoinCoin> = emptyMap()
    private var currentCoin: BitcoinCoin? = null
    private var feeJob: Job? = null

    fun handleEvent(event: BitcoinSendEvent) {
        viewModelScope.launch {
            when (event) {
                is BitcoinSendEvent.Initialize -> initialize(event.walletId, event.coin)
                is BitcoinSendEvent.UpdateAddress -> updateAddress(event.address)
                is BitcoinSendEvent.UpdateAmount -> updateAmount(event.amount)
                is BitcoinSendEvent.UpdateFeeLevel -> updateFeeLevel(event.feeLevel)
                is BitcoinSendEvent.SwitchNetwork -> switchNetwork(event.network)
                is BitcoinSendEvent.ToggleFiatMode -> toggleFiatMode(event.isFiatMode)
                BitcoinSendEvent.UseMax -> useMax()
            }
        }
    }

    init {
        observeAddressBook()
    }

    private fun observeAddressBook() {
        viewModelScope.launch {
            getAddressBookEntriesUseCase().collect { entries ->
                _state.update { it.copy(addressBookEntries = entries) }
            }
        }
    }

    private suspend fun initialize(walletId: String, coin: BitcoinCoin?) {
        if (_state.value.isInitialized && _state.value.walletId == walletId) {
            // Already initialized, just refresh data in background without resetting UI state
            val walletAddress = _state.value.walletAddress
            val network = _state.value.network
            val xpub = _state.value.coin?.xpub
            loadBalance(walletAddress, network, xpub)
            loadFeeEstimate(_state.value.feeLevel)
            loadFiatRate()
            return
        }

        _state.update {
            it.copy(
                walletId = walletId,
                isLoading = true,
                error = null,
                isInitialized = false
            )
        }

        wallet = walletRepository.getWallet(walletId)

        if (wallet == null) {
            handleError("Wallet not found")
            return
        }

        bitcoinCoins = wallet!!.bitcoinCoins.associateBy { it.network }
        val availableCoins = wallet!!.bitcoinCoins

        if (availableCoins.isEmpty()) {
            handleError("Bitcoin not enabled for this wallet")
            return
        }

        val availableNetworks = bitcoinCoins.keys.toList()

        // Determine target coin
        val targetCoin = coin ?: availableCoins.firstOrNull()

        if (targetCoin == null) {
            handleError("Bitcoin not enabled")
            return
        }

        currentCoin = targetCoin

        when (val result = getBitcoinWalletUseCase(walletId, targetCoin.network)) {
            is Result.Success -> {
                val walletInfo = result.data
                _state.update {
                    it.copy(
                        walletId = walletInfo.walletId,
                        walletName = walletInfo.walletName,
                        walletAddress = walletInfo.walletAddress,
                        network = walletInfo.network,
                        coin = targetCoin,
                        availableNetworks = availableNetworks,
                        isInitialized = true
                    )
                }

                // Load balance, fee estimate, and fiat rate after initialization
                loadBalance(walletInfo.walletAddress, walletInfo.network, targetCoin.xpub)
                loadFeeEstimate(FeeLevel.NORMAL)
                loadFiatRate()

                _state.update { it.copy(isLoading = false) }
            }

            is Result.Error -> handleError(result.message)
            else -> {}
        }
    }

    private suspend fun loadBalance(address: String, network: BitcoinNetwork, xpub: String? = null) {
        when (val result = getBitcoinBalanceUseCase(address, network, xpub)) {
            is Result.Success -> {
                val balance = result.data
                _state.update {
                    it.copy(
                        balance = balance,
                        balanceFormatted = "${
                            balance.setScale(8, RoundingMode.HALF_UP).stripTrailingZeros()
                                .toPlainString()
                        } BTC"
                    )
                }
                validateInputs()
            }

            is Result.Error -> handleError("Failed to load balance: ${result.message}")
            else -> {}
        }
    }

    private suspend fun loadFeeEstimate(feeLevel: FeeLevel) {
        _state.update { it.copy(isFeeLoading = true) }

        val state = _state.value
        
        // Detect if the current address is SegWit (starts with bc1 or tb1)
        val isSegwitAddress = state.walletAddress.startsWith("bc1", ignoreCase = true) || 
                             state.walletAddress.startsWith("tb1", ignoreCase = true)

        val baseFeeResult = getBitcoinFeeEstimateUseCase(
            feeLevel = feeLevel,
            inputCount = DEFAULT_INPUT_COUNT,
            outputCount = DEFAULT_OUTPUT_COUNT,
            network = state.network,
            isSegwit = isSegwitAddress
        )
        
        val feePerByte = if (baseFeeResult is Result.Success) baseFeeResult.data.feePerByte else 10.0
        val utxosResult = bitcoinBlockchainRepository.getUnspentOutputs(state.walletAddress, state.network)

        val inputCount = if (utxosResult is Result.Success) {
            val selected = selectBitcoinUtxosUseCase(
                utxos = utxosResult.data,
                targetSatoshis = state.amountValue.toSatoshis(),
                feePerByte = feePerByte
            )
            if (selected.isNotEmpty()) selected.size else utxosResult.data.size.coerceAtLeast(DEFAULT_INPUT_COUNT)
        } else {
            DEFAULT_INPUT_COUNT
        }

        val hasSegwitUtxo = (utxosResult as? Result.Success)?.data?.any { 
            org.bitcoinj.script.ScriptPattern.isP2WPKH(it.script) 
        } ?: isSegwitAddress

        // If the user has put in their full balance (Max/Sweep), there's only 1 output
        val outputCount = if (state.maxAmountSuggestion != null && state.amountValue == state.maxAmountSuggestion) 1 else 2

        when (val result = getBitcoinFeeEstimateUseCase(
            feeLevel = feeLevel,
            inputCount = inputCount,
            outputCount = outputCount,
            network = state.network,
            isSegwit = hasSegwitUtxo
        )) {
            is Result.Success -> {
                _state.update { it.copy(feeEstimate = result.data, isFeeLoading = false) }
                validateInputs()
            }

            is Result.Error -> {
                _state.update { it.copy(isFeeLoading = false) }
                handleError("Failed to load fee: ${result.message}")
            }

            else -> _state.update { it.copy(isFeeLoading = false) }
        }
    }

    private fun refreshFeeEstimate(immediate: Boolean = false) {
        feeJob?.cancel()
        // Set loading state IMMEDIATELY to prevent clicking "Continue" with stale data
        _state.update { it.copy(isFeeLoading = true) } 
        feeJob = viewModelScope.launch {
            if (!immediate) delay(500)
            loadFeeEstimate(_state.value.feeLevel)
        }
    }

    private suspend fun useMax() {
        _state.update { it.copy(isFeeLoading = true) }
        
        val state = _state.value
        val result = calculateMaxAmountUseCase(
            walletAddress = state.walletAddress,
            network = state.network,
            feeLevel = state.feeLevel,
            balance = state.balance
        )

        when (result) {
            is Result.Success -> {
                val data = result.data
                _state.update { 
                    it.copy(
                        maxAmountSuggestion = data.amountBtc,
                        maxFeeSuggestion = data.feeBtc,
                        isFeeLoading = false
                    ) 
                }
            }
            is Result.Error -> {
                _state.update { it.copy(isFeeLoading = false, maxAmountSuggestion = BigDecimal.ZERO) }
            }
            else -> {
                _state.update { it.copy(isFeeLoading = false) }
            }
        }
        validateInputs()
    }

    private fun updateAddress(address: String) {
        _state.update { it.copy(toAddress = address) }
        validateInputs()
        if (address.length >= 26) {
            refreshFeeEstimate(immediate = false)
        }
    }

    private fun updateAmount(amount: String) {
        if (amount == _state.value.amount) return

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
        if (amountValue > BigDecimal.ZERO) {
            refreshFeeEstimate(immediate = false)
        }
    }

    private fun updateFeeLevel(feeLevel: FeeLevel) {
        _state.update { it.copy(feeLevel = feeLevel) }
        refreshFeeEstimate(immediate = true)
    }

    private suspend fun switchNetwork(network: BitcoinNetwork) {
        val bitcoinCoin = bitcoinCoins[network] ?: return
        currentCoin = bitcoinCoin

        _state.update {
            it.copy(
                network = network,
                coin = bitcoinCoin,
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

    private suspend fun loadFiatRate() {
        when (val result = marketRepository.getTokenDetails("bitcoin", SupportedCurrency.USD)) {
            is Result.Success -> {
                _state.update { it.copy(fiatRate = result.data.currentPrice) }
            }
            else -> {}
        }
    }

    private fun validateInputs() {
        val currentWallet = wallet
        viewModelScope.launch {
            val state = _state.value
            val validationResult = validateBitcoinTransactionUseCase(
                walletId = state.walletId,
                wallet = currentWallet,
                toAddress = state.toAddress,
                amount = state.amountValue,
                network = state.network,
                balance = state.balance,
                feeEstimate = state.feeEstimate,
                isFeeLoading = state.isFeeLoading
            )

            _state.update { currentState ->
                currentState.copy(
                    validationResult = validationResult,
                    isValid = validationResult.isValid,
                    error = when {
                        !validationResult.isValid -> {
                            validationResult.addressError
                                ?: validationResult.selfSendError
                                ?: validationResult.amountError
                                ?: validationResult.balanceError
                                ?: validationResult.gasError
                                ?: validationResult.networkError
                                ?: "Invalid transaction"
                        }
                        else -> null
                    }
                )
            }
        }
    }

    private fun toggleFiatMode(isFiat: Boolean) {
        _state.update { it.copy(isFiatMode = isFiat) }
        validateInputs()
    }

    private fun handleError(message: String) {
        _state.update { it.copy(isLoading = false, error = message) }
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }
}
