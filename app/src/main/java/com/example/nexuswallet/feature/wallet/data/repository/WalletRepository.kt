package com.example.nexuswallet.feature.wallet.data.repository

import com.example.nexuswallet.feature.wallet.domain.CryptoWallet
import com.example.nexuswallet.feature.wallet.domain.Transaction
import com.example.nexuswallet.feature.wallet.domain.WalletBalance
import com.example.nexuswallet.feature.wallet.domain.WalletType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.math.BigDecimal
import java.math.BigInteger
import java.util.UUID
import kotlin.collections.sumOf

class WalletRepository {

    // Store wallets by ID
    private val wallets = mutableMapOf<String, CryptoWallet>()
    private val _walletsFlow = MutableStateFlow<List<CryptoWallet>>(emptyList())
    val walletsFlow: StateFlow<List<CryptoWallet>> = _walletsFlow.asStateFlow()

    // Store balances by wallet ID
    private val balances = mutableMapOf<String, WalletBalance>()

    // Store transactions by wallet ID
    private val transactions = mutableMapOf<String, List<Transaction>>()

    // Simple ID generator
    fun generateWalletId(): String = "wallet_${UUID.randomUUID().toString().take(8)}"

    // WALLET MANAGEMENT
    fun createWallet(wallet: CryptoWallet) {
        wallets[wallet.id] = wallet
        _walletsFlow.value = wallets.values.toList()
    }

    fun getWallet(walletId: String): CryptoWallet? = wallets[walletId]

    fun getAllWallets(): List<CryptoWallet> = wallets.values.toList()

    fun deleteWallet(walletId: String) {
        wallets.remove(walletId)
        balances.remove(walletId)
        transactions.remove(walletId)
        _walletsFlow.value = wallets.values.toList()
    }

    fun updateWallet(wallet: CryptoWallet) {
        wallets[wallet.id] = wallet
        _walletsFlow.value = wallets.values.toList()
    }

    // BALANCE MANAGEMENT
    fun setWalletBalance(balance: WalletBalance) {
        balances[balance.walletId] = balance
    }

    fun getWalletBalance(walletId: String): WalletBalance? = balances[walletId]

    fun getAllBalances(): Map<String, WalletBalance> = balances

    fun calculateTotalPortfolioValue(): BigDecimal {
        return balances.values.sumOf { balance ->
            BigDecimal(balance.usdValue.toString())
        }
    }

    // TRANSACTION MANAGEMENT
    fun addTransaction(walletId: String, transaction: Transaction) {
        val current = transactions[walletId]?.toMutableList() ?: mutableListOf()
        current.add(transaction)
        transactions[walletId] = current
    }

    fun getTransactions(walletId: String): List<Transaction> {
        return transactions[walletId] ?: emptyList()
    }

    fun getRecentTransactions(limit: Int = 10): List<Transaction> {
        return transactions.values.flatten()
            .sortedByDescending { it.timestamp }
            .take(limit)
    }

    // HELPER METHODS
    fun getWalletsByType(type: WalletType): List<CryptoWallet> {
        return wallets.values.filter { it.walletType == type }
    }

    fun hasWallet(walletId: String): Boolean = wallets.containsKey(walletId)

    fun getWalletCount(): Int = wallets.size

    fun hasWallets(): Boolean = wallets.isNotEmpty()

    // Balance formatting helpers
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
}