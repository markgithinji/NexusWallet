package com.example.nexuswallet.feature.wallet.data.repository

import android.content.Context
import com.example.nexuswallet.NexusWalletApplication
import com.example.nexuswallet.feature.authentication.domain.BackupResult
import com.example.nexuswallet.feature.wallet.domain.BitcoinWallet
import com.example.nexuswallet.feature.wallet.domain.CryptoWallet
import com.example.nexuswallet.feature.wallet.domain.EthereumNetwork
import com.example.nexuswallet.feature.wallet.domain.EthereumWallet
import com.example.nexuswallet.feature.wallet.domain.MultiChainWallet
import com.example.nexuswallet.feature.wallet.domain.Transaction
import com.example.nexuswallet.feature.wallet.domain.WalletBalance
import com.example.nexuswallet.feature.wallet.domain.WalletManager
import com.example.nexuswallet.feature.wallet.domain.WalletSettings
import com.example.nexuswallet.feature.wallet.domain.WalletStorage
import com.example.nexuswallet.feature.wallet.domain.WalletType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.math.BigInteger
import java.util.UUID
import kotlin.collections.sumOf

class WalletRepository(context: Context) {
    private val appContext = context.applicationContext
    private val storage = WalletStorage(appContext)
    private val walletManager = WalletManager(appContext)
    private val securityManager = NexusWalletApplication.instance.securityManager

    // Create a Flow for reactive UI updates
    private val _walletsFlow = MutableStateFlow<List<CryptoWallet>>(emptyList())
    val walletsFlow: StateFlow<List<CryptoWallet>> = _walletsFlow

    init {
        loadInitialData()
    }

    private fun loadInitialData() {
        val wallets = storage.loadAllWallets()
        _walletsFlow.value = wallets
    }

    // Wallet CRUD operations
    fun saveWallet(wallet: CryptoWallet) {
        storage.saveWallet(wallet)
        updateWalletsFlow(wallet)
    }

    fun getWallet(walletId: String): CryptoWallet? {
        return storage.loadWallet(walletId)
    }

    fun getAllWallets(): List<CryptoWallet> {
        return storage.loadAllWallets()
    }

    fun deleteWallet(walletId: String) {
        storage.deleteWallet(walletId)
        removeWalletFromFlow(walletId)
    }

    // Balance operations
    fun saveWalletBalance(balance: WalletBalance) {
        storage.saveWalletBalance(balance)
    }

    fun getWalletBalance(walletId: String): WalletBalance? {
        return storage.loadWalletBalance(walletId)
    }

    // Transaction operations
    fun saveTransactions(walletId: String, transactions: List<Transaction>) {
        storage.saveTransactions(walletId, transactions)
    }

    fun getTransactions(walletId: String): List<Transaction> {
        return storage.loadTransactions(walletId)
    }

    // Settings operations
    fun saveSettings(settings: WalletSettings) {
        storage.saveSettings(settings)
    }

    fun getSettings(walletId: String): WalletSettings? {
        return storage.loadSettings(walletId)
    }

    // Mnemonic operations
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

    // Helper queries
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

    // Create helper methods (delegate to WalletManager)
    fun generateNewMnemonic(wordCount: Int = 12): List<String> {
        return walletManager.generateMnemonic(wordCount)
    }

    fun validateMnemonic(mnemonic: List<String>): Boolean {
        return walletManager.validateMnemonic(mnemonic)
    }

    fun createBitcoinWallet(mnemonic: List<String>, name: String): BitcoinWallet {
        val wallet = walletManager.createBitcoinWallet(mnemonic, name)

        // Secure the mnemonic
        CoroutineScope(Dispatchers.IO).launch {
            securityManager.secureMnemonic(wallet.id, mnemonic)
        }

        return wallet
    }

    fun createEthereumWallet(
        mnemonic: List<String>,
        name: String,
        network: EthereumNetwork = EthereumNetwork.MAINNET
    ): EthereumWallet {
        return walletManager.createEthereumWallet(mnemonic, name, network)
    }

    fun createMultiChainWallet(
        mnemonic: List<String>,
        name: String
    ): MultiChainWallet {
        return walletManager.createMultiChainWallet(mnemonic, name)
    }

    // Formatting helpers
    fun formatBalance(balanceStr: String, decimals: Int): String {
        return walletManager.formatBalance(balanceStr, decimals)
    }

    fun convertToDecimal(balanceStr: String, decimals: Int): String {
        return walletManager.convertToDecimal(balanceStr, decimals)
    }

    // Flow update helpers
    private fun updateWalletsFlow(wallet: CryptoWallet) {
        val currentWallets = _walletsFlow.value.toMutableList()
        val existingIndex = currentWallets.indexOfFirst { it.id == wallet.id }

        if (existingIndex >= 0) {
            currentWallets[existingIndex] = wallet
        } else {
            currentWallets.add(wallet)
        }

        _walletsFlow.value = currentWallets
    }

    private fun removeWalletFromFlow(walletId: String) {
        val currentWallets = _walletsFlow.value.toMutableList()
        currentWallets.removeAll { it.id == walletId }
        _walletsFlow.value = currentWallets
    }

    companion object {
        @Volatile
        private var INSTANCE: WalletRepository? = null

        fun initialize(context: Context) {
            INSTANCE = WalletRepository(context.applicationContext)
        }

        fun getInstance(): WalletRepository {
            return INSTANCE ?: throw IllegalStateException("WalletRepository not initialized. Call initialize() first.")
        }
    }
}