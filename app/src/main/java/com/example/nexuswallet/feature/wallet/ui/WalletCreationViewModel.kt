package com.example.nexuswallet.feature.wallet.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.nexuswallet.NexusWalletApplication
import com.example.nexuswallet.feature.wallet.data.repository.WalletRepository
import com.example.nexuswallet.feature.wallet.domain.CryptoWallet
import com.example.nexuswallet.feature.wallet.domain.WalletType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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

    // Selected wallet type
    private val _selectedWalletType = MutableStateFlow(WalletType.MULTICHAIN)
    val selectedWalletType: StateFlow<WalletType> = _selectedWalletType

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
                _uiState.value =
                    WalletCreationUiState.Error(e.message ?: "Failed to generate wallet")
                _isMnemonicGenerated.value = false
            }
        }
    }

    fun setWalletType(type: WalletType) {
        _selectedWalletType.value = type
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
            // Clear entered words
            _enteredWords.value = emptyList()
            // Move to next step
            _currentStep.value = 3
        }
        return isVerified
    }

    fun nextStep() {
        when (_currentStep.value) {
            0 -> {
                // From type selection: Generate mnemonic and move to backup
                if (!_isMnemonicGenerated.value) {
                    generateMnemonic()
                }
                _currentStep.value = 1
            }
            1 -> {
                // From backup: Move to verification if mnemonic exists
                if (_mnemonic.value.isNotEmpty()) {
                    _currentStep.value = 2
                }
            }
            2 -> {
                // Just move to step 3 (will be validated separately)
                _currentStep.value = 3
            }
            3 -> {
                // From name: Move to success
                _currentStep.value = 4
            }
            else -> {
                if (_currentStep.value < 4) {
                    _currentStep.value = _currentStep.value + 1
                }
            }
        }
    }

    fun createWallet() {
        viewModelScope.launch {
            _uiState.value = WalletCreationUiState.Loading
            try {
                val mnemonicList = _mnemonic.value
                val name = if (_walletName.value.isBlank()) "My Wallet" else _walletName.value

                val wallet = when (_selectedWalletType.value) {
                    WalletType.BITCOIN -> walletRepository.createBitcoinWallet(mnemonicList, name)
                    WalletType.ETHEREUM -> walletRepository.createEthereumWallet(
                        mnemonicList,
                        name
                    )
                    WalletType.MULTICHAIN -> walletRepository.createMultiChainWallet(
                        mnemonicList,
                        name
                    )
                    else -> walletRepository.createMultiChainWallet(mnemonicList, name)
                }

                // Save the wallet to repository
                walletRepository.saveWallet(wallet)

                // Update UI state
                _uiState.value = WalletCreationUiState.WalletCreated(wallet)

            } catch (e: Exception) {
                _uiState.value = WalletCreationUiState.Error(e.message ?: "Failed to create wallet")
            }
        }
    }

    // Move to previous step
    fun previousStep() {
        if (_currentStep.value > 0) {
            _currentStep.value = _currentStep.value - 1
        }
    }

    fun reset() {
        _uiState.value = WalletCreationUiState.Idle
        _currentStep.value = 0
        _mnemonic.value = emptyList()
        _selectedWalletType.value = WalletType.MULTICHAIN
        _walletName.value = ""
        _enteredWords.value = emptyList()
        _isMnemonicGenerated.value = false
    }
}

sealed class WalletCreationUiState {
    object Idle : WalletCreationUiState()
    object Loading : WalletCreationUiState()
    object MnemonicGenerated : WalletCreationUiState()
    data class WalletCreated(val wallet: CryptoWallet) : WalletCreationUiState()
    data class Error(val message: String) : WalletCreationUiState()
}