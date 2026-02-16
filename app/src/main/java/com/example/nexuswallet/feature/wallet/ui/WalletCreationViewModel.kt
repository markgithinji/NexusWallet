package com.example.nexuswallet.feature.wallet.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.nexuswallet.feature.coin.bitcoin.BitcoinNetwork
import com.example.nexuswallet.feature.wallet.data.repository.WalletRepository
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.Wallet
import com.example.nexuswallet.feature.wallet.domain.CryptoWallet
import com.example.nexuswallet.feature.wallet.domain.EthereumNetwork
import com.example.nexuswallet.feature.wallet.domain.EthereumWallet
import com.example.nexuswallet.feature.wallet.domain.WalletType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
@HiltViewModel
class WalletCreationViewModel @Inject constructor(
    private val walletRepository: WalletRepository
) : ViewModel() {

    // UI State
    private val _uiState = MutableStateFlow<WalletCreationUiState>(WalletCreationUiState.Idle)
    val uiState: StateFlow<WalletCreationUiState> = _uiState

    // Current step
    private val _currentStep = MutableStateFlow(0)
    val currentStep: StateFlow<Int> = _currentStep

    // Generated mnemonic
    private val _mnemonic = MutableStateFlow<List<String>>(emptyList())
    val mnemonic: StateFlow<List<String>> = _mnemonic

    // Coin selection state
    data class CoinSelection(
        var includeBitcoin: Boolean = true,
        var includeEthereum: Boolean = true,
        var includeSolana: Boolean = true,
        var includeUSDC: Boolean = false,
        var ethereumNetwork: EthereumNetwork = EthereumNetwork.SEPOLIA,
        var bitcoinNetwork: BitcoinNetwork = BitcoinNetwork.TESTNET
    )

    private val _coinSelection = MutableStateFlow(CoinSelection())
    val coinSelection: StateFlow<CoinSelection> = _coinSelection

    // Wallet name
    private val _walletName = MutableStateFlow("")
    val walletName: StateFlow<String> = _walletName

    // User entered words for verification
    private val _enteredWords = MutableStateFlow<List<String>>(emptyList())
    val enteredWords: StateFlow<List<String>> = _enteredWords

    // Track if mnemonic is generated
    private val _isMnemonicGenerated = MutableStateFlow(false)
    val isMnemonicGenerated: StateFlow<Boolean> = _isMnemonicGenerated

    fun generateMnemonic() {
        viewModelScope.launch {
            _uiState.value = WalletCreationUiState.Loading
            try {
                val newMnemonic = walletRepository.generateNewMnemonic(12)
                _mnemonic.value = newMnemonic
                _isMnemonicGenerated.value = true
                _uiState.value = WalletCreationUiState.MnemonicGenerated
            } catch (e: Exception) {
                _uiState.value = WalletCreationUiState.Error(e.message ?: "Failed to generate wallet")
                _isMnemonicGenerated.value = false
            }
        }
    }

    fun updateCoinSelection(
        includeBitcoin: Boolean? = null,
        includeEthereum: Boolean? = null,
        includeSolana: Boolean? = null,
        includeUSDC: Boolean? = null,
        ethereumNetwork: EthereumNetwork? = null,
        bitcoinNetwork: BitcoinNetwork? = null
    ) {
        _coinSelection.update { current ->
            current.copy(
                includeBitcoin = includeBitcoin ?: current.includeBitcoin,
                includeEthereum = includeEthereum ?: current.includeEthereum,
                includeSolana = includeSolana ?: current.includeSolana,
                includeUSDC = includeUSDC ?: current.includeUSDC,
                ethereumNetwork = ethereumNetwork ?: current.ethereumNetwork,
                bitcoinNetwork = bitcoinNetwork ?: current.bitcoinNetwork
            )
        }
    }

    fun setWalletName(name: String) {
        _walletName.value = name
    }

    fun addWordToVerification(word: String) {
        _enteredWords.value = _enteredWords.value + word
    }

    fun removeWordFromVerification(index: Int) {
        _enteredWords.value = _enteredWords.value.toMutableList().apply {
            removeAt(index)
        }
    }

    fun verifyMnemonic(): Boolean {
        return walletRepository.validateMnemonic(_enteredWords.value)
    }

    fun completeVerificationAndMoveNext(): Boolean {
        val isVerified = verifyMnemonic()
        if (isVerified) {
            _enteredWords.value = emptyList()
            _currentStep.value = 2 // Move to name step
        }
        return isVerified
    }

    fun nextStep() {
        when (_currentStep.value) {
            0 -> {
                if (!_isMnemonicGenerated.value) {
                    generateMnemonic()
                }
                _currentStep.value = 1
            }
            1 -> {
                if (_mnemonic.value.isNotEmpty()) {
                    _currentStep.value = 2
                }
            }
            2 -> {
                _currentStep.value = 3
            }
        }
    }

    fun previousStep() {
        if (_currentStep.value > 0) {
            _currentStep.value = _currentStep.value - 1
        }
    }

    fun createWallet() {
        viewModelScope.launch {
            _uiState.value = WalletCreationUiState.Loading
            try {
                val mnemonicList = _mnemonic.value
                val name = if (_walletName.value.isBlank()) "My Wallet" else _walletName.value
                val selection = _coinSelection.value

                val result = walletRepository.createWallet(
                    mnemonic = mnemonicList,
                    name = name,
                    includeBitcoin = selection.includeBitcoin,
                    includeEthereum = selection.includeEthereum,
                    includeSolana = selection.includeSolana,
                    includeUSDC = selection.includeUSDC,
                    ethereumNetwork = selection.ethereumNetwork,
                    bitcoinNetwork = selection.bitcoinNetwork
                )

                if (result.isSuccess) {
                    val wallet = result.getOrThrow()
                    _uiState.value = WalletCreationUiState.WalletCreated(wallet)
                } else {
                    val error = result.exceptionOrNull()
                    _uiState.value = WalletCreationUiState.Error(
                        error?.message ?: "Failed to create wallet"
                    )
                }
            } catch (e: Exception) {
                _uiState.value = WalletCreationUiState.Error(e.message ?: "Failed to create wallet")
            }
        }
    }

    fun reset() {
        _uiState.value = WalletCreationUiState.Idle
        _currentStep.value = 0
        _mnemonic.value = emptyList()
        _coinSelection.value = CoinSelection()
        _walletName.value = ""
        _enteredWords.value = emptyList()
        _isMnemonicGenerated.value = false
    }
}

sealed class WalletCreationUiState {
    object Idle : WalletCreationUiState()
    object Loading : WalletCreationUiState()
    object MnemonicGenerated : WalletCreationUiState()
    data class WalletCreated(val wallet: Wallet) : WalletCreationUiState()
    data class Error(val message: String) : WalletCreationUiState()
}

