package com.example.nexuswallet.feature.wallet.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.nexuswallet.feature.coin.ethereum.EthereumBlockchainRepository
import com.example.nexuswallet.feature.coin.ethereum.GasPrice
import com.example.nexuswallet.feature.wallet.domain.BitcoinWallet
import com.example.nexuswallet.feature.wallet.domain.ChainType
import com.example.nexuswallet.feature.wallet.domain.CryptoWallet
import com.example.nexuswallet.feature.wallet.domain.EthereumWallet
import com.example.nexuswallet.feature.wallet.domain.MultiChainWallet
import com.example.nexuswallet.feature.wallet.domain.TokenBalance
import com.example.nexuswallet.feature.wallet.domain.Transaction
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.math.BigDecimal
import java.util.Date
import javax.inject.Inject

@HiltViewModel
class BlockchainViewModel @Inject constructor(
    private val ethereumBlockchainRepository: EthereumBlockchainRepository
) : ViewModel() {

    private val _ethBalance = MutableStateFlow<BigDecimal?>(null)
    val ethBalance: StateFlow<BigDecimal?> = _ethBalance

    private val _btcBalance = MutableStateFlow<BigDecimal?>(null)
    val btcBalance: StateFlow<BigDecimal?> = _btcBalance

    private val _transactions = MutableStateFlow<List<Transaction>>(emptyList())
    val transactions: StateFlow<List<Transaction>> = _transactions

    private val _gasPrice = MutableStateFlow<GasPrice?>(null)
    val gasPrice: StateFlow<GasPrice?> = _gasPrice

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private val _apiStatus = MutableStateFlow(ApiStatus.CONNECTING)
    val apiStatus: StateFlow<ApiStatus> = _apiStatus

    private val _lastUpdated = MutableStateFlow<Date?>(null)
    val lastUpdated: StateFlow<Date?> = _lastUpdated

    private val _apiTestResults = MutableStateFlow<List<ApiTestResult>>(emptyList())
    val apiTestResults: StateFlow<List<ApiTestResult>> = _apiTestResults

    fun fetchWalletData(wallet: CryptoWallet) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null

            try {
                when (wallet) {
                    is EthereumWallet -> {
                        _ethBalance.value = ethereumBlockchainRepository.getEthereumBalance(wallet.address)
                        _transactions.value =
                            ethereumBlockchainRepository.getEthereumTransactions(wallet.address)
                        loadGasPrice()
                        updateApiStatus(true)
                    }

                    else -> {}
                }
                _lastUpdated.value = Date()
            } catch (e: Exception) {
                _error.value = "Failed to fetch blockchain data: ${e.message}"
                Log.e("BlockchainVM", "Error: ${e.message}", e)
                updateApiStatus(false)
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun refresh(wallet: CryptoWallet) {
        fetchWalletData(wallet)
    }

    private val _rawData = MutableStateFlow<String?>(null)
    val rawData: StateFlow<String?> = _rawData

    private val _selectedApi = MutableStateFlow("etherscan")
    val selectedApi: StateFlow<String> = _selectedApi

    fun refreshRawData(wallet: CryptoWallet) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val data = getRawApiDataInternal(wallet, _selectedApi.value)
                _rawData.value = data
                _lastUpdated.value = Date()
            } catch (e: Exception) {
                _error.value = "Failed to fetch raw data: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun selectApi(api: String) {
        _selectedApi.value = api
    }

    private suspend fun getRawApiDataInternal(wallet: CryptoWallet, api: String): String {
        return try {
            when (api) {
                "etherscan" -> {
                    if (wallet is EthereumWallet || wallet is MultiChainWallet) {
                        val address = getEthereumAddress(wallet)
                        if (address.isNotEmpty()) {
                            val balance = ethereumBlockchainRepository.getEthereumBalance(address)
                            val json = Json { prettyPrint = true }
                            json.encodeToString(
                                RawBalanceResponse(
                                    address = address,
                                    balance = balance.toPlainString(),
                                    source = "Etherscan API",
                                    timestamp = System.currentTimeMillis()
                                )
                            )
                        } else {
                            "No Ethereum address found for this wallet"
                        }
                    } else {
                        "Wallet is not an Ethereum wallet"
                    }
                }

                else -> "Unknown API: $api"
            }
        } catch (e: Exception) {
            "Error fetching data from $api: ${e.message}\n\nStack Trace:\n${e.stackTraceToString()}"
        }
    }

    fun loadGasPrice() {
        viewModelScope.launch {
            try {
                _gasPrice.value = ethereumBlockchainRepository.getCurrentGasPrice()
                updateApiStatus(true)
            } catch (e: Exception) {
                Log.e("BlockchainVM", "Error loading gas price: ${e.message}")
                _gasPrice.value = GasPrice(
                    safe = "30",
                    propose = "35",
                    fast = "40"
                )
                updateApiStatus(false)
            }
        }
    }

    fun getCachedGasPrice(): GasPrice {
        return _gasPrice.value ?: GasPrice(
            safe = "30",
            propose = "35",
            fast = "40"
        )
    }

    // === DEBUG METHODS ===

    suspend fun getRawApiData(wallet: CryptoWallet, api: String): String {
        return try {
            when (api) {
                "etherscan" -> {
                    if (wallet is EthereumWallet || wallet is MultiChainWallet) {
                        val address = getEthereumAddress(wallet)
                        if (address.isNotEmpty()) {
                            val response = ethereumBlockchainRepository.getEthereumBalance(address)
                            val json = Json { prettyPrint = true }
                            json.encodeToString(
                                RawBalanceResponse(
                                    address = address,
                                    balance = response.toPlainString(),
                                    source = "Etherscan API",
                                    timestamp = System.currentTimeMillis()
                                )
                            )
                        } else {
                            "Not an Ethereum wallet or no Ethereum address found"
                        }
                    } else {
                        "Not an Ethereum wallet"
                    }
                }

                else -> "Unknown API: $api"
            }
        } catch (e: Exception) {
            "Error fetching data from $api: ${e.message}\n\nStack Trace:\n${e.stackTraceToString()}"
        }
    }

    private suspend fun testEtherscanApi(): ApiTestResult {
        return try {
            // Test with a known Ethereum address (Vitalik's address)
            val testAddress = "0xd8dA6BF26964aF9D7eEd9e03E53415D37aA96045"
            val startTime = System.currentTimeMillis()
            val balance = ethereumBlockchainRepository.getEthereumBalance(testAddress)
            val duration = System.currentTimeMillis() - startTime

            ApiTestResult(
                name = "Etherscan",
                isConnected = true,
                responseTime = "${duration}ms",
                lastBlock = "Latest",
                message = "✓ Connected - Balance: ${balance.toPlainString()} ETH"
            )
        } catch (e: Exception) {
            ApiTestResult(
                name = "Etherscan",
                isConnected = false,
                responseTime = "N/A",
                lastBlock = "Unknown",
                message = "✗ Failed: ${e.message ?: "Unknown error"}"
            )
        }
    }

    // Helper methods
    private fun getEthereumAddress(wallet: CryptoWallet): String {
        return when (wallet) {
            is EthereumWallet -> wallet.address
            is MultiChainWallet -> wallet.ethereumWallet?.address ?: ""
            else -> ""
        }
    }

    private fun getBitcoinAddress(wallet: CryptoWallet): String {
        return when (wallet) {
            is BitcoinWallet -> wallet.address
            is MultiChainWallet -> wallet.bitcoinWallet?.address ?: ""
            else -> ""
        }
    }

    private fun getWalletAddress(wallet: CryptoWallet): String {
        return getEthereumAddress(wallet).ifEmpty { getBitcoinAddress(wallet) }
    }

    private fun updateApiStatus(success: Boolean) {
        _apiStatus.value = if (success) ApiStatus.CONNECTED else ApiStatus.ERROR
    }

    fun validateAddress(address: String, chain: ChainType): Boolean {
        return when (chain) {
            ChainType.ETHEREUM -> ethereumBlockchainRepository.isValidEthereumAddress(address)
            ChainType.BITCOIN -> ethereumBlockchainRepository.isValidBitcoinAddress(address)
            else -> address.isNotBlank()
        }
    }

    fun clearError() {
        _error.value = null
    }
}

enum class ApiStatus {
    CONNECTED, CONNECTING, ERROR, DISCONNECTED
}

@Serializable
data class ApiTestResult(
    val name: String,
    val isConnected: Boolean,
    val responseTime: String,
    val lastBlock: String,
    val message: String
)

@kotlinx.serialization.Serializable
data class RawBalanceResponse(
    val address: String,
    val balance: String,
    val source: String,
    val timestamp: Long
)

@kotlinx.serialization.Serializable
data class RawTokenResponse(
    val address: String,
    val tokens: List<TokenBalance>,
    val source: String,
    val timestamp: Long
)