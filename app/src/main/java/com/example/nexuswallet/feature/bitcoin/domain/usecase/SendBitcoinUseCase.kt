package com.example.nexuswallet.feature.bitcoin.domain.usecase

import com.example.nexuswallet.feature.bitcoin.domain.model.PreparedBitcoinTransaction
import com.example.nexuswallet.feature.bitcoin.domain.model.SendBitcoinResult
import com.example.nexuswallet.feature.bitcoin.domain.repository.BitcoinBlockchainRepository
import com.example.nexuswallet.feature.bitcoin.domain.repository.BitcoinTransactionRepository
import com.example.nexuswallet.feature.core.domain.di.IoDispatcher
import com.example.nexuswallet.feature.core.domain.model.BitcoinTransaction
import com.example.nexuswallet.feature.core.domain.repository.KeyStoreRepository
import com.example.nexuswallet.feature.core.domain.repository.VaultRepository
import com.example.nexuswallet.feature.core.util.Result
import com.example.nexuswallet.feature.core.util.WalletConstants.KEY_BITCOIN_MAINNET
import com.example.nexuswallet.feature.core.util.WalletConstants.KEY_BITCOIN_TESTNET
import com.example.nexuswallet.feature.core.util.decodeHex
import com.example.nexuswallet.feature.core.util.use
import com.example.nexuswallet.feature.logging.Logger
import com.example.nexuswallet.feature.wallet.domain.model.BitcoinNetwork
import com.example.nexuswallet.feature.wallet.domain.model.TransactionStatus
import com.example.nexuswallet.feature.wallet.domain.repository.WalletRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import org.bitcoinj.core.ECKey
import org.bitcoinj.core.LegacyAddress
import org.bitcoinj.core.Transaction as BitcoinJTransaction
import org.bitcoinj.core.Utils
import org.bitcoinj.params.MainNetParams
import org.bitcoinj.params.TestNet3Params
import java.math.BigDecimal
import java.math.RoundingMode
import javax.crypto.Cipher
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SendBitcoinUseCase @Inject constructor(
    private val walletRepository: WalletRepository,
    private val bitcoinBlockchainRepository: BitcoinBlockchainRepository,
    private val bitcoinTransactionRepository: BitcoinTransactionRepository,
    private val keyStoreRepository: KeyStoreRepository,
    private val vaultRepository: VaultRepository,
    private val logger: Logger,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {

    suspend operator fun invoke(
        preparedTransaction: PreparedBitcoinTransaction,
        walletId: String,
        network: BitcoinNetwork,
        cipher: Cipher? = null
    ): Result<SendBitcoinResult> = withContext(ioDispatcher) {
        logger.d(TAG, "Starting Bitcoin send | walletId=$walletId, network=$network, target=${preparedTransaction.toAddress.take(8)}...")

        // Get wallet
        val wallet = walletRepository.getWallet(walletId) ?: run {
            logger.e(TAG, "Wallet not found | walletId=$walletId")
            return@withContext Result.Error("Wallet not found")
        }

        // Get the specific Bitcoin coin for this network
        val bitcoinCoin = wallet.bitcoinCoins.find { it.network == network }
        if (bitcoinCoin == null) {
            logger.e(TAG, "Bitcoin not enabled for network $network in wallet: $walletId")
            return@withContext Result.Error("Bitcoin not enabled for $network")
        }

        // Get private key type
        val keyType = when (bitcoinCoin.network) {
            BitcoinNetwork.Mainnet -> KEY_BITCOIN_MAINNET
            BitcoinNetwork.Testnet -> KEY_BITCOIN_TESTNET
        }

        // Get encrypted private key
        val encryptedData = vaultRepository.getEncryptedPrivateKey(
            walletId = walletId,
            keyType = keyType
        )

        if (encryptedData == null) {
            logger.e(TAG, "No private key found in vault | walletId=$walletId, keyType=$keyType")
            return@withContext Result.Error("No private key found")
        }

        val (encryptedHex, iv) = encryptedData

        // Decrypt private key
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
        val ecKey = privateKeyBytes.use {
            try {
                ECKey.fromPrivate(it)
            } catch (e: Exception) {
                logger.e(TAG, "Invalid private key format after decryption")
                return@withContext Result.Error("Invalid private key format")
            }
        }

        // Verify key matches address
        val networkParams = when (bitcoinCoin.network) {
            BitcoinNetwork.Mainnet -> MainNetParams.get()
            BitcoinNetwork.Testnet -> TestNet3Params.get()
        }
        if (LegacyAddress.fromKey(networkParams, ecKey).toString() != bitcoinCoin.address) {
            logger.e(TAG, "Private key mismatch | derivedAddress does not match stored address")
            return@withContext Result.Error("Private key does not match wallet address")
        }

        // Create and sign transaction using prepared data
        logger.d(TAG, "Signing transaction | amount=${preparedTransaction.amountSatoshis} satoshis")
        when (val signResult = bitcoinBlockchainRepository.createAndSignTransaction(
            fromKey = ecKey,
            toAddress = preparedTransaction.toAddress,
            satoshis = preparedTransaction.amountSatoshis,
            feeLevel = preparedTransaction.feeLevel,
            network = bitcoinCoin.network
        )) {
            is Result.Success -> {
                val signedTx = signResult.data
                logger.d(TAG, "Transaction signed successfully | txId=${signedTx.txId}")

                broadcastAndSaveTransaction(
                    signedTx = signedTx,
                    preparedTx = preparedTransaction,
                    walletId = walletId,
                    network = bitcoinCoin.network
                )
            }

            is Result.Error -> {
                logger.e(TAG, "Signing failed | error=${signResult.message}")
                Result.Error("Failed to create signed transaction: ${signResult.message}")
            }

            else -> Result.Error("Unknown signing error")
        }
    }

    private suspend fun broadcastAndSaveTransaction(
        signedTx: BitcoinJTransaction,
        preparedTx: PreparedBitcoinTransaction,
        walletId: String,
        network: BitcoinNetwork
    ): Result<SendBitcoinResult> {
        val signedHex = Utils.HEX.encode(signedTx.bitcoinSerialize())
        val txId = signedTx.txId.toString()

        logger.d(TAG, "Broadcasting transaction | txId=$txId")
        return when (val broadcastResult = bitcoinBlockchainRepository.broadcastTransaction(
            signedHex = signedHex,
            network = network
        )) {
            is Result.Success -> {
                logger.d(TAG, "Broadcast successful | txHash=${broadcastResult.data}")
                
                // Calculate BTC amounts
                val btcAmount = BigDecimal(preparedTx.amountSatoshis).divide(
                    BigDecimal(100_000_000),
                    8,
                    RoundingMode.HALF_UP
                )

                val btcFee = BigDecimal(preparedTx.feeSatoshis).divide(
                    BigDecimal(100_000_000),
                    8,
                    RoundingMode.HALF_UP
                )

                // Create and save transaction
                val transaction = BitcoinTransaction(
                    id = txId,
                    walletId = walletId,
                    fromAddress = preparedTx.fromAddress,
                    toAddress = preparedTx.toAddress,
                    status = TransactionStatus.PENDING,
                    timestamp = System.currentTimeMillis(),
                    note = null,
                    feeLevel = preparedTx.feeLevel,
                    network = network,
                    isIncoming = false,
                    txHash = broadcastResult.data,
                    amount = btcAmount.toPlainString(),
                    fee = btcFee.toPlainString(),
                    symbol = "BTC",
                    amountSatoshis = preparedTx.amountSatoshis,
                    feeSatoshis = preparedTx.feeSatoshis,
                    feePerByte = preparedTx.feePerByte,
                    estimatedSize = preparedTx.estimatedSize.toLong(),
                    signedHex = signedHex
                )

                bitcoinTransactionRepository.saveTransaction(transaction)
                logger.d(TAG, "Transaction record saved to database")

                Result.Success(
                    SendBitcoinResult(
                        transactionId = transaction.id,
                        txHash = broadcastResult.data,
                        success = true,
                        error = null
                    )
                )
            }

            is Result.Error -> {
                logger.e(TAG, "Broadcast failed | error=${broadcastResult.message}")
                Result.Error(broadcastResult.message)
            }

            else -> {
                logger.e(TAG, "Unknown broadcast error occurred")
                Result.Error("Unknown broadcast error")
            }
        }
    }

    companion object {
        private const val TAG = "SendBitcoinUC"
    }
}
