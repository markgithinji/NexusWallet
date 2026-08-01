package com.example.nexuswallet.feature.solana.domain.usecase

import com.example.nexuswallet.feature.core.domain.di.IoDispatcher
import com.example.nexuswallet.feature.core.domain.model.BroadcastResult
import com.example.nexuswallet.feature.core.domain.model.FeeLevel
import com.example.nexuswallet.feature.core.domain.model.SolanaTransaction
import com.example.nexuswallet.feature.core.domain.repository.KeyStoreRepository
import com.example.nexuswallet.feature.core.domain.repository.VaultRepository
import com.example.nexuswallet.feature.core.util.Result
import com.example.nexuswallet.feature.core.util.WalletConstants.KEY_SOLANA_DEVNET
import com.example.nexuswallet.feature.core.util.WalletConstants.KEY_SOLANA_MAINNET
import com.example.nexuswallet.feature.core.util.decodeHex
import com.example.nexuswallet.feature.core.util.use
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
    private val vaultRepository: VaultRepository,
    private val keyStoreRepository: KeyStoreRepository,
    private val logger: Logger,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) {

    suspend operator fun invoke(
        walletId: String,
        toAddress: String,
        amount: BigDecimal,
        feeLevel: FeeLevel,
        coin: SolanaCoin,
        note: String?,
        cipher: Cipher? = null
    ): Result<SendSolanaResult> = withContext(ioDispatcher) {
        logger.d(TAG, "Starting Solana send | walletId=$walletId, amount=$amount SOL, target=${toAddress.take(8)}...")

        val wallet = walletRepository.getWallet(walletId) ?: run {
            logger.e(TAG, "Wallet not found | walletId=$walletId")
            return@withContext Result.Error("Wallet not found")
        }

        // Verify this Solana coin belongs to the wallet
        val solanaCoin = wallet.solanaCoins.find {
            it.address == coin.address && it.network == coin.network
        }

        if (solanaCoin == null) {
            logger.e(TAG, "Solana mismatch | address ${coin.address.take(8)}... not enabled for ${coin.network.name}")
            return@withContext Result.Error("Solana not enabled for ${coin.network.name}")
        }

        // 1. Get fee estimate
        logger.d(TAG, "Step 1: Requesting fee estimate | network=${coin.network.name}")
        val feeResult = solanaBlockchainRepository.getFeeEstimate(feeLevel, coin.network)
        if (feeResult is Result.Error) {
            logger.e(TAG, "Fee estimation failed | error=${feeResult.message}")
            return@withContext Result.Error(feeResult.message)
        }
        val feeEstimate = (feeResult as Result.Success).data
        logger.d(TAG, "Fee estimate received | totalFee=${feeEstimate.feeSol} SOL")

        val lamports = amount.multiply(BigDecimal(LAMPORTS_PER_SOL)).toLong()

        // 2. Get private key
        val keyType = when (coin.network) {
            SolanaNetwork.Mainnet -> KEY_SOLANA_MAINNET
            SolanaNetwork.Devnet -> KEY_SOLANA_DEVNET
        }

        val encryptedData = vaultRepository.getEncryptedPrivateKey(
            walletId = walletId,
            keyType = keyType
        )

        if (encryptedData == null) {
            logger.e(TAG, "No private key found in vault | keyType=$keyType")
            return@withContext Result.Error("No private key found. Make sure Solana is enabled in your wallet.")
        }

        val (encryptedHex, iv) = encryptedData

        // Decrypt private key (Repository now handles safe calls and mapping hardware errors)
        val decryptionResult = if (cipher != null) {
            logger.d(TAG, "Decrypting private key using provided cipher")
            keyStoreRepository.decryptWithCipher(cipher, encryptedHex.decodeHex())
        } else {
            logger.d(TAG, "Decrypting private key using stored IV")
            keyStoreRepository.decrypt(encryptedHex.decodeHex(), iv)
        }

        if (decryptionResult is Result.Error) {
            logger.e(TAG, "Decryption failed | error=${decryptionResult.message}")
            return@withContext decryptionResult
        }

        val privateKeyBytes = (decryptionResult as Result.Success).data

        val keypair = privateKeyBytes.use {
            createSolanaKeypair(it)
        } ?: run {
            logger.e(TAG, "Invalid private key format after decryption")
            return@withContext Result.Error("Invalid private key format")
        }

        val derivedAddress = keypair.publicKey.toString()
        if (derivedAddress != solanaCoin.address) {
            logger.e(TAG, "Private key mismatch | derivedAddress=$derivedAddress != storedAddress=${solanaCoin.address}")
            return@withContext Result.Error("Private key doesn't match wallet")
        }

        // 3. Create and sign transaction
        logger.d(TAG, "Step 3: Creating and signing transaction locally")
        val signedTxResult = solanaBlockchainRepository.createAndSignTransaction(
            fromKeypair = keypair,
            toAddress = toAddress,
            lamports = lamports,
            network = coin.network,
            priorityFeeRate = feeEstimate.priorityFeeRate,
            computeUnitLimit = feeEstimate.computeUnits
        )

        if (signedTxResult is Result.Error) {
            logger.e(TAG, "Signing failed | error=${signedTxResult.message}")
            return@withContext Result.Error(signedTxResult.message)
        }
        val signedTx = (signedTxResult as Result.Success).data
        logger.d(TAG, "Transaction signed successfully | signature=${signedTx.signature.take(8)}...")

        // 4. Broadcast transaction
        logger.d(TAG, "Step 4: Broadcasting transaction to ${coin.network.name}")
        val broadcastResult = broadcastTransaction(signedTx, coin.network)

        // 5. save transaction after successful broadcast
        if (broadcastResult.success) {
            logger.d(TAG, "Broadcast successful | Saving transaction record")
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
        } else {
            logger.e(TAG, "Broadcast failed | error=${broadcastResult.error}")
        }

        val sendResult = SendSolanaResult(
            transactionId = signedTx.signature,
            txHash = signedTx.signature,
            success = broadcastResult.success,
            error = broadcastResult.error
        )

        Result.Success(sendResult)
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
                BroadcastResult(success = false, error = broadcastResult.message)
            }

            Result.Loading -> {
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
        null
    }

    companion object {
        private const val TAG = "SendSolanaUC"
        private const val KEYPAIR_64_BYTES = 64
        private const val KEYPAIR_32_BYTES = 32
    }
}
