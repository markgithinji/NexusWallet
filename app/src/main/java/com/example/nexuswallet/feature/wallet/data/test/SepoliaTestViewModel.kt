package com.example.nexuswallet.feature.wallet.data.test

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
class SepoliaTestViewModel @Inject constructor(
    private val repository: SepoliaRepository,
    private val walletRepository: WalletRepository,
    private val keyManager: KeyManager
) : ViewModel() {

    private val _balance = MutableStateFlow<BigDecimal?>(null)
    val balance: StateFlow<BigDecimal?> = _balance.asStateFlow()

    private val _transactions = MutableStateFlow<List<SepoliaTransaction>>(emptyList())
    val transactions: StateFlow<List<SepoliaTransaction>> = _transactions.asStateFlow()

    private val _nonce = MutableStateFlow<Long?>(null)
    val nonce: StateFlow<Long?> = _nonce.asStateFlow()

    private val _gasPrice = MutableStateFlow<String?>(null)
    val gasPrice: StateFlow<String?> = _gasPrice.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _sendStatus = MutableStateFlow<String?>(null)
    val sendStatus: StateFlow<String?> = _sendStatus.asStateFlow()

    private val _txHash = MutableStateFlow<String?>(null)
    val txHash: StateFlow<String?> = _txHash.asStateFlow()

    private val _testResult = MutableStateFlow<String?>(null)
    val testResult: StateFlow<String?> = _testResult.asStateFlow()

    fun loadWalletData(address: String) {
        viewModelScope.launch {
            _loading.value = true
            _error.value = null

            try {
                // Load all data in parallel
                val balanceDeferred = repository.getBalance(address)
                val nonceDeferred = repository.getTransactionCount(address)
                val gasPriceDeferred = repository.getGasPrice()
                val txsDeferred = repository.getTransactions(address)

                _balance.value = balanceDeferred
                _nonce.value = nonceDeferred
                _gasPrice.value = gasPriceDeferred
                _transactions.value = txsDeferred

                if (balanceDeferred == BigDecimal.ZERO) {
                    _error.value = "Balance is zero. Make sure you have test ETH from a faucet."
                }

            } catch (e: Exception) {
                _error.value = "Error: ${e.message}"
                e.printStackTrace()
            } finally {
                _loading.value = false
            }
        }
    }

    fun sendTestTransaction() {
        viewModelScope.launch {
//            _sendStatus.value = "Finding wallet..."
//            _error.value = null
//            _txHash.value = null
//
//            try {
//                val fromAddress = "0xf35d0111a5a55d65b21b9a22f242095584c0c058"
//                val toAddress = "0xb4b0d6410aa23d3bb9c47672210cd70c0e04cb7d"
//
//                val wallets = walletRepository.getAllWallets().first()
//
//                // Find the sending wallet
//                val wallet = wallets.find {
//                    it.address.equals(fromAddress, ignoreCase = true)
//                }
//
//                if (wallet == null) {
//                    _sendStatus.value = "❌ Sending wallet not found"
//                    _error.value = "Wallet with address $fromAddress not found"
//                    return@launch
//                }
//
//                Log.d("SendDebug", "✓ Found wallet: ${wallet.name}")
//                Log.d("SendDebug", "Wallet ID: ${wallet.id}")
//                Log.d("SendDebug", "Wallet address: ${wallet.address}")
//
//                // Verify private key exists
//                val hasKey = keyManager.hasPrivateKey(wallet.id)
//                Log.d("SendDebug", "Has ETH_PRIVATE_KEY? $hasKey")
//
//                if (!hasKey) {
//                    _sendStatus.value = "❌ No Ethereum private key found"
//                    _error.value = "No private key found for wallet"
//                    return@launch
//                }
//
//                _sendStatus.value = "Checking balance..."
//
//                // Get balance
//                val balance = repository.getBalance(fromAddress)
//                Log.d("SendDebug", "Balance: $balance ETH")
//
//                if (balance < BigDecimal("0.01")) {
//                    _sendStatus.value = "❌ Insufficient balance"
//                    _error.value = "Balance: $balance ETH (need 0.01 ETH)"
//                    return@launch
//                }
//
//                _sendStatus.value = "Preparing transaction..."
//
//                // Get current nonce for verification
//                val currentNonce = repository.getTransactionCount(fromAddress)
//                Log.d("SendDebug", "Current nonce: $currentNonce")
//
//                _sendStatus.value = "Sending 0.01 ETH..."
//
//                val result = repository.sendSepoliaETH(
//                    walletId = wallet.id,
//                    fromAddress = fromAddress,
//                    toAddress = toAddress,
//                    amountEth = BigDecimal("0.01")
//                )
//
//                if (result.isSuccess) {
//                    val hash = result.getOrThrow()
//                    _txHash.value = hash
//                    _sendStatus.value = " Transaction sent!"
//                    _testResult.value = "Success! Hash: ${hash.take(10)}..."
//
//                    // Update UI with transaction info
//                    _error.value = "Transaction submitted. Check Etherscan: https://sepolia.etherscan.io/tx/$hash"
//                } else {
//                    val error = result.exceptionOrNull()?.message ?: "Unknown error"
//                    _sendStatus.value = " Failed"
//                    _error.value = error
//                }
//
//            } catch (e: Exception) {
//                Log.e("SendDebug", "Send error: ${e.message}", e)
//                _sendStatus.value = " Error"
//                _error.value = "Error: ${e.message}"
//            }
        }
    }
//
//    // Helper function to verify wallet setup
//    fun verifyWalletSetup(address: String) {
//        viewModelScope.launch {
//            _loading.value = true
//            _error.value = null
//
//            try {
//                // 1. Check if wallet exists
//                val wallets = walletRepository.getAllWallets().first()
//                val wallet = wallets.find { it.address.equals(address, ignoreCase = true) }
//
//                if (wallet == null) {
//                    _error.value = "Wallet not found in local database"
//                    return@launch
//                }
//
//                // 2. Check if private key exists
//                val hasKey = keyManager.hasPrivateKey(wallet.id)
//                if (!hasKey) {
//                    _error.value = "No private key found for wallet"
//                    return@launch
//                }
//
//                // 3. Check balance
//                val balance = repository.getBalance(address)
//                if (balance == BigDecimal.ZERO) {
//                    _error.value = "Wallet has 0 balance on Sepolia"
//                    return@launch
//                }
//
//                // 4. Check nonce
//                val nonce = repository.getTransactionCount(address)
//                Log.d("Verify", "Wallet verified: address=$address, balance=$balance, nonce=$nonce")
//
//                _testResult.value = " Wallet ready! Balance: $balance ETH, Nonce: $nonce"
//
//            } catch (e: Exception) {
//                _error.value = "Verification failed: ${e.message}"
//            } finally {
//                _loading.value = false
//            }
//        }
//    }

}