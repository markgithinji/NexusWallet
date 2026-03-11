package com.example.nexuswallet.feature.solana.domain.repository

import com.example.nexuswallet.feature.core.domain.model.BroadcastResult
import com.example.nexuswallet.feature.core.domain.model.FeeLevel
import com.example.nexuswallet.feature.core.util.Result
import com.example.nexuswallet.feature.solana.data.model.SolanaSignedTransaction
import com.example.nexuswallet.feature.solana.data.remote.HeliusTransactionResponse
import com.example.nexuswallet.feature.solana.domain.model.SolanaFeeEstimate
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.SolanaNetwork
import org.sol4k.Keypair
import java.math.BigDecimal

interface SolanaBlockchainRepository {
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
        signedTransaction: com.example.nexuswallet.feature.solana.data.model.SolanaSignedTransaction,
        network: SolanaNetwork
    ): Result<BroadcastResult>

    fun validateAddress(address: String): Result<Boolean>

    suspend fun getTransactions(
        address: String,
        network: SolanaNetwork,
        limit: Int = 50
    ): Result<List<HeliusTransactionResponse>>

    suspend fun getTransaction(
        signature: String,
        network: SolanaNetwork
    ): Result<HeliusTransactionResponse>

    fun parseTransfer(
        transaction: com.example.nexuswallet.feature.solana.data.remote.HeliusTransactionResponse,
        walletAddress: String
    ): com.example.nexuswallet.feature.solana.domain.model.TransferInfo?
}