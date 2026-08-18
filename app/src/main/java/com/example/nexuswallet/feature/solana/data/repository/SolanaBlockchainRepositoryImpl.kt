package com.example.nexuswallet.feature.solana.data.repository

import com.example.nexuswallet.feature.core.domain.di.IoDispatcher
import com.example.nexuswallet.feature.core.domain.model.BroadcastResult
import com.example.nexuswallet.feature.core.domain.model.FeeLevel
import com.example.nexuswallet.feature.core.domain.model.SolanaTransaction
import com.example.nexuswallet.feature.core.util.Result
import com.example.nexuswallet.feature.core.util.SafeApiCall
import com.example.nexuswallet.feature.solana.data.model.SolanaSignedTransaction
import com.example.nexuswallet.feature.solana.data.remote.HeliusApi
import com.example.nexuswallet.feature.solana.data.remote.model.HeliusTransactionRequest
import com.example.nexuswallet.feature.solana.data.remote.model.HeliusTransactionResponse
import com.example.nexuswallet.feature.solana.data.toDomain
import com.example.nexuswallet.feature.solana.domain.model.SolanaFeeEstimate
import com.example.nexuswallet.feature.solana.domain.model.TransferInfo
import com.example.nexuswallet.feature.solana.domain.repository.SolanaBlockchainRepository
import com.example.nexuswallet.feature.solana.util.SolanaConstants.LAMPORTS_PER_SOL
import com.example.nexuswallet.feature.wallet.domain.model.SolanaNetwork
import com.example.nexuswallet.feature.wallet.domain.model.TransactionStatus
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.sol4k.Base58
import org.sol4k.Connection
import org.sol4k.Keypair
import org.sol4k.PublicKey
import org.sol4k.TransactionMessage
import org.sol4k.VersionedTransaction
import org.sol4k.instruction.SetComputeUnitLimitInstruction
import org.sol4k.instruction.SetComputeUnitPriceInstruction
import org.sol4k.instruction.TransferInstruction
import java.math.BigDecimal
import java.math.RoundingMode
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

@Singleton
class SolanaBlockchainRepositoryImpl @Inject constructor(
    @param:Named("helius_rpc_devnet") private val rpcDevnetConnection: Connection,
    @param:Named("helius_rpc_mainnet") private val rpcMainnetConnection: Connection,
    @param:Named("helius_api_devnet") private val devnetApi: HeliusApi,
    @param:Named("helius_api_mainnet") private val mainnetApi: HeliusApi,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : SolanaBlockchainRepository {

    private fun getRpcConnection(network: SolanaNetwork): Connection {
        return when (network) {
            SolanaNetwork.Mainnet -> rpcMainnetConnection
            SolanaNetwork.Devnet -> rpcDevnetConnection
        }
    }

    private fun getHeliusApi(network: SolanaNetwork): HeliusApi {
        return when (network) {
            SolanaNetwork.Mainnet -> mainnetApi
            SolanaNetwork.Devnet -> devnetApi
        }
    }

    override suspend fun getRecentBlockhash(network: SolanaNetwork): Result<String> =
        withContext(ioDispatcher) {
            SafeApiCall.make {
                val connection = getRpcConnection(network)
                val blockhash = connection.getLatestBlockhash()
                blockhash
            }
        }

    override suspend fun getBalance(
        address: String,
        network: SolanaNetwork
    ): Result<BigDecimal> = withContext(ioDispatcher) {
        SafeApiCall.make {
            val connection = getRpcConnection(network)
            val publicKey = PublicKey(address)
            val balance = connection.getBalance(publicKey)

            val result = BigDecimal(balance).divide(
                BigDecimal(LAMPORTS_PER_SOL),
                SOL_DECIMALS,
                RoundingMode.HALF_UP
            )
            
            result
        }
    }

    override suspend fun getTransactionStatus(
        signature: String,
        network: SolanaNetwork
    ): Result<TransactionStatus> = withContext(ioDispatcher) {
        val result = getTransaction(signature, network)
        when (result) {
            is Result.Success -> {
                val tx = result.data
                if (tx.transactionError != null) {
                    Result.Success(TransactionStatus.FAILED)
                } else {
                    Result.Success(TransactionStatus.SUCCESS)
                }
            }
            is Result.Error -> {
                // If not found yet, it's pending
                if (result.message.contains("not found", ignoreCase = true)) {
                    Result.Success(TransactionStatus.PENDING)
                } else {
                    Result.Error(result.message)
                }
            }
            else -> Result.Success(TransactionStatus.PENDING)
        }
    }

    override suspend fun getTokenBalance(
        address: String,
        mintAddress: String,
        network: SolanaNetwork
    ): Result<BigDecimal> = withContext(ioDispatcher) {
        SafeApiCall.make {
            val connection = getRpcConnection(network)

            val owner = PublicKey(address)
            val mint = PublicKey(mintAddress)

            val (associatedTokenAccount, _) = PublicKey.findProgramDerivedAddress(
                holderAddress = owner,
                tokenMintAddress = mint
            )

            val tokenBalance = connection.getTokenAccountBalance(associatedTokenAccount)

            BigDecimal(tokenBalance.amount).divide(
                BigDecimal.TEN.pow(tokenBalance.decimals),
                tokenBalance.decimals,
                RoundingMode.HALF_UP
            )
        }
    }

    override suspend fun getFeeEstimate(
        feeLevel: FeeLevel,
        network: SolanaNetwork,
        fromAddress: String?,
        toAddress: String?,
        lamports: Long?,
        tokenMint: String?
    ): Result<SolanaFeeEstimate> = withContext(ioDispatcher) {
        SafeApiCall.make {
            val baseFeeLamports = SOLANA_FIXED_FEE_LAMPORTS

            val percentile = when (feeLevel) {
                FeeLevel.SLOW -> SLOW_PERCENTILE
                FeeLevel.NORMAL -> NORMAL_PERCENTILE
                FeeLevel.FAST -> FAST_PERCENTILE
            }

            val priorityFeeRate = if (percentile > 0) {
                val result = getRecommendedPriorityFee(percentile, toAddress, network)
                if (result is Result.Success) result.data else DEFAULT_PRIORITY_FEE
            } else DEFAULT_PRIORITY_FEE

            // Dynamic Compute Unit Estimation
            val computeUnits = if (fromAddress != null && toAddress != null) {
                estimateComputeUnits(fromAddress, toAddress, lamports ?: 0L, network, tokenMint)
            } else {
                when (feeLevel) {
                    FeeLevel.SLOW -> SLOW_COMPUTE_UNITS
                    FeeLevel.NORMAL -> NORMAL_COMPUTE_UNITS
                    FeeLevel.FAST -> FAST_COMPUTE_UNITS
                }
            }

            val priorityFeeLamports =
                (priorityFeeRate.toLong() * computeUnits) / MICRO_LAMPORTS_PER_LAMPORT
            val totalFeeLamports = baseFeeLamports + priorityFeeLamports

            val totalFeeSol = BigDecimal(totalFeeLamports).divide(
                BigDecimal(LAMPORTS_PER_SOL),
                SOL_DECIMALS,
                RoundingMode.HALF_UP
            ).toPlainString()

            val estimatedTime = when (feeLevel) {
                FeeLevel.SLOW -> SLOW_ESTIMATED_TIME_SECONDS
                FeeLevel.NORMAL -> NORMAL_ESTIMATED_TIME_SECONDS
                FeeLevel.FAST -> FAST_ESTIMATED_TIME_SECONDS
            }

            SolanaFeeEstimate(
                feeLamports = totalFeeLamports,
                feeSol = totalFeeSol,
                priorityFeeRate = priorityFeeRate.toLong(),
                estimatedTime = estimatedTime,
                priority = feeLevel,
                computeUnits = computeUnits
            )
        }
    }

    private suspend fun estimateComputeUnits(
        fromAddress: String,
        toAddress: String,
        lamports: Long,
        network: SolanaNetwork,
        tokenMint: String? = null
    ): Int {
        return try {
            val connection = getRpcConnection(network)
            val fromPublicKey = PublicKey(fromAddress)
            val toPublicKey = PublicKey(toAddress)
            val blockhash = connection.getLatestBlockhash()

            val instructions = mutableListOf<org.sol4k.instruction.Instruction>()

            if (tokenMint == null) {
                instructions.add(TransferInstruction(fromPublicKey, toPublicKey, lamports))
            } else {
                val mint = PublicKey(tokenMint)
                val (receiverAta, _) = PublicKey.findProgramDerivedAddress(toPublicKey, mint)
                
                val receiverAtaInfo = connection.getAccountInfo(receiverAta)
                if (receiverAtaInfo == null) {
                    instructions.add(
                        org.sol4k.instruction.CreateAssociatedTokenAccountInstruction(
                            payer = fromPublicKey,
                            associatedToken = receiverAta,
                            owner = toPublicKey,
                            mint = mint
                        )
                    )
                }

                val (senderAta, _) = PublicKey.findProgramDerivedAddress(fromPublicKey, mint)
                instructions.add(
                    org.sol4k.instruction.SplTransferInstruction(
                        from = senderAta,
                        to = receiverAta,
                        mint = mint,
                        owner = fromPublicKey,
                        amount = lamports,
                        decimals = 0 // Simulation doesn't strictly validate decimals for CU count
                    )
                )
            }

            val message = TransactionMessage.newMessage(
                feePayer = fromPublicKey,
                recentBlockhash = blockhash,
                instructions = instructions
            )
            val transaction = VersionedTransaction(message)
            
            // Use sol4k simulation and parse logs for CU usage
            val result = connection.simulateTransaction(transaction)
            if (result is org.sol4k.api.TransactionSimulationSuccess) {
                // Regex to find "consumed 1234 compute units" in logs
                val cuRegex = Regex("""consumed (\d+)""")
                val consumed = result.logs.mapNotNull { line ->
                    cuRegex.find(line)?.groupValues?.get(1)?.toIntOrNull()
                }.maxOrNull()

                // If found, add 20% safety buffer for production reliability
                if (consumed != null) return (consumed * 1.2).toInt()
            }

            // Fallback to safe production constants if simulation logs are ambiguous
            val fallback = if (tokenMint == null) 1000 else 50000
            fallback
        } catch (e: Exception) {
            // Fallback to safe production constants if simulation fails
            if (tokenMint == null) 1000 else 50000
        }
    }

    private suspend fun getRecommendedPriorityFee(
        percentile: Int,
        toAddress: String?,
        network: SolanaNetwork
    ): Result<Int> = withContext(ioDispatcher) {
        SafeApiCall.make {
            val connection = getRpcConnection(network)

            val accounts = toAddress?.let {
                try {
                    listOf(PublicKey(it))
                } catch (e: Exception) {
                    emptyList()
                }
            } ?: emptyList()

            val recentFees = connection.getRecentPrioritizationFees(accounts)

            if (recentFees.isEmpty()) {
                return@make DEFAULT_PRIORITY_FEE
            }

            val feeValues = recentFees.map { it.prioritizationFee }.sorted()
            val index = (feeValues.size * percentile / PERCENTILE_DIVISOR).coerceIn(
                COERCE_MIN,
                feeValues.size - 1
            )
            val recommendedFee = feeValues[index].toInt()

            recommendedFee
        }
    }

    override suspend fun createAndSignTransaction(
        fromKeypair: Keypair,
        toAddress: String,
        lamports: Long,
        network: SolanaNetwork,
        priorityFeeRate: Long,
        computeUnitLimit: Int,
        tokenMint: String?,
        tokenDecimals: Int?
    ): Result<SolanaSignedTransaction> = withContext(ioDispatcher) {
        SafeApiCall.make {
            val connection = getRpcConnection(network)
            val blockhash = connection.getLatestBlockhash()
            val receiver = PublicKey(toAddress)

            val instructions = mutableListOf<org.sol4k.instruction.Instruction>()

            // 1. Set Compute Unit Price (Priority Fee)
            if (priorityFeeRate > 0) {
                instructions.add(SetComputeUnitPriceInstruction(priorityFeeRate))
            }

            // 2. Set Compute Unit Limit
            if (computeUnitLimit > 0) {
                instructions.add(SetComputeUnitLimitInstruction(computeUnitLimit.toLong()))
            }

            // 3. Add Transfer Instruction
            if (tokenMint == null) {
                // Native SOL transfer
                instructions.add(TransferInstruction(fromKeypair.publicKey, receiver, lamports))
            } else {
                // SPL Token transfer
                val mint = PublicKey(tokenMint)
                
                // Recipient's Associated Token Account
                val (receiverAta, _) = PublicKey.findProgramDerivedAddress(
                    holderAddress = receiver,
                    tokenMintAddress = mint
                )

                // Check if receiver's ATA exists
                val receiverAtaInfo = connection.getAccountInfo(receiverAta)
                if (receiverAtaInfo == null) {
                    // Bundle instruction to create ATA
                    instructions.add(
                        org.sol4k.instruction.CreateAssociatedTokenAccountInstruction(
                            payer = fromKeypair.publicKey,
                            associatedToken = receiverAta,
                            owner = receiver,
                            mint = mint
                        )
                    )
                }

                // Sender's Associated Token Account
                val (senderAta, _) = PublicKey.findProgramDerivedAddress(
                    holderAddress = fromKeypair.publicKey,
                    tokenMintAddress = mint
                )

                instructions.add(
                    org.sol4k.instruction.SplTransferInstruction(
                        from = senderAta,
                        to = receiverAta,
                        mint = mint,
                        owner = fromKeypair.publicKey,
                        amount = lamports,
                        decimals = tokenDecimals ?: 0
                    )
                )
            }

            val message = TransactionMessage.newMessage(
                feePayer = fromKeypair.publicKey,
                recentBlockhash = blockhash,
                instructions = instructions
            )

            val transaction = VersionedTransaction(message)
            transaction.sign(fromKeypair)

            val serializedTx = transaction.serialize()

            val signature = if (serializedTx.size >= SIGNATURE_SIZE) {
                Base58.encode(serializedTx.copyOfRange(0, SIGNATURE_SIZE))
            } else {
                val hash = MessageDigest.getInstance(SHA_256_ALGORITHM).digest(serializedTx)
                Base58.encode(hash)
            }

            SolanaSignedTransaction(
                signature = signature,
                serialize = { serializedTx }
            )
        }
    }

    override suspend fun broadcastTransaction(
        signedTransaction: SolanaSignedTransaction,
        network: SolanaNetwork
    ): Result<BroadcastResult> = withContext(ioDispatcher) {
        SafeApiCall.make {
            val connection = getRpcConnection(network)
            val serializedTx = signedTransaction.serialize()
            val signature = connection.sendTransaction(serializedTx)

            // Transaction Confirmation Loop
            // Solana transactions can be dropped during congestion, so we poll for status.
            // We use Helius API for polling
            var confirmed = false
            repeat(15) { // Poll for ~30 seconds
                val statusResult = getTransaction(signature, network)
                if (statusResult is Result.Success) {
                    val tx = statusResult.data
                    if (tx.transactionError != null) {
                        return@make BroadcastResult(
                            success = false,
                            hash = signature,
                            error = "Transaction failed on-chain: ${tx.transactionError}"
                        )
                    }
                    confirmed = true
                    return@make BroadcastResult(success = true, hash = signature)
                }
                delay(2000)
            }

            BroadcastResult(
                success = confirmed,
                hash = signature,
                error = if (!confirmed) "Transaction confirmation timed out" else null
            )
        }
    }

    override suspend fun getTransactions(
        walletId: String,
        address: String,
        network: SolanaNetwork,
        limit: Int
    ): Result<List<SolanaTransaction>> = withContext(ioDispatcher) {
        SafeApiCall.make {
            val api = getHeliusApi(network)
            val heliusTransactions = api.getTransactions(
                address = address,
                limit = limit
            )

            heliusTransactions.mapNotNull { heliusTx ->
                heliusTx.toDomain(walletId, address, network)
            }
        }
    }

    override suspend fun getTransaction(
        signature: String,
        network: SolanaNetwork
    ): Result<HeliusTransactionResponse> = withContext(ioDispatcher) {
        SafeApiCall.make {
            val api = getHeliusApi(network)
            val request = HeliusTransactionRequest(
                transactions = listOf(signature)
            )
            val transactions = api.getTransaction(request = request)

            transactions.firstOrNull() ?: throw Exception("Transaction not found")
        }
    }

    override fun parseTransfer(
        transaction: HeliusTransactionResponse,
        walletAddress: String
    ): TransferInfo? {
        return try {
            val nativeTransfer = transaction.nativeTransfers.find {
                it.fromUserAccount == walletAddress || it.toUserAccount == walletAddress
            }

            nativeTransfer?.let {
                val isIncoming = it.toUserAccount == walletAddress
                TransferInfo(
                    from = it.fromUserAccount,
                    to = it.toUserAccount,
                    amount = it.amount,
                    isIncoming = isIncoming,
                    fee = transaction.fee
                )
            }
        } catch (e: Exception) {
            null
        }
    }

    companion object {
        private const val TAG = "SolanaRepo"
        private const val SOLANA_FIXED_FEE_LAMPORTS = 5000L
        private const val SOL_DECIMALS = 9
        private const val MICRO_LAMPORTS_PER_LAMPORT = 1_000_000L

        // Fee estimate constants
        private const val SLOW_PERCENTILE = 0
        private const val NORMAL_PERCENTILE = 50
        private const val FAST_PERCENTILE = 95
        private const val DEFAULT_PRIORITY_FEE = 0
        private const val PERCENTILE_DIVISOR = 100
        private const val COERCE_MIN = 0

        // Compute unit constants
        private const val SLOW_COMPUTE_UNITS = 200_000
        private const val NORMAL_COMPUTE_UNITS = 400_000
        private const val FAST_COMPUTE_UNITS = 800_000

        // Estimated time constants (in seconds)
        private const val SLOW_ESTIMATED_TIME_SECONDS = 2
        private const val NORMAL_ESTIMATED_TIME_SECONDS = 1
        private const val FAST_ESTIMATED_TIME_SECONDS = 1

        // Signature constants
        private const val SIGNATURE_SIZE = 64
        private const val SHA_256_ALGORITHM = "SHA-256"
    }
}