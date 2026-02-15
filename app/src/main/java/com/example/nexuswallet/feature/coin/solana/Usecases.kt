package com.example.nexuswallet.feature.coin.solana

import android.util.Log
import com.example.nexuswallet.feature.wallet.data.local.TransactionLocalDataSource
import com.example.nexuswallet.feature.wallet.data.model.BroadcastResult
import com.example.nexuswallet.feature.coin.bitcoin.FeeLevel
import com.example.nexuswallet.feature.wallet.data.model.FeeEstimate
import com.example.nexuswallet.feature.wallet.data.model.SendTransaction
import com.example.nexuswallet.feature.wallet.data.model.SignedTransaction
import com.example.nexuswallet.feature.wallet.data.repository.KeyManager
import com.example.nexuswallet.feature.wallet.data.repository.WalletRepository
import com.example.nexuswallet.feature.wallet.domain.ChainType
import com.example.nexuswallet.feature.wallet.domain.SolanaWallet
import com.example.nexuswallet.feature.wallet.domain.TransactionStatus
import com.example.nexuswallet.feature.wallet.domain.WalletType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.sol4k.Keypair
import java.math.BigDecimal
import javax.inject.Inject
import javax.inject.Singleton
import com.example.nexuswallet.feature.coin.Result
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.SolanaCoin

@Singleton
class CreateSolanaTransactionUseCase @Inject constructor(
    private val walletRepository: WalletRepository,
    private val solanaBlockchainRepository: SolanaBlockchainRepository,
    private val solanaTransactionRepository: SolanaTransactionRepository
) {
    suspend operator fun invoke(
        walletId: String,
        toAddress: String,
        amount: BigDecimal,
        feeLevel: FeeLevel = FeeLevel.NORMAL,
        note: String? = null
    ): Result<SolanaTransaction> {
        return try {
            val wallet = walletRepository.getWallet(walletId)
                ?: return Result.Error("Wallet not found", IllegalArgumentException("Wallet not found"))

            val solanaCoin = wallet.solana
                ?: return Result.Error("Solana not enabled for this wallet", IllegalArgumentException("Solana not enabled"))

            createSolanaTransaction(wallet.id, solanaCoin, toAddress, amount, feeLevel, note)
        } catch (e: Exception) {
            Result.Error("Failed to create transaction: ${e.message}", e)
        }
    }

    private suspend fun createSolanaTransaction(
        walletId: String,
        solanaCoin: SolanaCoin,
        toAddress: String,
        amount: BigDecimal,
        feeLevel: FeeLevel,
        note: String?
    ): Result<SolanaTransaction> = withContext(Dispatchers.IO) {
        try {
            Log.d("CreateSolanaTxUseCase", "Creating Solana transaction...")

            val blockhashResult = solanaBlockchainRepository.getRecentBlockhash()
            val blockhash = when (blockhashResult) {
                is Result.Success -> blockhashResult.data
                is Result.Error -> return@withContext Result.Error(
                    "Failed to get blockhash: ${blockhashResult.message}",
                    blockhashResult.throwable
                )
                Result.Loading -> return@withContext Result.Error("Timeout getting blockhash", null)
            }

            val feeResult = solanaBlockchainRepository.getFeeEstimate(feeLevel)
            val feeEstimate = when (feeResult) {
                is Result.Success -> feeResult.data
                is Result.Error -> return@withContext Result.Error(
                    "Failed to get fee estimate: ${feeResult.message}",
                    feeResult.throwable
                )
                Result.Loading -> return@withContext Result.Error("Timeout getting fee", null)
            }

            val lamports = amount.multiply(BigDecimal("1000000000")).toLong()
            val feeLamports = feeEstimate.totalFee.toLong()

            val transaction = SolanaTransaction(
                id = "sol_tx_${System.currentTimeMillis()}",
                walletId = walletId,
                fromAddress = solanaCoin.address,
                toAddress = toAddress,
                amountLamports = lamports,
                amountSol = amount.toPlainString(),
                feeLamports = feeLamports,
                feeSol = BigDecimal(feeEstimate.totalFeeDecimal).toPlainString(),
                blockhash = blockhash,
                signedData = null,
                signature = null,
                status = TransactionStatus.PENDING,
                note = note,
                timestamp = System.currentTimeMillis(),
                feeLevel = feeLevel,
                network = "mainnet"
            )

            solanaTransactionRepository.saveTransaction(transaction)
            Log.d("CreateSolanaTxUseCase", "Saved Solana transaction")
            Result.Success(transaction)

        } catch (e: Exception) {
            Log.e("CreateSolanaTxUseCase", "Error: ${e.message}", e)
            Result.Error("Failed to create transaction: ${e.message}", e)
        }
    }
}
@Singleton
class SignSolanaTransactionUseCase @Inject constructor(
    private val walletRepository: WalletRepository,
    private val solanaBlockchainRepository: SolanaBlockchainRepository,
    private val keyManager: KeyManager,
    private val solanaTransactionRepository: SolanaTransactionRepository
) {
    suspend operator fun invoke(transactionId: String): Result<SolanaTransaction> {
        return try {
            val transaction = solanaTransactionRepository.getTransaction(transactionId)
                ?: return Result.Error("Transaction not found", IllegalArgumentException("Transaction not found"))

            signSolanaTransaction(transaction)
        } catch (e: Exception) {
            Result.Error("Signing failed: ${e.message}", e)
        }
    }

    private suspend fun signSolanaTransaction(
        transaction: SolanaTransaction
    ): Result<SolanaTransaction> = withContext(Dispatchers.IO) {

        val wallet = walletRepository.getWallet(transaction.walletId)
            ?: return@withContext Result.Error("Wallet not found", IllegalArgumentException("Wallet not found"))

        val solanaCoin = wallet.solana
            ?: return@withContext Result.Error("Solana not enabled for this wallet", IllegalArgumentException("Solana not enabled"))

        Log.d("SignSolanaTxUseCase", "Signing transaction: ${transaction.id}")

        val blockhashResult = solanaBlockchainRepository.getRecentBlockhash()
        val currentBlockhash = when (blockhashResult) {
            is Result.Success -> blockhashResult.data
            is Result.Error -> return@withContext Result.Error(
                "Failed to get blockhash: ${blockhashResult.message}",
                blockhashResult.throwable
            )
            Result.Loading -> return@withContext Result.Error("Timeout getting blockhash", null)
        }

        val privateKeyResult = keyManager.getPrivateKeyForSigning(
            transaction.walletId,
            keyType = "SOLANA_PRIVATE_KEY"
        )

        if (privateKeyResult.isFailure) {
            return@withContext Result.Error(
                privateKeyResult.exceptionOrNull()?.message ?: "No private key",
                privateKeyResult.exceptionOrNull()
            )
        }

        val privateKeyHex = privateKeyResult.getOrThrow()
        val keypair = createSolanaKeypair(privateKeyHex)
            ?: return@withContext Result.Error("Invalid private key format", IllegalArgumentException("Invalid key"))

        val derivedAddress = keypair.publicKey.toString()
        if (derivedAddress != solanaCoin.address) {
            return@withContext Result.Error(
                "Private key doesn't match wallet",
                IllegalStateException("Address mismatch")
            )
        }

        val signedTxResult = solanaBlockchainRepository.createAndSignTransaction(
            fromKeypair = keypair,
            toAddress = transaction.toAddress,
            lamports = transaction.amountLamports
        )

        val signedTx = when (signedTxResult) {
            is Result.Success -> signedTxResult.data
            is Result.Error -> return@withContext Result.Error(
                "Failed to sign: ${signedTxResult.message}",
                signedTxResult.throwable
            )
            Result.Loading -> return@withContext Result.Error("Timeout signing", null)
        }

        val signatureBytes = signedTx.signature
        val txHash = signatureBytes.toHexString()

        val updatedTransaction = transaction.copy(
            status = TransactionStatus.PENDING,
            signature = signatureBytes,
            signedData = signedTx.serialize(),
            blockhash = currentBlockhash
        )

        solanaTransactionRepository.updateTransaction(updatedTransaction)
        Result.Success(updatedTransaction)
    }

    private fun createSolanaKeypair(privateKeyHex: String): Keypair? = try {
        val cleanPrivateKeyHex = if (privateKeyHex.startsWith("0x")) {
            privateKeyHex.substring(2)
        } else {
            privateKeyHex
        }

        val privateKeyBytes = cleanPrivateKeyHex.hexToByteArray()

        when (privateKeyBytes.size) {
            64 -> Keypair.fromSecretKey(privateKeyBytes)
            32 -> Keypair.fromSecretKey(privateKeyBytes + ByteArray(32))
            else -> null
        }
    } catch (e: Exception) {
        Log.e("SignSolanaTxUseCase", "Error creating keypair: ${e.message}", e)
        null
    }

    private fun String.hexToByteArray(): ByteArray {
        return chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    }

    private fun ByteArray.toHexString(): String {
        return joinToString("") { "%02x".format(it) }
    }
}

@Singleton
class BroadcastSolanaTransactionUseCase @Inject constructor(
    private val solanaBlockchainRepository: SolanaBlockchainRepository,
    private val solanaTransactionRepository: SolanaTransactionRepository
) {
    suspend operator fun invoke(transactionId: String): Result<BroadcastResult> = withContext(Dispatchers.IO) {
        Log.d("BroadcastSolanaUseCase", "Broadcasting transaction: $transactionId")

        val transaction = solanaTransactionRepository.getTransaction(transactionId)
            ?: return@withContext Result.Error("Transaction not found", IllegalArgumentException("Transaction not found"))

        val signedData = transaction.signedData
            ?: return@withContext Result.Error("Transaction not signed", IllegalStateException("Not signed"))

        val solanaSignedTx = SolanaBlockchainRepository.SolanaSignedTransaction(
            signature = transaction.signature ?: ByteArray(0),
            serialize = { signedData }
        )

        val broadcastResult = solanaBlockchainRepository.broadcastTransaction(solanaSignedTx)

        when (broadcastResult) {
            is Result.Success -> {
                val result = broadcastResult.data
                val updatedTransaction = if (result.success) {
                    transaction.copy(
                        status = TransactionStatus.SUCCESS,
                        signature = transaction.signature
                    )
                } else {
                    transaction.copy(
                        status = TransactionStatus.FAILED
                    )
                }
                solanaTransactionRepository.updateTransaction(updatedTransaction)
                Result.Success(result)
            }
            is Result.Error -> Result.Error(broadcastResult.message, broadcastResult.throwable)
            Result.Loading -> Result.Error("Broadcast timeout", null)
        }
    }
}


@Singleton
class GetSolanaBalanceUseCase @Inject constructor(
    private val solanaBlockchainRepository: SolanaBlockchainRepository
) {
    suspend operator fun invoke(address: String): Result<BigDecimal> {
        return solanaBlockchainRepository.getBalance(address)
    }
}

@Singleton
class GetSolanaFeeEstimateUseCase @Inject constructor(
    private val solanaBlockchainRepository: SolanaBlockchainRepository
) {
    suspend operator fun invoke(feeLevel: FeeLevel = FeeLevel.NORMAL): Result<FeeEstimate> {
        return solanaBlockchainRepository.getFeeEstimate(feeLevel)
    }
}

@Singleton
class GetSolanaRecentBlockhashUseCase @Inject constructor(
    private val solanaBlockchainRepository: SolanaBlockchainRepository
) {
    suspend operator fun invoke(): Result<String> {
        return solanaBlockchainRepository.getRecentBlockhash()
    }
}

@Singleton
class RequestSolanaAirdropUseCase @Inject constructor(
    private val solanaBlockchainRepository: SolanaBlockchainRepository
) {
    suspend operator fun invoke(address: String, amountSol: Double = 1.0): Result<String> {
        return solanaBlockchainRepository.requestAirdrop(address, amountSol)
    }
}

//@Singleton
//class GetSolanaTransactionHistoryUseCase @Inject constructor(
//    private val solanaBlockchainRepository: SolanaBlockchainRepository
//) {
//    suspend operator fun invoke(address: String, limit: Int = 10): Result<List<SolanaBlockchainRepository.SolanaTransaction>> {
//        return solanaBlockchainRepository.getTransactionHistory(address, limit)
//    }
//}

@Singleton
class ValidateSolanaAddressUseCase @Inject constructor(
    private val solanaBlockchainRepository: SolanaBlockchainRepository
) {
    operator fun invoke(address: String): Result<Boolean> {
        return solanaBlockchainRepository.validateAddress(address)
    }
}