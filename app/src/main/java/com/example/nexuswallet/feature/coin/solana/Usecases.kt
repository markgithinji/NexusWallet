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

@Singleton
class CreateSolanaTransactionUseCase @Inject constructor(
    private val walletRepository: WalletRepository,
    private val solanaBlockchainRepository: SolanaBlockchainRepository,
    private val transactionLocalDataSource: TransactionLocalDataSource
) {
    suspend operator fun invoke(
        walletId: String,
        toAddress: String,
        amount: BigDecimal,
        feeLevel: FeeLevel = FeeLevel.NORMAL,
        note: String? = null
    ): Result<SendTransaction> {
        return try {
            val wallet = walletRepository.getWallet(walletId) as? SolanaWallet
                ?: return Result.Error("Solana wallet not found", IllegalArgumentException("Wallet not found"))

            createSolanaTransaction(wallet, toAddress, amount, feeLevel, note)
        } catch (e: Exception) {
            Result.Error("Failed to create transaction: ${e.message}", e)
        }
    }

    private suspend fun createSolanaTransaction(
        wallet: SolanaWallet,
        toAddress: String,
        amount: BigDecimal,
        feeLevel: FeeLevel,
        note: String?
    ): Result<SendTransaction> {
        Log.d("CreateSolanaTxUseCase", "Creating Solana transaction...")

        val blockhashResult = solanaBlockchainRepository.getRecentBlockhash()
        val blockhash = when (blockhashResult) {
            is Result.Success -> blockhashResult.data
            is Result.Error -> return Result.Error(
                "Failed to get blockhash: ${blockhashResult.message}",
                blockhashResult.throwable
            )
            Result.Loading -> return Result.Error("Timeout getting blockhash", null)
        }

        val feeResult = solanaBlockchainRepository.getFeeEstimate(feeLevel)
        val feeEstimate = when (feeResult) {
            is Result.Success -> feeResult.data
            is Result.Error -> return Result.Error(
                "Failed to get fee estimate: ${feeResult.message}",
                feeResult.throwable
            )
            Result.Loading -> return Result.Error("Timeout getting fee", null)
        }

        val lamports = amount.multiply(BigDecimal("1000000000")).toLong()

        val transaction = SendTransaction(
            id = "sol_tx_${System.currentTimeMillis()}",
            walletId = wallet.id,
            walletType = WalletType.SOLANA,
            fromAddress = wallet.address,
            toAddress = toAddress,
            amount = lamports.toString(),
            amountDecimal = amount.toPlainString(),
            fee = feeEstimate.totalFee,
            feeDecimal = feeEstimate.totalFeeDecimal,
            total = (lamports + feeEstimate.totalFee.toLong()).toString(),
            totalDecimal = (amount + BigDecimal(feeEstimate.totalFeeDecimal)).toPlainString(),
            chain = ChainType.SOLANA,
            status = TransactionStatus.PENDING,
            note = note,
            timestamp = System.currentTimeMillis(),
            feeLevel = feeLevel,
            metadata = mapOf(
                "blockhash" to blockhash,
                "feePayer" to wallet.address
            )
        )

        transactionLocalDataSource.saveSendTransaction(transaction)
        return Result.Success(transaction)
    }
}
@Singleton
class SignSolanaTransactionUseCase @Inject constructor(
    private val walletRepository: WalletRepository,
    private val solanaBlockchainRepository: SolanaBlockchainRepository,
    private val keyManager: KeyManager,
    private val transactionLocalDataSource: TransactionLocalDataSource
) {
    suspend operator fun invoke(transactionId: String): Result<SignedTransaction> {
        return try {
            val transaction = transactionLocalDataSource.getSendTransaction(transactionId)
                ?: return Result.Error("Transaction not found", IllegalArgumentException("Transaction not found"))

            if (transaction.walletType != WalletType.SOLANA) {
                return Result.Error("Only Solana signing supported", IllegalArgumentException("Wrong wallet type"))
            }

            signSolanaTransaction(transaction)
        } catch (e: Exception) {
            Result.Error("Signing failed: ${e.message}", e)
        }
    }

    private suspend fun signSolanaTransaction(
        transaction: SendTransaction
    ): Result<SignedTransaction> = withContext(Dispatchers.IO) {

        val wallet = walletRepository.getWallet(transaction.walletId) as? SolanaWallet
            ?: return@withContext Result.Error("Solana wallet not found", IllegalArgumentException("Wallet not found"))

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

        val feeResult = solanaBlockchainRepository.getFeeEstimate()
        val fee = when (feeResult) {
            is Result.Success -> feeResult.data
            is Result.Error -> return@withContext Result.Error(
                "Failed to get fee: ${feeResult.message}",
                feeResult.throwable
            )
            Result.Loading -> return@withContext Result.Error("Timeout getting fee", null)
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
        if (derivedAddress != wallet.address) {
            return@withContext Result.Error(
                "Private key doesn't match wallet",
                IllegalStateException("Address mismatch")
            )
        }

        val lamports = transaction.amount.toLongOrNull() ?: 0L

        val signedTxResult = solanaBlockchainRepository.createAndSignTransaction(
            fromKeypair = keypair,
            toAddress = transaction.toAddress,
            lamports = lamports
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

        val signedTransaction = SignedTransaction(
            rawHex = signedTx.serialize().toHexString(),
            hash = txHash,
            chain = transaction.chain
        )

        val updatedTransaction = transaction.copy(
            status = TransactionStatus.PENDING,
            hash = txHash,
            signedHex = signedTransaction.rawHex,
            metadata = transaction.metadata + mapOf(
                "blockhash" to currentBlockhash,
                "signature" to txHash,
                "feePayer" to wallet.address
            )
        )

        transactionLocalDataSource.saveSendTransaction(updatedTransaction)
        Result.Success(signedTransaction)
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
    private val transactionLocalDataSource: TransactionLocalDataSource
) {
    suspend operator fun invoke(transactionId: String): Result<BroadcastResult> = withContext(Dispatchers.IO) {
        Log.d("BroadcastSolanaUseCase", "Broadcasting transaction: $transactionId")

        val transaction = transactionLocalDataSource.getSendTransaction(transactionId)
            ?: return@withContext Result.Error("Transaction not found", IllegalArgumentException("Transaction not found"))

        if (transaction.chain != ChainType.SOLANA) {
            return@withContext Result.Error("Not a Solana transaction", IllegalArgumentException("Wrong chain"))
        }

        val signedHex = transaction.signedHex
            ?: return@withContext Result.Error("Transaction not signed", IllegalStateException("Not signed"))

        val signatureBytes = signedHex.hexToByteArray()
        val solanaSignedTx = SolanaBlockchainRepository.SolanaSignedTransaction(
            signature = signatureBytes.take(64).toByteArray(),
            serialize = { signatureBytes }
        )

        val broadcastResult = solanaBlockchainRepository.broadcastTransaction(solanaSignedTx)

        when (broadcastResult) {
            is Result.Success -> {
                val result = broadcastResult.data
                val updatedTransaction = if (result.success) {
                    transaction.copy(
                        status = TransactionStatus.SUCCESS,
                        hash = result.hash ?: transaction.hash
                    )
                } else {
                    transaction.copy(
                        status = TransactionStatus.FAILED
                    )
                }
                transactionLocalDataSource.saveSendTransaction(updatedTransaction)
                Result.Success(result)
            }
            is Result.Error -> Result.Error(broadcastResult.message, broadcastResult.throwable)
            Result.Loading -> Result.Error("Broadcast timeout", null)
        }
    }

    private fun String.hexToByteArray(): ByteArray {
        return chunked(2).map { it.toInt(16).toByte() }.toByteArray()
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