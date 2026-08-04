package com.example.nexuswallet.feature.wallet.ui.importwallet

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.nexuswallet.feature.core.domain.exception.HardwareAuthRequiredException
import com.example.nexuswallet.feature.core.util.Result
import com.example.nexuswallet.feature.ethereum.domain.model.EVMTokenType
import com.example.nexuswallet.feature.wallet.domain.model.BitcoinNetwork
import com.example.nexuswallet.feature.wallet.domain.model.EthereumNetwork
import com.example.nexuswallet.feature.wallet.domain.model.Network
import com.example.nexuswallet.feature.wallet.domain.usecase.CreateWalletUseCase
import com.example.nexuswallet.feature.wallet.domain.usecase.ValidateMnemonicUseCase
import com.example.nexuswallet.feature.wallet.ui.walletcreation.WalletCreationUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.crypto.Cipher
import javax.inject.Inject

@HiltViewModel
class ImportWalletViewModel @Inject constructor(
    private val validateMnemonicUseCase: ValidateMnemonicUseCase,
    private val createWalletUseCase: CreateWalletUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow<WalletCreationUiState>(WalletCreationUiState.Idle)
    val uiState: StateFlow<WalletCreationUiState> = _uiState.asStateFlow()

    private val _currentStep = MutableStateFlow(0)
    val currentStep: StateFlow<Int> = _currentStep.asStateFlow()

    private val _mnemonicWords = MutableStateFlow(List(12) { charArrayOf() })
    val mnemonicWords: StateFlow<List<CharArray>> = _mnemonicWords.asStateFlow()

    private val _walletName = MutableStateFlow("")
    val walletName: StateFlow<String> = _walletName.asStateFlow()

    private val _selectedNetworks = MutableStateFlow(
        setOf(
            BitcoinNetwork.Mainnet,
            BitcoinNetwork.Testnet,
            EthereumNetwork.Mainnet,
            EthereumNetwork.Sepolia
        )
    )
    val selectedNetworks: StateFlow<Set<Network>> = _selectedNetworks.asStateFlow()

    private val _selectedTokens = MutableStateFlow<Map<EthereumNetwork, Set<EVMTokenType>>>(emptyMap())
    val selectedTokens: StateFlow<Map<EthereumNetwork, Set<EVMTokenType>>> = _selectedTokens.asStateFlow()

    private val _authRequest = MutableStateFlow<Long?>(null)
    val authRequest: StateFlow<Long?> = _authRequest.asStateFlow()

    private var pendingWalletId: String? = null

    private val _cryptoObject = MutableStateFlow<Cipher?>(null)
    val cryptoObject: StateFlow<Cipher?> = _cryptoObject.asStateFlow()

    fun updateWord(index: Int, word: String) {
        _mnemonicWords.update { current ->
            current.toMutableList().apply {
                this[index] = word.trim().lowercase().toCharArray()
            }
        }
    }

    fun setWalletName(name: String) {
        _walletName.value = name
    }

    fun nextStep() {
        if (_currentStep.value == 0) {
            _currentStep.value = 1
        }
    }

    fun previousStep() {
        if (_currentStep.value == 1) {
            _currentStep.value = 0
        }
    }

    fun toggleNetwork(network: Network, isSelected: Boolean) {
        _selectedNetworks.update { current ->
            if (isSelected) current + network else current - network
        }
        if (!isSelected && network is EthereumNetwork) {
            _selectedTokens.update { it - network }
        }
    }

    fun toggleToken(network: EthereumNetwork, evmTokenType: EVMTokenType, isSelected: Boolean) {
        _selectedTokens.update { current ->
            val currentTokens = current.toMutableMap()
            val networkTokens = currentTokens[network]?.toMutableSet() ?: mutableSetOf()

            if (isSelected) networkTokens.add(evmTokenType) else networkTokens.remove(evmTokenType)

            if (networkTokens.isEmpty()) currentTokens.remove(network)
            else currentTokens[network] = networkTokens

            currentTokens
        }
    }

    fun importWallet(cipher: Cipher? = null) {
        val words = _mnemonicWords.value
        if (words.any { it.isEmpty() }) {
            _uiState.value = WalletCreationUiState.Error("Please fill in all 12 words")
            return
        }

        // Validate mnemonic (temporarily using String conversion for the validator library)
        if (!validateMnemonicUseCase(words)) {
            _uiState.value = WalletCreationUiState.Error("Invalid mnemonic phrase")
            return
        }

        if (_selectedNetworks.value.isEmpty()) {
            _uiState.value = WalletCreationUiState.Error("Please select at least one network")
            return
        }

        viewModelScope.launch {
            _uiState.value = WalletCreationUiState.Loading
            
            val name = _walletName.value.ifBlank { "Imported Wallet" }
            val walletId = pendingWalletId ?: "wallet_${System.currentTimeMillis()}".also { pendingWalletId = it }

            val result = createWalletUseCase(
                walletId = walletId,
                mnemonic = words,
                name = name,
                selectedNetworks = _selectedNetworks.value,
                selectedTokens = _selectedTokens.value,
                cipher = cipher
            )

            when (result) {
                is Result.Success -> {
                    pendingWalletId = null
                    _cryptoObject.value = null
                    _authRequest.value = null
                    _uiState.value = WalletCreationUiState.WalletCreated(result.data)
                }
                is Result.Error -> {
                    val authException = result.throwable as? HardwareAuthRequiredException
                    if (authException != null) {
                        _cryptoObject.value = authException.cryptoObject?.cipher
                        _authRequest.value = System.currentTimeMillis()
                        _uiState.value = WalletCreationUiState.Idle
                    } else {
                        _uiState.value = WalletCreationUiState.Error(result.message)
                    }
                }
                else -> {
                    _uiState.value = WalletCreationUiState.Error("Unexpected result")
                }
            }
        }
    }

    fun onBiometricSuccess(cipher: Cipher? = null) {
        _cryptoObject.value = null
        _authRequest.value = null
        viewModelScope.launch {
            // Small delay to ensure TEE session is fully registered on physical hardware
            delay(300)
            importWallet(cipher)
        }
    }

    fun setErrorMessage(message: String) {
        _uiState.value = WalletCreationUiState.Error(message)
    }

    fun clearAuthRequest() {
        _authRequest.value = null
    }
}
