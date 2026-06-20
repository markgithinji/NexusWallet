package com.example.nexuswallet.feature.wallet.ui.backup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.nexuswallet.feature.wallet.domain.usecase.GetMnemonicUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class BackupViewModel @Inject constructor(
    private val getMnemonicUseCase: GetMnemonicUseCase
) : ViewModel() {

    private val _mnemonic = MutableStateFlow<List<String>?>(null)
    val mnemonic: StateFlow<List<String>?> = _mnemonic.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    fun loadMnemonic(walletId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _mnemonic.value = getMnemonicUseCase(walletId)
            _isLoading.value = false
        }
    }
}
