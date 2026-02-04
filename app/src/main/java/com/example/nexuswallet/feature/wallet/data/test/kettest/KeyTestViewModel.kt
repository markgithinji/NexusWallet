package com.example.nexuswallet.feature.wallet.data.test.kettest

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.nexuswallet.feature.wallet.data.repository.KeyManager
import com.example.nexuswallet.feature.wallet.data.repository.WalletRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.math.BigDecimal
import javax.inject.Inject
@HiltViewModel
class KeyTestViewModel @Inject constructor(
    private val testRepo: KeyStorageTestRepository
) : ViewModel() {

    private val _testResult = MutableStateFlow<String?>(null)
    val testResult: StateFlow<String?> = _testResult.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    fun runKeyStorageTest() {
        viewModelScope.launch {
            _loading.value = true
            _testResult.value = null

            try {
                val result = testRepo.testKeyStorage()
                _testResult.value = result
            } catch (e: Exception) {
                _testResult.value = "❌ Error: ${e.message}"
            } finally {
                _loading.value = false
            }
        }
    }

    fun runRealWalletTest() {
        viewModelScope.launch {
            _loading.value = true
            _testResult.value = null

            try {
                val result = testRepo.testRealWalletKeys()
                _testResult.value = result
            } catch (e: Exception) {
                _testResult.value = "❌ Error: ${e.message}"
            } finally {
                _loading.value = false
            }
        }
    }
}