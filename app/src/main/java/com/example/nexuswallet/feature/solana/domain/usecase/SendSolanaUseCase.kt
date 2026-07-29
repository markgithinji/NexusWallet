package com.example.nexuswallet.feature.solana.domain.usecase

import android.security.keystore.UserNotAuthenticatedException
import com.example.nexuswallet.feature.authentication.domain.repository.SecurityPreferencesRepository
import com.example.nexuswallet.feature.core.domain.di.IoDispatcher
import com.example.nexuswallet.feature.core.domain.exception.HardwareAuthRequiredException
import com.example.nexuswallet.feature.core.domain.model.BroadcastResult
import com.example.nexuswallet.feature.core.domain.model.FeeLevel
import com.example.nexuswallet.feature.core.domain.model.SolanaTransaction
import com.example.nexuswallet.feature.core.domain.repository.KeyStoreRepository
import com.example.nexuswallet.feature.core.util.Result
import com.example.nexuswallet.feature.core.util.WalletConstants.KEY_SOLANA_DEVNET
import com.example.nexuswallet.feature.core.util.WalletConstants.KEY_SOLANA_MAINNET
import com.example.nexuswallet.feature.core.util.decodeHex
import com.example.nexuswallet.feature.logging.Logger
import com.example.nexuswallet.feature.solana.data.model.SolanaSignedTransaction
import com.example.nexuswallet.feature.solana.domain.model.SendSolanaResult
import com.example.nexuswallet.feature.solana.domain.model.SolanaFeeEstimate
import com.example.nexuswallet.feature.solana.domain.repository.SolanaBlockchainRepository
import com.example.nexuswallet.feature.solana.domain.repository.SolanaTransactionRepository
import com.example.nexuswallet.feature.solana.util.SolanaConstants.LAMPORTS_PER_SOL
import com.example.nexuswallet.feature.wallet.domain.model.SolanaCoin
import com.example.nexuswallet.feature.wallet.domain.model.SolanaNetwork
import com.example.nexuswallet.feature.wallet.domain.model.TransactionStatus
import com.example.nexuswallet.feature.wallet.domain.repository.WalletRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import org.sol4k.Keypair
import java.math.BigDecimal
import javax.crypto.Cipher
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SendSolanaUseCase @Inject constructor(
    private val walletRepository: WalletRepository,
    private val solanaBlockchainRepository: SolanaBlockchainRepository,
    private val solanaTransactionRepository: SolanaTransactionRepository,
    private val securityPreferencesRepository: SecurityPreferencesRepository,
    private val keyStoreRepository: KeyStoreRepository,
    private val logger: Logger,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) {

    private val tag = "SendSolanaUC"

    suspend operator fun invoke(
        walletId: String,
        toAddress: String,
        amount: BigDecimal,
        feeLevel: FeeLevel,
        coin: SolanaCoin,
        note: String?,
        cipher: Cipher? = null
    ): Result<SendSolanaResult> = withContext(ioDispatcher) {
        logger.d(tag, "Sending $amount SOL to $toAddress on ${coin.network.name}")

        val wallet = walletRepository.getWallet(walletId) ?: run {
            logger.e(tag, "Wallet not found: $walletId")
            return@withContext Result.Error("Wallet not found")
        }

        // Verify this Solana coin belongs to the wallet
        val solanaCoin = wallet.solanaCoins.find {
            it.address == coin.address && it.network == coin.network
        }

        if (solanaCoin == null) {
            logger.e(tag, "Solana coin not found in wallet: $walletId for network ${coin.network.name}")
            return@withContext Result.Error("Solana not enabled for ${coin.network.name}")
        }

        logger.d(tag, "Network: ${solanaCoin.network.name}")
        logger.d(tag, "From address: ${solanaCoin.address.take(8)}...")

        // 1. Get fee estimate
        val feeResult = solanaBlockchainRepository.getFeeEstimate(feeLevel, coin.network)
        if (feeResult is Result.Error) {
            logger.e(tag, "Failed to get fee estimate on ${coin.network.name}")
            return@withContext Result.Error(feeResult.message)
        }
        val feeEstimate = (feeResult as Result.Success).data

        val lamports = amount.multiply(BigDecimal(LAMPORTS_PER_SOL)).toLong()

        // 2. Get private key
        val keyType = when (coin.network) {
            SolanaNetwork.Mainnet -> KEY_SOLANA_MAINNET
            SolanaNetwork.Devnet -> KEY_SOLANA_DEVNET
        }

        logger.d(tag, "Looking for key with type: $keyType")

        val encryptedData = securityPreferencesRepository.getEncryptedPrivateKey(
            walletId = walletId,
            keyType = keyType
        )

        if (encryptedData == null) {
            logger.e(tag, "No private key found for wallet: $walletId with keyType: $keyType")
            return@withContext Result.Error("No private key found. Make sure Solana is enabled in your wallet.")
        }

        val (encryptedHex, iv) = encryptedData

        val privateKeyBytes = if (cipher != null) {
            try {
                keyStoreRepository.decryptWithCipher(cipher, encryptedHex.decodeHex())
            } catch (e: Exception) {
                logger.e(tag, "Failed to decrypt with provided cipher: ${e.message}")
                return@withContext Result.Error("Decryption failed")
            }
        } else {
            try {
                keyStoreRepository.decrypt(encryptedHex.decodeHex(), iv)
            } catch (e: Exception) {
                val isAuthRequired = e is UserNotAuthenticatedException || 
                                     e.cause is UserNotAuthenticatedException ||
                                     e is javax.crypto.IllegalBlockSizeException && e.message?.contains("user not authenticated", true) == true

                if (isAuthRequired) {
                    return@withContext Result.Error(
                        message = "Authentication required",
                        throwable = HardwareAuthRequiredException(null)
                    )
                }
                logger.e(tag, "Failed to decrypt private key: ${e.message}")
                return@withContext Result.Error("Failed to decrypt private key")
            }
        }

        try {
            val keypair = createSolanaKeypair(privateKeyBytes)
                ?: return@withContext Result.Error("Invalid private key format")

            val derivedAddress = keypair.publicKey.toString()
            if (derivedAddress != solanaCoin.address) {
                logger.e(tag, "Private key doesn't match wallet")
                logger.e(tag, "Derived: $derivedAddress, Expected: ${solanaCoin.address}")
                return@withContext Result.Error("Private key doesn't match wallet")
            }

            // 3. Create and sign transaction
            val signedTxResult = solanaBlockchainRepository.createAndSignTransaction(
                fromKeypair = keypair,
                toAddress = toAddress,
                lamports = lamports,
                network = coin.network,
                priorityFeeRate = feeEstimate.priorityFeeRate,
                computeUnitLimit = feeEstimate.computeUnits
            )

            if (signedTxResult is Result.Error) {
                logger.e(
                    tag,
                    "Failed to sign transaction on ${coin.network.name}: ${signedTxResult.message}"
                )
                return@withContext Result.Error(signedTxResult.message)
            }
            val signedTx = (signedTxResult as Result.Success).data

            // 4. Broadcast transaction
            val broadcastResult = broadcastTransaction(signedTx, coin.network)

            // 5. save transaction after successful broadcast
            if (broadcastResult.success) {
                val transaction = createTransactionRecord(
                    walletId = walletId,
                    toAddress = toAddress,
                    amount = amount,
                    feeLevel = feeLevel,
                    note = note,
                    coin = solanaCoin,
                    feeEstimate = feeEstimate,
                    signature = signedTx.signature
                )

                solanaTransactionRepository.saveTransaction(transaction)
                logger.d(
                    tag,
                    "Transaction saved after successful broadcast: ${transaction.id} with signature ${
                        signedTx.signature.take(SIGNATURE_PREVIEW_LENGTH)
                    }..."
                )
            } else {
                logger.e(tag, "Broadcast failed, no transaction saved: ${broadcastResult.error}")
            }

            val sendResult = SendSolanaResult(
                transactionId = signedTx.signature,
                txHash = signedTx.signature,
                success = broadcastResult.success,
                error = broadcastResult.error
            )

            if (sendResult.success) {
                logger.d(
                    tag,
                    "Send successful on ${coin.network.name}: tx ${
                        sendResult.txHash.take(
                            SIGNATURE_PREVIEW_LENGTH
                        )
                    }..."
                )
            } else {
                logger.e(tag, "Send failed on ${coin.network.name}: ${sendResult.error}")
            }

            Result.Success(sendResult)
        } finally {
            privateKeyBytes.fill(0)
        }
    }

    private fun createTransactionRecord(
        walletId: String,
        toAddress: String,
        amount: BigDecimal,
        feeLevel: FeeLevel,
        note: String?,
        coin: SolanaCoin,
        feeEstimate: SolanaFeeEstimate,
        signature: String?
    ): SolanaTransaction {
        val lamports = amount.multiply(BigDecimal(LAMPORTS_PER_SOL)).toLong()

        return SolanaTransaction(
            id = signature ?: "sol_tx_${System.currentTimeMillis()}",
            walletId = walletId,
            fromAddress = coin.address,
            toAddress = toAddress,
            status = TransactionStatus.PENDING,
            timestamp = System.currentTimeMillis(),
            note = note,
            feeLevel = feeLevel,
            network = coin.network,
            isIncoming = false,
            txHash = signature,
            amount = amount.toPlainString(),
            fee = feeEstimate.feeSol,
            symbol = coin.symbol,
            amountLamports = lamports,
            feeLamports = feeEstimate.feeLamports,
            signature = signature,
            tokenMint = null,
            tokenSymbol = null,
            tokenName = null,
            tokenDecimals = null,
            slot = null,
            blockTime = null
        )
    }

    private suspend fun broadcastTransaction(
        signedTx: SolanaSignedTransaction,
        network: SolanaNetwork
    ): BroadcastResult {
        val broadcastResult = solanaBlockchainRepository.broadcastTransaction(signedTx, network)

        return when (broadcastResult) {
            is Result.Success -> {
                broadcastResult.data
            }

            is Result.Error -> {
                logger.e(tag, "Broadcast failed on $network: ${broadcastResult.message}")
                BroadcastResult(success = false, error = broadcastResult.message)
            }

            Result.Loading -> {
                logger.e(tag, "Broadcast timeout on $network")
                BroadcastResult(success = false, error = "Broadcast timeout")
            }
        }
    }

    private fun createSolanaKeypair(privateKeyBytes: ByteArray): Keypair? = try {
        when (privateKeyBytes.size) {
            KEYPAIR_64_BYTES -> Keypair.fromSecretKey(privateKeyBytes)
            KEYPAIR_32_BYTES -> {
                val fullKey = ByteArray(KEYPAIR_64_BYTES)
                System.arraycopy(privateKeyBytes, 0, fullKey, 0, privateKeyBytes.size)
                val keypair = Keypair.fromSecretKey(fullKey)
                fullKey.fill(0)
                keypair
            }

            else -> null
        }
    } catch (e: Exception) {
        logger.e(tag, "Error creating keypair: ${e.message}")
        null
    }

    companion object{
        private const val SIGNATURE_PREVIEW_LENGTH = 8
        private const val KEYPAIR_64_BYTES = 64
        private const val KEYPAIR_32_BYTES = 32
    }
}