package com.example.nexuswallet.feature.coin.solana

import com.example.nexuswallet.feature.coin.BroadcastResult
import com.example.nexuswallet.feature.coin.Result
import com.example.nexuswallet.feature.coin.bitcoin.FeeLevel
import org.sol4k.Keypair
import java.math.BigDecimal
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.SolanaNetwork
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

interface SolanaBlockchainRepository {
    suspend fun getRecentBlockhash(network: SolanaNetwork): Result<String>
    suspend fun getBalance(
        address: String,
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

    suspend fun getTransactionSignatures(
        address: String,
        network: SolanaNetwork,
        limit: Int
    ): Result<List<SolanaTransactionResponse>>

    suspend fun getTransactionDetails(
        signature: String,
        network: SolanaNetwork
    ): Result<SolanaTransactionDetailsResponse>

    suspend fun getFullTransactionHistory(
        address: String,
        network: SolanaNetwork,
        limit: Int
    ): Result<List<Pair<SolanaTransactionResponse, SolanaTransactionDetailsResponse?>>>

    fun parseTransferFromDetails(
        details: SolanaTransactionDetailsResponse,
        walletAddress: String
    ): TransferInfo?

    suspend fun getTokenBalance(
        address: String,
        mintAddress: String,
        network: SolanaNetwork
    ): Result<BigDecimal>

    fun validateAddress(address: String): Result<Boolean>
}