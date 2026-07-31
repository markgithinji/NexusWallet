package com.example.nexuswallet.feature.bitcoin.domain.usecase

import android.security.keystore.UserNotAuthenticatedException
import com.example.nexuswallet.feature.bitcoin.domain.model.PreparedBitcoinTransaction
import com.example.nexuswallet.feature.bitcoin.domain.model.SendBitcoinResult
import com.example.nexuswallet.feature.bitcoin.domain.repository.BitcoinBlockchainRepository
import com.example.nexuswallet.feature.bitcoin.domain.repository.BitcoinTransactionRepository
import com.example.nexuswallet.feature.core.domain.di.IoDispatcher
import com.example.nexuswallet.feature.core.domain.exception.HardwareAuthRequiredException
import com.example.nexuswallet.feature.core.domain.model.BitcoinTransaction
import com.example.nexuswallet.feature.core.domain.repository.KeyStoreRepository
import com.example.nexuswallet.feature.core.domain.repository.VaultRepository
import com.example.nexuswallet.feature.core.util.Result
import com.example.nexuswallet.feature.core.util.WalletConstants.KEY_BITCOIN_MAINNET
import com.example.nexuswallet.feature.core.util.WalletConstants.KEY_BITCOIN_TESTNET
import com.example.nexuswallet.feature.core.util.decodeHex
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
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {

    suspend operator fun invoke(
        preparedTransaction: PreparedBitcoinTransaction,
        walletId: String,
        network: BitcoinNetwork,
        cipher: Cipher? = null
    ): Result<SendBitcoinResult> = withContext(ioDispatcher) {
        // Get wallet
        val wallet = walletRepository.getWallet(walletId) ?: run {
            return@withContext Result.Error("Wallet not found")
        }

        // Get the specific Bitcoin coin for this network
        val bitcoinCoin = wallet.bitcoinCoins.find { it.network == network }
        if (bitcoinCoin == null) {
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
            return@withContext Result.Error("No private key found")
        }

        val (encryptedHex, iv) = encryptedData

        val privateKeyBytes = if (cipher != null) {
            try {
                keyStoreRepository.decryptWithCipher(cipher, encryptedHex.decodeHex())
            } catch (e: Exception) {
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
                return@withContext Result.Error("Failed to decrypt private key")
            }
        }

        val ecKey = try {
            ECKey.fromPrivate(privateKeyBytes)
        } catch (e: Exception) {
            return@withContext Result.Error("Invalid private key format")
        } finally {
            privateKeyBytes.fill(0)
        }

        // Verify key matches address
        val networkParams = when (bitcoinCoin.network) {
            BitcoinNetwork.Mainnet -> MainNetParams.get()
            BitcoinNetwork.Testnet -> TestNet3Params.get()
        }
        if (LegacyAddress.fromKey(networkParams, ecKey).toString() != bitcoinCoin.address) {
            return@withContext Result.Error("Private key does not match wallet address")
        }

        // Create and sign transaction using prepared data
        when (val signResult = bitcoinBlockchainRepository.createAndSignTransaction(
            fromKey = ecKey,
            toAddress = preparedTransaction.toAddress,
            satoshis = preparedTransaction.amountSatoshis,
            feeLevel = preparedTransaction.feeLevel,
            network = bitcoinCoin.network
        )) {
            is Result.Success -> {
                val signedTx = signResult.data

                broadcastAndSaveTransaction(
                    signedTx = signedTx,
                    preparedTx = preparedTransaction,
                    walletId = walletId,
                    network = bitcoinCoin.network
                )
            }

            is Result.Error -> {
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

        return when (val broadcastResult = bitcoinBlockchainRepository.broadcastTransaction(
            signedHex = signedHex,
            network = network
        )) {
            is Result.Success -> {
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
                Result.Error(broadcastResult.message)
            }

            else -> Result.Error("Unknown broadcast error")
        }
    }
}