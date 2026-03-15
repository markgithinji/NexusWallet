package com.example.nexuswallet.feature.wallet.ui.walletcreation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.nexuswallet.feature.core.domain.model.CoinType
import com.example.nexuswallet.feature.core.util.Result
import com.example.nexuswallet.feature.wallet.domain.model.BitcoinNetwork
import com.example.nexuswallet.feature.wallet.domain.model.EthereumNetwork
import com.example.nexuswallet.feature.wallet.domain.model.Network
import com.example.nexuswallet.feature.wallet.domain.usecase.CreateWalletUseCase
import com.example.nexuswallet.feature.wallet.domain.usecase.GenerateMnemonicUseCase
import com.example.nexuswallet.feature.wallet.domain.usecase.ValidateMnemonicUseCase
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

    // Selected networks (base chains)
    private val _selectedNetworks = MutableStateFlow(
        setOf(
            BitcoinNetwork.Mainnet,
            BitcoinNetwork.Testnet,
            EthereumNetwork.Mainnet,
            EthereumNetwork.Sepolia
        )
    )
    val selectedNetworks: StateFlow<Set<Network>> = _selectedNetworks.asStateFlow()

    // Selected tokens (USDC, USDT on specific networks)
    private val _selectedTokens = MutableStateFlow<Map<EthereumNetwork, Set<CoinType>>>(emptyMap())
    val selectedTokens: StateFlow<Map<EthereumNetwork, Set<CoinType>>> =
        _selectedTokens.asStateFlow()

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
                _uiState.value =
                    WalletCreationUiState.Error(e.message ?: "Failed to generate wallet")
                _isMnemonicGenerated.value = false
            }
        }
    }

    fun toggleNetwork(network: Network, isSelected: Boolean) {
        _selectedNetworks.update { current ->
            if (isSelected) {
                current + network
            } else {
                current - network
            }
        }
    }

    fun toggleToken(network: EthereumNetwork, coinType: CoinType, isSelected: Boolean) {
        _selectedTokens.update { current ->
            val currentTokens = current.toMutableMap()
            val networkTokens = currentTokens[network]?.toMutableSet() ?: mutableSetOf()

            if (isSelected) {
                networkTokens.add(coinType)
            } else {
                networkTokens.remove(coinType)
            }

            if (networkTokens.isEmpty()) {
                currentTokens.remove(network)
            } else {
                currentTokens[network] = networkTokens
            }

            currentTokens
        }
    }

    fun isTokenSelected(network: EthereumNetwork, coinType: CoinType): Boolean {
        return _selectedTokens.value[network]?.contains(coinType) ?: false
    }

    fun hasSelections(): Boolean {
        return _selectedNetworks.value.isNotEmpty() || _selectedTokens.value.isNotEmpty()
    }

    fun getSelectedNetworksCount(): Int {
        return _selectedNetworks.value.size
    }

    fun getSelectedTokensCount(): Int {
        return _selectedTokens.value.values.sumOf { it.size }
    }

    fun getSelectedNetworksByType(coinType: CoinType): List<Network> {
        return _selectedNetworks.value.filter { it.coinType == coinType }
    }

    fun getSelectedTokensList(): List<Pair<EthereumNetwork, CoinType>> {
        return _selectedTokens.value.flatMap { (network, tokens) ->
            tokens.map { network to it }
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
        return validateMnemonicUseCase(_enteredWords.value)
    }

    fun completeVerificationAndMoveNext(): Boolean {
        val isVerified = verifyMnemonic()
        if (isVerified) {
            _enteredWords.value = emptyList()
            _currentStep.value = 3 // Move to name step
        }
        return isVerified
    }

    fun nextStep() {
        when (_currentStep.value) {
            0 -> {
                if (!hasSelections()) {
                    _uiState.value =
                        WalletCreationUiState.Error("Please select at least one network or token")
                    return
                }
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

                val result = createWalletUseCase(
                    mnemonic = mnemonicList,
                    name = name,
                    selectedNetworks = _selectedNetworks.value,
                    selectedTokens = _selectedTokens.value
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
        _selectedNetworks.value = setOf(
            BitcoinNetwork.Mainnet,
            BitcoinNetwork.Testnet,
            EthereumNetwork.Mainnet,
            EthereumNetwork.Sepolia
        )
        _selectedTokens.value = emptyMap()
        _walletName.value = ""
        _enteredWords.value = emptyList()
        _isMnemonicGenerated.value = false
    }
}