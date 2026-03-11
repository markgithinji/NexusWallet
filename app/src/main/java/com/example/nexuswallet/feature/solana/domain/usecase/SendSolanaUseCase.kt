package com.example.nexuswallet.feature.solana.domain.usecase

import com.example.nexuswallet.feature.authentication.domain.repository.KeyStoreRepository
import com.example.nexuswallet.feature.authentication.domain.repository.SecurityPreferencesRepository
import com.example.nexuswallet.feature.core.domain.model.BroadcastResult
import com.example.nexuswallet.feature.core.domain.model.FeeLevel
import com.example.nexuswallet.feature.core.util.Result
import com.example.nexuswallet.feature.solana.data.model.SolanaSignedTransaction
import com.example.nexuswallet.feature.solana.domain.model.SendSolanaResult
import com.example.nexuswallet.feature.solana.domain.model.SolanaFeeEstimate
import com.example.nexuswallet.feature.solana.domain.model.SolanaTransaction
import com.example.nexuswallet.feature.solana.util.SolanaConstants.LAMPORTS_PER_SOL
import com.example.nexuswallet.feature.solana.util.SolanaConstants.SOLANA_PRIVATE_KEY_TYPE
import com.example.nexuswallet.feature.logging.Logger
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.SolanaCoin
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.SolanaNetwork
import com.example.nexuswallet.feature.wallet.domain.TransactionStatus
import com.example.nexuswallet.feature.wallet.domain.WalletRepository
import com.example.nexuswallet.toHex
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.sol4k.Keypair
import java.math.BigDecimal
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SendSolanaUseCase @Inject constructor(
    private val walletRepository: WalletRepository,
    private val solanaBlockchainRepository: com.example.nexuswallet.feature.solana.domain.repository.SolanaBlockchainRepository,
    private val solanaTransactionRepository: com.example.nexuswallet.feature.solana.domain.repository.SolanaTransactionRepository,
    private val securityPreferencesRepository: SecurityPreferencesRepository,
    private val keyStoreRepository: KeyStoreRepository,
    private val logger: Logger
) {

    private val tag = "SendSolanaUC"

    suspend operator fun invoke(
        walletId: String,
        toAddress: String,
        amount: BigDecimal,
        feeLevel: FeeLevel,
        network: SolanaNetwork,
        note: String?
    ): Result<SendSolanaResult> = withContext(Dispatchers.IO) {
        logger.d(tag, "Sending $amount SOL to $toAddress on $network")

        val wallet = walletRepository.getWallet(walletId) ?: run {
            logger.e(tag, "Wallet not found: $walletId")
            return@withContext Result.Error("Wallet not found")
        }

        // Get the specific Solana coin for this network
        val solanaCoin = wallet.solanaCoins.find { it.network == network }
        if (solanaCoin == null) {
            logger.e(tag, "Solana not enabled for network $network in wallet: $walletId")
            return@withContext Result.Error("Solana not enabled for $network")
        }

        logger.d(tag, "Network: ${solanaCoin.network}")

        // 1. Get fee estimate
        val feeResult = solanaBlockchainRepository.getFeeEstimate(feeLevel, network)
        if (feeResult is Result.Error) {
            logger.e(tag, "Failed to get fee estimate on $network")
            return@withContext Result.Error(feeResult.message)
        }
        val feeEstimate = (feeResult as Result.Success).data

        val lamports = amount.multiply(BigDecimal(LAMPORTS_PER_SOL)).toLong()

        // 2. Get private key
        logger.d(tag, "Looking for key with type: ${SOLANA_PRIVATE_KEY_TYPE}")

        val encryptedData = securityPreferencesRepository.getEncryptedPrivateKey(
            walletId = walletId,
            keyType = SOLANA_PRIVATE_KEY_TYPE
        )

        if (encryptedData == null) {
            logger.e(tag, "No private key found for wallet: $walletId")
            return@withContext Result.Error("No private key found. Make sure Solana is enabled in your wallet.")
        }

        val (encryptedHex, iv) = encryptedData

        val privateKeyHex = try {
            keyStoreRepository.decryptString(encryptedHex, iv.toHex())
        } catch (e: Exception) {
            logger.e(tag, "Failed to decrypt private key: ${e.message}")
            return@withContext Result.Error("Failed to decrypt private key")
        }

        val keypair = createSolanaKeypair(privateKeyHex)
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
            network = network
        )

        if (signedTxResult is Result.Error) {
            logger.e(tag, "Failed to sign transaction on $network: ${signedTxResult.message}")
            return@withContext Result.Error(signedTxResult.message)
        }
        val signedTx = (signedTxResult as Result.Success).data

        // 4. Broadcast transaction
        val broadcastResult = broadcastTransaction(signedTx, network)

        // 5. save transaction after successful broadcast
        if (broadcastResult.success) {
            val transaction = createTransactionRecord(
                walletId = walletId,
                toAddress = toAddress,
                amount = amount,
                feeLevel = feeLevel,
                note = note,
                network = network,
                solanaCoin = solanaCoin,
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
            transactionId = "sol_tx_${System.currentTimeMillis()}",
            txHash = signedTx.signature ?: "",
            success = broadcastResult.success,
            error = broadcastResult.error
        )

        if (sendResult.success) {
            logger.d(
                tag,
                "Send successful on $network: tx ${sendResult.txHash.take(SIGNATURE_PREVIEW_LENGTH)}..."
            )
        } else {
            logger.e(tag, "Send failed on $network: ${sendResult.error}")
        }

        Result.Success(sendResult)
    }

    private fun createTransactionRecord(
        walletId: String,
        toAddress: String,
        amount: BigDecimal,
        feeLevel: FeeLevel,
        note: String?,
        network: SolanaNetwork,
        solanaCoin: SolanaCoin,
        feeEstimate: SolanaFeeEstimate,
        signature: String?
    ): SolanaTransaction {
        val lamports = amount.multiply(BigDecimal(LAMPORTS_PER_SOL)).toLong()

        return SolanaTransaction(
            id = "sol_tx_${System.currentTimeMillis()}",
            walletId = walletId,
            fromAddress = solanaCoin.address,
            toAddress = toAddress,
            amountLamports = lamports,
            amountSol = amount.toPlainString(),
            feeLamports = feeEstimate.feeLamports,
            feeSol = feeEstimate.feeSol,
            signature = signature,
            status = TransactionStatus.SUCCESS,
            note = note,
            timestamp = System.currentTimeMillis(),
            feeLevel = feeLevel,
            network = network,
            isIncoming = false,
            tokenMint = null,
            tokenSymbol = null,
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

    private fun createSolanaKeypair(privateKeyHex: String): Keypair? = try {
        val cleanPrivateKeyHex = if (privateKeyHex.startsWith(HEX_PREFIX)) {
            privateKeyHex.substring(HEX_PREFIX.length)
        } else {
            privateKeyHex
        }
        val privateKeyBytes = cleanPrivateKeyHex.hexToByteArray()
        when (privateKeyBytes.size) {
            KEYPAIR_64_BYTES -> Keypair.fromSecretKey(privateKeyBytes)
            KEYPAIR_32_BYTES -> Keypair.fromSecretKey(privateKeyBytes + ByteArray(KEYPAIR_32_BYTES))
            else -> null
        }
    } catch (e: Exception) {
        logger.e(tag, "Error creating keypair: ${e.message}")
        null
    }

    companion object{
        private const val SIGNATURE_PREVIEW_LENGTH = 8
        private const val HEX_PREFIX = "0x"
        private const val KEYPAIR_64_BYTES = 64
        private const val KEYPAIR_32_BYTES = 32
    }
}