package com.example.nexuswallet.feature.wallet.domain


import android.content.Context
import com.example.nexuswallet.feature.authentication.domain.BackupResult
import com.example.nexuswallet.NexusWalletApplication
import com.example.nexuswallet.feature.wallet.data.repository.WalletRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.math.BigInteger

class WalletDataManager(context: Context) {
    private val appContext = context.applicationContext
    private val storage = WalletStorage(appContext)
    private val walletManager = WalletManager(appContext)
    private val securityManager = NexusWalletApplication.instance.securityManager

    companion object {
        @Volatile
        private var INSTANCE: WalletDataManager? = null

        fun initialize(context: Context) {
            INSTANCE = WalletDataManager(context.applicationContext)
        }

        fun getInstance(): WalletDataManager {
            return INSTANCE ?: throw IllegalStateException("WalletDataManager not initialized. Call initialize() first.")
        }
    }

    // Create a Flow for reactive UI updates
    private val _walletsFlow = MutableStateFlow<List<CryptoWallet>>(emptyList())
    val walletsFlow: StateFlow<List<CryptoWallet>> = _walletsFlow

    init {
        // Load initial data and update flow
        loadInitialData()
    }

    private fun loadInitialData() {
        val wallets = storage.loadAllWallets()
        _walletsFlow.value = wallets
    }

    fun generateNewMnemonic(wordCount: Int = 12): List<String> {
        return walletManager.generateMnemonic(wordCount)
    }

    fun validateMnemonic(mnemonic: List<String>): Boolean {
        return walletManager.validateMnemonic(mnemonic)
    }

    // WALLET MANAGEMENT
    fun saveWallet(wallet: CryptoWallet) {
        // Save to persistent storage
        storage.saveWallet(wallet)

        // Update the Flow
        val currentWallets = _walletsFlow.value.toMutableList()
        val existingIndex = currentWallets.indexOfFirst { it.id == wallet.id }

        if (existingIndex >= 0) {
            currentWallets[existingIndex] = wallet
        } else {
            currentWallets.add(wallet)
        }

        _walletsFlow.value = currentWallets
    }

    fun getWallet(walletId: String): CryptoWallet? {
        return storage.loadWallet(walletId)
    }

    fun getAllWallets(): List<CryptoWallet> {
        return storage.loadAllWallets()
    }

    fun deleteWallet(walletId: String) {
        // Delete from persistent storage
        storage.deleteWallet(walletId)

        // Update the Flow
        val currentWallets = _walletsFlow.value.toMutableList()
        currentWallets.removeAll { it.id == walletId }
        _walletsFlow.value = currentWallets
    }

    // BALANCE MANAGEMENT
    fun saveWalletBalance(balance: WalletBalance) {
        storage.saveWalletBalance(balance)
    }

    fun getWalletBalance(walletId: String): WalletBalance? {
        return storage.loadWalletBalance(walletId)
    }

    fun createSampleBalance(walletId: String, address: String): WalletBalance {
        val nativeBalance = when {
            address.startsWith("bc1") -> "150000000" // 1.5 BTC in satoshis
            address.startsWith("0x") -> "2500000000000000000" // 2.5 ETH in wei
            else -> "0"
        }

        val nativeDecimal = formatBalance(nativeBalance, 18)
        val usdValue = if (address.startsWith("bc1")) 45000.0 else 8750.0

        return WalletBalance(
            walletId = walletId,
            address = address,
            nativeBalance = nativeBalance,
            nativeBalanceDecimal = nativeDecimal,
            usdValue = usdValue,
            tokens = emptyList()
        )
    }

    // TRANSACTION MANAGEMENT
    fun saveTransactions(walletId: String, transactions: List<Transaction>) {
        storage.saveTransactions(walletId, transactions)
    }

    fun getTransactions(walletId: String): List<Transaction> {
        return storage.loadTransactions(walletId)
    }

    // WALLET CREATION METHODS
    fun createBitcoinWallet(
        mnemonic: List<String>,
        name: String
    ): BitcoinWallet {
        val wallet = walletManager.createBitcoinWallet(mnemonic, name)

        // Secure the mnemonic
        CoroutineScope(Dispatchers.IO).launch {
            securityManager.secureMnemonic(wallet.id, mnemonic)
        }

        saveWallet(wallet)
        return wallet
    }

    fun createEthereumWallet(
        mnemonic: List<String>,
        name: String,
        network: EthereumNetwork = EthereumNetwork.MAINNET
    ): EthereumWallet {
        val wallet = walletManager.createEthereumWallet(mnemonic, name, network)
        saveWallet(wallet)
        return wallet
    }

    fun createMultiChainWallet(
        mnemonic: List<String>,
        name: String
    ): MultiChainWallet {
        val wallet = walletManager.createMultiChainWallet(mnemonic, name)
        saveWallet(wallet)
        return wallet
    }

    // SECURITY METHODS
    suspend fun getMnemonic(walletId: String): List<String>? {
        return securityManager.retrieveMnemonic(walletId)
    }

    suspend fun createBackup(walletId: String): BackupResult {
        val wallet = getWallet(walletId)
        return if (wallet != null) {
            securityManager.createEncryptedBackup(walletId, wallet)
        } else {
            BackupResult.Error(IllegalArgumentException("Wallet not found"))
        }
    }

    // HELPER METHODS
    fun hasWallets(): Boolean {
        return getAllWallets().isNotEmpty()
    }

    fun getWalletCount(): Int {
        return getAllWallets().size
    }

    fun calculateTotalPortfolioValue(): BigDecimal {
        val wallets = getAllWallets()
        var total = BigDecimal.ZERO

        wallets.forEach { wallet ->
            val balance = getWalletBalance(wallet.id)
            if (balance != null) {
                total = total.add(BigDecimal(balance.usdValue.toString()))
            }
        }

        return total
    }

    // Formatting helpers (moved from repository)
    fun formatBalance(balanceStr: String, decimals: Int): String {
        return try {
            val bigInt = BigInteger(balanceStr)
            val divisor = BigInteger.TEN.pow(decimals)
            val integerPart = bigInt.divide(divisor)
            val fractionalPart = bigInt.mod(divisor)

            if (fractionalPart == BigInteger.ZERO) {
                integerPart.toString()
            } else {
                val fractionalStr = fractionalPart.toString().padStart(decimals, '0')
                    .trimEnd('0')
                "$integerPart.$fractionalStr"
            }
        } catch (e: Exception) {
            "0"
        }
    }

    fun convertToDecimal(balanceStr: String, decimals: Int): String {
        return try {
            val bigInt = BigInteger(balanceStr)
            val divisor = BigDecimal(BigInteger.TEN.pow(decimals))
            BigDecimal(bigInt).divide(divisor).toString()
        } catch (e: Exception) {
            "0"
        }
    }

    // SETTINGS MANAGEMENT (keep if needed)
    fun saveSettings(settings: WalletSettings) {
        storage.saveSettings(settings)
    }

    fun getSettings(walletId: String): WalletSettings? {
        return storage.loadSettings(walletId)
    }
}