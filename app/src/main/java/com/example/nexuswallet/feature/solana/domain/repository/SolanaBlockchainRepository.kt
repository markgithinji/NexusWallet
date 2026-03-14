package com.example.nexuswallet.feature.solana.domain.repository

import com.example.nexuswallet.feature.core.domain.model.BroadcastResult
import com.example.nexuswallet.feature.core.domain.model.FeeLevel
import com.example.nexuswallet.feature.core.util.Result
import com.example.nexuswallet.feature.solana.data.model.SolanaSignedTransaction
import com.example.nexuswallet.feature.solana.data.remote.model.HeliusTransactionResponse
import com.example.nexuswallet.feature.solana.domain.model.SolanaFeeEstimate
import com.example.nexuswallet.feature.solana.domain.model.SolanaNetwork
import com.example.nexuswallet.feature.solana.domain.model.SolanaTransaction
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
        signedTransaction: SolanaSignedTransaction,
        network: SolanaNetwork
    ): Result<BroadcastResult>

    fun validateAddress(address: String): Result<Boolean>

    suspend fun getTransactions(
        walletId: String,
        address: String,
        network: SolanaNetwork,
        limit: Int
    ): Result<List<SolanaTransaction>>

    suspend fun getTransaction(
        signature: String,
        network: SolanaNetwork
    ): Result<HeliusTransactionResponse>

    fun parseTransfer(
        transaction: HeliusTransactionResponse,
        walletAddress: String
    ): com.example.nexuswallet.feature.solana.domain.model.TransferInfo?
}