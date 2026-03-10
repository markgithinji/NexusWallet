package com.example.nexuswallet.feature.coin.solana

import com.example.nexuswallet.feature.coin.BroadcastResult
import com.example.nexuswallet.feature.coin.Result
import com.example.nexuswallet.feature.coin.FeeLevel
import org.sol4k.Keypair
import java.math.BigDecimal
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.SolanaNetwork
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

interface SolanaBlockchainRepository {
    // ===== RPC Methods (using sol4k) =====
    suspend fun getRecentBlockhash(network: SolanaNetwork): Result<String>

    suspend fun getBalance(
        address: String,
        network: SolanaNetwork
    ): Result<BigDecimal>

    suspend fun getTokenBalance(
        address: String,
        mintAddress: String,
        network: SolanaNetwork
    ): Result<BigDecimal>

    suspend fun getFeeEstimate(
        feeLevel: FeeLevel,
        network: SolanaNetwork
    ): Result<SolanaFeeEstimate>

    suspend fun createAndSignTransaction(
        fromKeypair: Keypair,
        toAddress: String,
        lamports: Long,
        network: SolanaNetwork
    ): Result<SolanaSignedTransaction>

    suspend fun broadcastTransaction(
        signedTransaction: SolanaSignedTransaction,
        network: SolanaNetwork
    ): Result<BroadcastResult>

    fun validateAddress(address: String): Result<Boolean>

    // ===== HELIUS API METHODS =====
    suspend fun getTransactions(
        address: String,
        network: SolanaNetwork,
        limit: Int = 50
    ): Result<List<HeliusTransaction>>

    suspend fun getTransaction(
        signature: String,
        network: SolanaNetwork
    ): Result<HeliusTransaction>

    fun parseTransfer(
        transaction: HeliusTransaction,
        walletAddress: String
    ): TransferInfo?
}