package com.example.nexuswallet.feature.wallet.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.nexuswallet.feature.coin.Result
import com.example.nexuswallet.feature.wallet.data.securityrefactor.GenerateMnemonicUseCase
import com.example.nexuswallet.feature.wallet.data.securityrefactor.ValidateMnemonicUseCase
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.Wallet
import com.example.nexuswallet.feature.wallet.domain.CreateWalletUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class WalletCreationViewModel @Inject constructor(
    private val generateMnemonicUseCase: GenerateMnemonicUseCase,
    private val validateMnemonicUseCase: ValidateMnemonicUseCase,
    private val createWalletUseCase: CreateWalletUseCase
) : ViewModel() {

    // UI State
    private val _uiState = MutableStateFlow<WalletCreationUiState>(WalletCreationUiState.Idle)
    val uiState: StateFlow<WalletCreationUiState> = _uiState.asStateFlow()

    // Current step
    private val _currentStep = MutableStateFlow(0)
    val currentStep: StateFlow<Int> = _currentStep.asStateFlow()

    // Generated mnemonic
    private val _mnemonic = MutableStateFlow<List<String>>(emptyList())
    val mnemonic: StateFlow<List<String>> = _mnemonic.asStateFlow()

    // Coin selection state
    data class CoinSelection(
        // Bitcoin - can select multiple networks
        var includeBitcoinMainnet: Boolean = true,
        var includeBitcoinTestnet: Boolean = true,

        // Ethereum - Native ETH
        var includeEthereumMainnet: Boolean = true,
        var includeEthereumSepolia: Boolean = true,

        // Solana
        var includeSolanaMainnet: Boolean = false,
        var includeSolanaDevnet: Boolean = false,

        // Tokens (USDC, USDT, etc.)
        var includeUSDCMainnet: Boolean = false,
        var includeUSDCSepolia: Boolean = false,
        var includeUSDTMainnet: Boolean = false
    )

    private val _coinSelection = MutableStateFlow(CoinSelection())
    val coinSelection: StateFlow<CoinSelection> = _coinSelection.asStateFlow()

    // Wallet name
    private val _walletName = MutableStateFlow("")
    val walletName: StateFlow<String> = _walletName.asStateFlow()

    // User entered words for verification
    private val _enteredWords = MutableStateFlow<List<String>>(emptyList())
    val enteredWords: StateFlow<List<String>> = _enteredWords.asStateFlow()

    // Track if mnemonic is generated
    private val _isMnemonicGenerated = MutableStateFlow(false)
    val isMnemonicGenerated: StateFlow<Boolean> = _isMnemonicGenerated.asStateFlow()

    fun generateMnemonic() {
        viewModelScope.launch {
            _uiState.value = WalletCreationUiState.Loading
            try {
                val newMnemonic = generateMnemonicUseCase(12)
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
        // Bitcoin
        includeBitcoinMainnet: Boolean? = null,
        includeBitcoinTestnet: Boolean? = null,

        // Ethereum
        includeEthereumMainnet: Boolean? = null,
        includeEthereumSepolia: Boolean? = null,

        // Solana
        includeSolanaMainnet: Boolean? = null,
        includeSolanaDevnet: Boolean? = null,

        // Tokens
        includeUSDCMainnet: Boolean? = null,
        includeUSDCSepolia: Boolean? = null,
        includeUSDTMainnet: Boolean? = null
    ) {
        _coinSelection.update { current ->
            current.copy(
                // Bitcoin
                includeBitcoinMainnet = includeBitcoinMainnet ?: current.includeBitcoinMainnet,
                includeBitcoinTestnet = includeBitcoinTestnet ?: current.includeBitcoinTestnet,

                // Ethereum
                includeEthereumMainnet = includeEthereumMainnet ?: current.includeEthereumMainnet,
                includeEthereumSepolia = includeEthereumSepolia ?: current.includeEthereumSepolia,

                // Solana
                includeSolanaMainnet = includeSolanaMainnet ?: current.includeSolanaMainnet,
                includeSolanaDevnet = includeSolanaDevnet ?: current.includeSolanaDevnet,

                // Tokens
                includeUSDCMainnet = includeUSDCMainnet ?: current.includeUSDCMainnet,
                includeUSDCSepolia = includeUSDCSepolia ?: current.includeUSDCSepolia,
                includeUSDTMainnet = includeUSDTMainnet ?: current.includeUSDTMainnet
            )
        }
    }

    // Helper methods to get counts for UI
    fun getSelectedBitcoinCount(): Int {
        val selection = _coinSelection.value
        return listOfNotNull(
            selection.includeBitcoinMainnet.takeIf { it },
            selection.includeBitcoinTestnet.takeIf { it }
        ).size
    }

    fun getSelectedEthereumCount(): Int {
        val selection = _coinSelection.value
        return listOfNotNull(
            selection.includeEthereumMainnet.takeIf { it },
            selection.includeEthereumSepolia.takeIf { it }
        ).size
    }

    fun getSelectedSolanaCount(): Int {
        val selection = _coinSelection.value
        return listOfNotNull(
            selection.includeSolanaMainnet.takeIf { it },
            selection.includeSolanaDevnet.takeIf { it }
        ).size
    }

    fun getSelectedTokenCount(): Int {
        val selection = _coinSelection.value
        return listOfNotNull(
            selection.includeUSDCMainnet.takeIf { it },
            selection.includeUSDCSepolia.takeIf { it },
            selection.includeUSDTMainnet.takeIf { it }
        ).size
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
        return validateMnemonicUseCase(_enteredWords.value)
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
                if (mnemonicList.isEmpty()) {
                    _uiState.value = WalletCreationUiState.Error("No mnemonic generated")
                    return@launch
                }

                val name = if (_walletName.value.isBlank()) "My Wallet" else _walletName.value
                val selection = _coinSelection.value

                val result = createWalletUseCase(
                    mnemonic = mnemonicList,
                    name = name,
                    // Bitcoin networks
                    includeBitcoinMainnet = selection.includeBitcoinMainnet,
                    includeBitcoinTestnet = selection.includeBitcoinTestnet,

                    // Ethereum networks
                    includeEthereumMainnet = selection.includeEthereumMainnet,
                    includeEthereumSepolia = selection.includeEthereumSepolia,

                    // Solana networks
                    includeSolanaMainnet = selection.includeSolanaMainnet,
                    includeSolanaDevnet = selection.includeSolanaDevnet,

                    // Tokens
                    includeUSDCMainnet = selection.includeUSDCMainnet,
                    includeUSDCSepolia = selection.includeUSDCSepolia,
                    includeUSDTMainnet = selection.includeUSDTMainnet
                )

                when (result) {
                    is Result.Success -> {
                        _uiState.value = WalletCreationUiState.WalletCreated(result.data)
                    }
                    is Result.Error -> {
                        _uiState.value = WalletCreationUiState.Error(result.message)
                    }
                    Result.Loading -> {
                        _uiState.value = WalletCreationUiState.Error("Unexpected loading state")
                    }
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
        _coinSelection.value = CoinSelection(
            includeBitcoinMainnet = true,
            includeBitcoinTestnet = true,
            includeEthereumMainnet = true,
            includeEthereumSepolia = true,
            includeSolanaMainnet = false,
            includeSolanaDevnet = false,
            includeUSDCMainnet = false,
            includeUSDCSepolia = false,
            includeUSDTMainnet = false
        )
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