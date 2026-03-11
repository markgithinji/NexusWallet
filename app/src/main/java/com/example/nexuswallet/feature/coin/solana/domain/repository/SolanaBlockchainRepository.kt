package com.example.nexuswallet.feature.coin.solana.domain.repository

import com.example.nexuswallet.feature.coin.BroadcastResult
import com.example.nexuswallet.feature.coin.FeeLevel
import com.example.nexuswallet.feature.coin.Result
import com.example.nexuswallet.feature.coin.solana.SolanaFeeEstimate
import com.example.nexuswallet.feature.coin.solana.TransferInfo
import com.example.nexuswallet.feature.coin.solana.data.model.SolanaSignedTransaction
import com.example.nexuswallet.feature.coin.solana.data.remote.HeliusTransactionResponse
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
        signedTransaction: SolanaSignedTransaction,
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
        transaction: HeliusTransactionResponse,
        walletAddress: String
    ): TransferInfo?
}