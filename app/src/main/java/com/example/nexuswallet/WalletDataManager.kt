package com.example.nexuswallet


import android.content.Context
import com.example.nexuswallet.data.model.BitcoinWallet
import com.example.nexuswallet.data.model.CryptoWallet
import com.example.nexuswallet.data.model.EthereumNetwork
import com.example.nexuswallet.data.model.EthereumWallet
import com.example.nexuswallet.data.model.MultiChainWallet
import com.example.nexuswallet.data.model.Transaction
import com.example.nexuswallet.data.model.WalletBalance
import com.example.nexuswallet.data.model.WalletSettings
import com.example.nexuswallet.data.repository.WalletRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.math.BigDecimal

class WalletDataManager(context: Context) {

    private val appContext = context.applicationContext
    val walletManager = WalletManager(appContext)
    val repository = WalletRepository()
    val storage = WalletStorage(appContext)
    val securityManager = NexusWalletApplication.instance.securityManager

    init {
        loadDataFromStorage()
    }

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
    private fun loadDataFromStorage() {
        // Load wallets
        val wallets = storage.loadAllWallets()
        wallets.forEach { repository.createWallet(it) }

        // Load balances
        wallets.forEach { wallet ->
            val balance = storage.loadWalletBalance(wallet.id)
            if (balance != null) {
                repository.setWalletBalance(balance)
            }
        }

        // Load transactions
        wallets.forEach { wallet ->
            val transactions = storage.loadTransactions(wallet.id)
            transactions.forEach { tx -> repository.addTransaction(wallet.id, tx) }
        }
    }

    fun generateNewMnemonic(wordCount: Int = 12): List<String> {
        return walletManager.generateMnemonic(wordCount)
    }

    fun validateMnemonic(mnemonic: List<String>): Boolean {
        return walletManager.validateMnemonic(mnemonic)
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

    // WALLET MANAGEMENT
    fun saveWallet(wallet: CryptoWallet) {
        storage.saveWallet(wallet)
        repository.createWallet(wallet)
    }

    fun getWallet(walletId: String): CryptoWallet? {
        return repository.getWallet(walletId)
    }

    fun getAllWallets(): List<CryptoWallet> {
        return repository.getAllWallets()
    }

    fun getWalletsFlow(): StateFlow<List<CryptoWallet>> {
        return repository.walletsFlow
    }

    fun deleteWallet(walletId: String) {
        storage.deleteWallet(walletId)
        repository.deleteWallet(walletId)
    }

    // BALANCE MANAGEMENT
    fun saveWalletBalance(balance: WalletBalance) {
        storage.saveWalletBalance(balance)
        repository.setWalletBalance(balance)
    }

    fun getWalletBalance(walletId: String): WalletBalance? {
        return repository.getWalletBalance(walletId) ?: storage.loadWalletBalance(walletId)
    }

    fun createSampleBalance(walletId: String, address: String): WalletBalance {
        val nativeBalance = when {
            address.startsWith("bc1") -> "150000000" // 1.5 BTC in satoshis
            address.startsWith("0x") -> "2500000000000000000" // 2.5 ETH in wei
            else -> "0"
        }

        val nativeDecimal = repository.formatBalance(nativeBalance, 18)
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
        transactions.forEach { repository.addTransaction(walletId, it) }
    }

    fun getTransactions(walletId: String): List<Transaction> {
        return repository.getTransactions(walletId)
    }

    // SETTINGS MANAGEMENT
    fun saveSettings(settings: WalletSettings) {
        storage.saveSettings(settings)
    }

    fun getSettings(walletId: String): WalletSettings? {
        return storage.loadSettings(walletId)
    }

    // HELPER METHODS
    fun hasWallets(): Boolean = repository.hasWallets()

    fun getWalletCount(): Int = repository.getWalletCount()

    fun calculateTotalPortfolioValue(): BigDecimal {
        return repository.calculateTotalPortfolioValue()
    }

    // Formatting helpers
    fun formatBalance(balanceStr: String, decimals: Int): String {
        return repository.formatBalance(balanceStr, decimals)
    }

    fun convertToDecimal(balanceStr: String, decimals: Int): String {
        return repository.convertToDecimal(balanceStr, decimals)
    }

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
}