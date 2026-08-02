package com.example.nexuswallet.feature.settings.ui.security

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.example.nexuswallet.feature.settings.domain.repository.SecurityRepository
import com.example.nexuswallet.feature.core.util.Result
import com.example.nexuswallet.feature.ethereum.domain.model.EVMTokenType
import com.example.nexuswallet.feature.settings.domain.model.*
import com.example.nexuswallet.feature.settings.domain.repository.BackupRepository
import com.example.nexuswallet.feature.settings.domain.usecase.*
import com.example.nexuswallet.feature.wallet.domain.model.EthereumNetwork
import com.example.nexuswallet.feature.wallet.domain.model.Network
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

@HiltViewModel
class SecuritySettingsViewModel @Inject constructor(
    private val getAuthStatusUseCase: GetAuthStatusUseCase,
    private val setBiometricEnabledUseCase: SetBiometricEnabledUseCase,
    private val securityRepository: SecurityRepository,
    private val setPinUseCase: SetPinUseCase,
    private val clearPinUseCase: ClearPinUseCase,
    private val verifyPinUseCase: VerifyPinUseCase,
    private val clearAllSecurityDataUseCase: ClearAllSecurityDataUseCase,
    private val createBackupUseCase: CreateBackupUseCase,
    private val restoreBackupUseCase: RestoreBackupUseCase,
    private val backupRepository: BackupRepository
) : ViewModel() {

    // UI State
    private val _uiState = MutableStateFlow<Result<SecurityUiState>>(Result.Loading)
    val uiState: StateFlow<Result<SecurityUiState>> = _uiState.asStateFlow()

    // UI Effects
    private val _uiEffect = MutableSharedFlow<SecurityUiEffect>()
    val uiEffect = _uiEffect.asSharedFlow()

    // Dialog states
    private val _showPinSetupDialog = MutableStateFlow(false)
    val showPinSetupDialog: StateFlow<Boolean> = _showPinSetupDialog.asStateFlow()

    private val _showPinChangeDialog = MutableStateFlow(false)
    val showPinChangeDialog: StateFlow<Boolean> = _showPinChangeDialog.asStateFlow()

    private val _showPinVerifyDialog = MutableStateFlow(false)
    val showPinVerifyDialog: StateFlow<Boolean> = _showPinVerifyDialog.asStateFlow()

    private val _showClearAllDataDialog = MutableStateFlow(false)
    val showClearAllDataDialog: StateFlow<Boolean> = _showClearAllDataDialog.asStateFlow()

    private val _showRestoreSelectionDialog = MutableStateFlow(false)
    val showRestoreSelectionDialog: StateFlow<Boolean> = _showRestoreSelectionDialog.asStateFlow()

    private val _clearAllConfirmationText = MutableStateFlow("")
    val clearAllConfirmationText: StateFlow<String> = _clearAllConfirmationText.asStateFlow()

    private val _authRequest = MutableStateFlow<Long?>(null)
    val authRequest: StateFlow<Long?> = _authRequest.asStateFlow()

    private val _pinSetupError = MutableStateFlow<String?>(null)
    val pinSetupError: StateFlow<String?> = _pinSetupError.asStateFlow()

    // Operation state for loading overlays
    private val _operationState = MutableStateFlow<SecurityOperation>(SecurityOperation.IDLE)
    val operationState: StateFlow<SecurityOperation> = _operationState.asStateFlow()

    // Restore Selection State
    private val _decryptedBundle = MutableStateFlow<BackupBundle?>(null)
    val decryptedBundle: StateFlow<BackupBundle?> = _decryptedBundle.asStateFlow()

    private val _restoreSelection = MutableStateFlow(RestoreSelection())
    val restoreSelection: StateFlow<RestoreSelection> = _restoreSelection.asStateFlow()

    private val _pinVerifyPurpose = MutableStateFlow<PinVerifyPurpose?>(null)
    val pinVerifyPurpose: StateFlow<PinVerifyPurpose?> = _pinVerifyPurpose.asStateFlow()

    private var pendingBackupData: ByteArray? = null
    private var pendingBackupPin: String? = null

    init {
        loadSecurityStatus()
    }

    private fun loadSecurityStatus() {
        viewModelScope.launch {
            _uiState.value = Result.Loading

            when (val result = getAuthStatusUseCase()) {
                is Result.Success -> {
                    val status = result.data
                    _uiState.value = Result.Success(
                        SecurityUiState(
                            isBiometricEnabled = status.isBiometricEnabled,
                            isPinSet = status.isPinSet,
                            isPrivacyModeEnabled = status.isPrivacyModeEnabled,
                            isRequireAuthForSend = status.isRequireAuthForSend,
                            availableAuthMethods = status.availableMethods,
                            isAnyAuthEnabled = status.isAnyAuthEnabled
                        )
                    )
                }
                is Result.Error -> {
                    _uiState.value = Result.Error(result.message)
                }
                Result.Loading -> { /* Ignore */ }
            }
        }
    }

    fun setBiometricEnabled(enabled: Boolean) {
        viewModelScope.launch {
            when (val result = setBiometricEnabledUseCase(enabled)) {
                is Result.Success -> {
                    refreshAuthStatus()
                }
                is Result.Error -> {
                    _uiEffect.emit(SecurityUiEffect.ShowSnackbar(result.message))
                }
                Result.Loading -> { /* Ignore */ }
            }
        }
    }

    fun setPrivacyModeEnabled(enabled: Boolean) {
        viewModelScope.launch {
            try {
                securityRepository.setPrivacyModeEnabled(enabled)
                refreshAuthStatus()
            } catch (e: Exception) {
                _uiEffect.emit(SecurityUiEffect.ShowSnackbar(e.message ?: "Failed to update privacy mode"))
            }
        }
    }

    fun setRequireAuthForSend(enabled: Boolean) {
        viewModelScope.launch {
            try {
                securityRepository.setRequireAuthForSend(enabled)
                refreshAuthStatus()
            } catch (e: Exception) {
                _uiEffect.emit(SecurityUiEffect.ShowSnackbar(e.message ?: "Failed to update security preference"))
            }
        }
    }

    private suspend fun refreshAuthStatus() {
        when (val result = getAuthStatusUseCase()) {
            is Result.Success -> {
                val status = result.data
                _uiState.update { currentState ->
                    when (currentState) {
                        is Result.Success -> {
                            val updatedState = currentState.data.copy(
                                isBiometricEnabled = status.isBiometricEnabled,
                                isPinSet = status.isPinSet,
                                isPrivacyModeEnabled = status.isPrivacyModeEnabled,
                                isRequireAuthForSend = status.isRequireAuthForSend,
                                availableAuthMethods = status.availableMethods,
                                isAnyAuthEnabled = status.isAnyAuthEnabled
                            )
                            Result.Success(updatedState)
                        }
                        else -> {
                            Result.Success(
                                SecurityUiState(
                                    isBiometricEnabled = status.isBiometricEnabled,
                                    isPinSet = status.isPinSet,
                                    isPrivacyModeEnabled = status.isPrivacyModeEnabled,
                                    isRequireAuthForSend = status.isRequireAuthForSend,
                                    availableAuthMethods = status.availableMethods,
                                    isAnyAuthEnabled = status.isAnyAuthEnabled
                                )
                            )
                        }
                    }
                }
            }
            is Result.Error -> {
                _uiEffect.emit(SecurityUiEffect.ShowSnackbar(result.message))
            }
            Result.Loading -> { /* Ignore */ }
        }
    }

    private fun checkPinAndProceed(action: () -> Unit) {
        val state = _uiState.value
        if (state is Result.Success) {
            if (state.data.isPinSet) {
                action()
            } else {
                viewModelScope.launch {
                    _uiEffect.emit(SecurityUiEffect.ShowSnackbar("Please set a PIN first to secure your backup"))
                }
            }
        }
    }

    fun handleCreateBackupClick() {
        checkPinAndProceed {
            _pinVerifyPurpose.value = PinVerifyPurpose.BACKUP
            _showPinVerifyDialog.value = true
            _pinSetupError.value = null
        }
    }

    fun handleRestoreBackupClick() {
        viewModelScope.launch {
            _uiEffect.emit(SecurityUiEffect.SelectBackupFile)
        }
    }

    fun onBackupFileSelected(data: ByteArray) {
        pendingBackupData = data
        _pinVerifyPurpose.value = PinVerifyPurpose.RESTORE
        _showPinVerifyDialog.value = true
        _pinSetupError.value = null
    }

    fun onPinVerified(pin: String) {
        viewModelScope.launch {
            _pinSetupError.value = null
            
            // For RESTORE, we use the entered PIN directly for decryption.
            // If it's wrong, the decryption will fail in executeBackupOperation.
            // This allows restoration on new installs where no local PIN is set yet.
            if (_pinVerifyPurpose.value == PinVerifyPurpose.RESTORE) {
                _showPinVerifyDialog.value = false
                executeBackupOperation(pin)
                return@launch
            }

            val verifyResult = verifyPinUseCase(pin)
            if (verifyResult is Result.Success && verifyResult.data) {
                _showPinVerifyDialog.value = false
                executeBackupOperation(pin)
            } else {
                _pinSetupError.value = "Incorrect PIN"
            }
        }
    }

    private fun executeBackupOperation(pin: String) {
        viewModelScope.launch {
            when (_pinVerifyPurpose.value) {
                PinVerifyPurpose.BACKUP -> {
                    _operationState.value = SecurityOperation.BACKING_UP
                    when (val result = createBackupUseCase(pin)) {
                        is Result.Success -> {
                            _uiEffect.emit(SecurityUiEffect.SaveBackupFile(
                                data = result.data,
                                fileName = "nexus_backup_${System.currentTimeMillis()}.bin"
                            ))
                        }
                        is Result.Error -> _uiEffect.emit(SecurityUiEffect.ShowSnackbar(result.message))
                        else -> {}
                    }
                    _operationState.value = SecurityOperation.IDLE
                }
                PinVerifyPurpose.RESTORE -> {
                    val data = pendingBackupData
                    if (data != null) {
                        _operationState.value = SecurityOperation.RESTORING
                        when (val result = backupRepository.decryptBackup(data, pin)) {
                            is Result.Success -> {
                                _decryptedBundle.value = result.data
                                pendingBackupPin = pin
                                // Initialize selection with everything enabled
                                initRestoreSelection(result.data)
                                _showRestoreSelectionDialog.value = true
                            }
                            is Result.Error -> {
                                _uiEffect.emit(SecurityUiEffect.ShowSnackbar(result.message))
                            }
                            else -> {}
                        }
                        _operationState.value = SecurityOperation.IDLE
                    }
                }
                null -> {}
            }
            _pinVerifyPurpose.value = null
            pendingBackupData = null
        }
    }

    private fun initRestoreSelection(bundle: BackupBundle) {
        val walletIds = bundle.wallets.map { it.id }.toSet()
        val networks = mutableMapOf<String, Set<String>>()
        val tokens = mutableMapOf<String, Map<String, Set<EVMTokenType>>>()

        bundle.wallets.forEach { wallet ->
            val allNetworks = (wallet.bitcoinCoins.map { it.network.name } +
                               wallet.solanaCoins.map { it.network.name } +
                               wallet.evmTokens.map { it.network.name }).toSet()
            networks[wallet.id] = allNetworks

            val walletTokens = mutableMapOf<String, Set<EVMTokenType>>()
            wallet.evmTokens.groupBy { it.network.name }.forEach { (netName, evmTokens) ->
                walletTokens[netName] = evmTokens.map { it.evmTokenType }.toSet()
            }
            tokens[wallet.id] = walletTokens
        }

        _restoreSelection.value = RestoreSelection(
            selectedWallets = walletIds,
            selectedNetworks = networks,
            selectedTokens = tokens
        )
    }

    fun toggleWalletSelection(walletId: String, selected: Boolean) {
        _restoreSelection.update { current ->
            val newWallets = if (selected) current.selectedWallets + walletId else current.selectedWallets - walletId
            current.copy(selectedWallets = newWallets)
        }
    }

    fun toggleNetworkSelection(walletId: String, networkName: String, selected: Boolean) {
        _restoreSelection.update { current ->
            val walletNetworks = current.selectedNetworks[walletId]?.toMutableSet() ?: mutableSetOf()
            if (selected) walletNetworks.add(networkName) else walletNetworks.remove(networkName)
            
            val newNetworks = current.selectedNetworks.toMutableMap()
            newNetworks[walletId] = walletNetworks
            current.copy(selectedNetworks = newNetworks)
        }
    }

    fun toggleTokenSelection(walletId: String, networkName: String, tokenType: EVMTokenType, selected: Boolean) {
        _restoreSelection.update { current ->
            val walletTokens = current.selectedTokens[walletId]?.toMutableMap() ?: mutableMapOf()
            val netTokens = walletTokens[networkName]?.toMutableSet() ?: mutableSetOf()
            
            if (selected) netTokens.add(tokenType) else netTokens.remove(tokenType)
            walletTokens[networkName] = netTokens
            
            val newTokens = current.selectedTokens.toMutableMap()
            newTokens[walletId] = walletTokens
            current.copy(selectedTokens = newTokens)
        }
    }

    fun confirmRestore() {
        val bundle = _decryptedBundle.value ?: return
        val selection = _restoreSelection.value
        val pin = pendingBackupPin
        
        viewModelScope.launch {
            _showRestoreSelectionDialog.value = false
            _operationState.value = SecurityOperation.RESTORING
            
            when (val result = restoreBackupUseCase(bundle, selection)) {
                is Result.Success -> {
                    // Auto-set the PIN used for decryption as the app PIN
                    if (pin != null) {
                        setPinUseCase(pin)
                    }
                    refreshAuthStatus()
                    _uiEffect.emit(SecurityUiEffect.ShowSnackbar("Backup restored successfully"))
                    _uiEffect.emit(SecurityUiEffect.RestoreSuccess)
                }
                is Result.Error -> {
                    _uiEffect.emit(SecurityUiEffect.ShowSnackbar(result.message))
                }
                else -> {}
            }
            
            _operationState.value = SecurityOperation.IDLE
            _decryptedBundle.value = null
            pendingBackupPin = null
        }
    }

    fun cancelRestoreSelection() {
        _showRestoreSelectionDialog.value = false
        _decryptedBundle.value = null
        pendingBackupPin = null
        pendingBackupData = null
    }

    fun deleteBackup() {
        viewModelScope.launch {
            _operationState.value = SecurityOperation.UPDATING
            // TODO: Implement delete backup logic
            delay(1000)
            _operationState.value = SecurityOperation.IDLE
        }
    }

    fun requestClearAllData() {
        _showClearAllDataDialog.value = true
        _clearAllConfirmationText.value = ""
    }

    fun onClearAllConfirmationTextChanged(text: String) {
        _clearAllConfirmationText.value = text
    }

    fun cancelClearAllData() {
        _showClearAllDataDialog.value = false
        _clearAllConfirmationText.value = ""
    }

    fun confirmClearAllData() {
        if (_clearAllConfirmationText.value.trim().uppercase() != "DELETE") {
            return
        }
        _showClearAllDataDialog.value = false
        
        // Trigger authentication request for the UI
        _authRequest.value = System.currentTimeMillis()
    }

    fun onClearAllAuthSuccess() {
        _authRequest.value = null
        clearAllData()
    }

    private fun clearAllData() {
        viewModelScope.launch {
            _operationState.value = SecurityOperation.UPDATING

            when (val result = clearAllSecurityDataUseCase()) {
                is Result.Success -> {
                    refreshAuthStatus()
                    _uiEffect.emit(SecurityUiEffect.ShowSnackbar("All security data cleared"))
                }
                is Result.Error -> {
                    _uiEffect.emit(SecurityUiEffect.ShowSnackbar(result.message))
                }
                Result.Loading -> { /* Ignore */ }
            }

            _operationState.value = SecurityOperation.IDLE
        }
    }

    fun setupPin() {
        _showPinSetupDialog.value = true
        _pinSetupError.value = null
    }

    fun setNewPin(pin: String) {
        viewModelScope.launch {
            _operationState.value = SecurityOperation.UPDATING

            when (val result = setPinUseCase(pin)) {
                is Result.Success -> {
                    if (result.data) {
                        refreshAuthStatus()
                        _showPinSetupDialog.value = false
                        _showPinChangeDialog.value = false
                        _uiEffect.emit(SecurityUiEffect.ShowSnackbar("PIN set successfully"))
                    } else {
                        _pinSetupError.value = "Failed to set PIN"
                    }
                }
                is Result.Error -> {
                    _pinSetupError.value = "Failed to set PIN: ${result.message}"
                }
                Result.Loading -> {
                    _pinSetupError.value = "Setting PIN..."
                }
            }

            _operationState.value = SecurityOperation.IDLE
        }
    }

    fun changePin() {
        _showPinChangeDialog.value = true
        _pinSetupError.value = null
    }

    fun removePin() {
        viewModelScope.launch {
            _operationState.value = SecurityOperation.UPDATING

            when (val result = clearPinUseCase()) {
                is Result.Success -> {
                    refreshAuthStatus()
                    _uiEffect.emit(SecurityUiEffect.ShowSnackbar("PIN removed"))
                }
                is Result.Error -> {
                    _uiEffect.emit(SecurityUiEffect.ShowSnackbar(result.message))
                }
                Result.Loading -> { /* Ignore */ }
            }

            _operationState.value = SecurityOperation.IDLE
        }
    }

    fun cancelPinSetup() {
        _showPinSetupDialog.value = false
        _showPinChangeDialog.value = false
        _showPinVerifyDialog.value = false
        _pinSetupError.value = null
        _pinVerifyPurpose.value = null
        pendingBackupData = null
        pendingBackupPin = null
    }

    fun clearError() {
        _uiState.update { currentState ->
            when (currentState) {
                is Result.Error -> Result.Loading
                else -> currentState
            }
        }
    }

    fun retry() {
        loadSecurityStatus()
    }
}
