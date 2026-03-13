package com.example.nexuswallet.feature.bitcoin.domain.usecase

import com.example.nexuswallet.feature.authentication.domain.repository.SecurityPreferencesRepository
import com.example.nexuswallet.feature.bitcoin.domain.model.BitcoinTransaction
import com.example.nexuswallet.feature.bitcoin.domain.model.PreparedBitcoinTransaction
import com.example.nexuswallet.feature.bitcoin.domain.model.SendBitcoinResult
import com.example.nexuswallet.feature.bitcoin.domain.repository.BitcoinBlockchainRepository
import com.example.nexuswallet.feature.bitcoin.domain.repository.BitcoinTransactionRepository
import com.example.nexuswallet.feature.core.domain.model.CoinType
import com.example.nexuswallet.feature.core.data.repository.KeyStoreRepository
import com.example.nexuswallet.feature.core.util.Result
import com.example.nexuswallet.feature.core.util.WalletConstants.KEY_BITCOIN_MAINNET
import com.example.nexuswallet.feature.core.util.WalletConstants.KEY_BITCOIN_TESTNET
import com.example.nexuswallet.feature.logging.Logger
import com.example.nexuswallet.feature.wallet.domain.model.BitcoinNetwork
import com.example.nexuswallet.feature.wallet.domain.model.TransactionStatus
import com.example.nexuswallet.feature.wallet.domain.repository.WalletRepository
import com.example.nexuswallet.toHex
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.bitcoinj.core.DumpedPrivateKey
import org.bitcoinj.core.LegacyAddress
import org.bitcoinj.core.Transaction
import org.bitcoinj.core.Utils
import org.bitcoinj.params.MainNetParams
import org.bitcoinj.params.TestNet3Params
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SendBitcoinUseCase @Inject constructor(
    private val walletRepository: WalletRepository,
    private val bitcoinBlockchainRepository: BitcoinBlockchainRepository,
    private val bitcoinTransactionRepository: BitcoinTransactionRepository,
    private val keyStoreRepository: KeyStoreRepository,
    private val securityPreferencesRepository: SecurityPreferencesRepository,
    private val logger: Logger
) {

    private val tag = "SendBitcoinUC"

    suspend operator fun invoke(
        preparedTransaction: PreparedBitcoinTransaction,
        walletId: String,
        network: BitcoinNetwork
    ): Result<SendBitcoinResult> = withContext(Dispatchers.IO) {
        logger.d(
            tag,
            "Sending prepared transaction: ${preparedTransaction.transactionId} | walletId=$walletId | network=$network"
        )

        // Get wallet
        val wallet = walletRepository.getWallet(walletId) ?: run {
            logger.e(tag, "Wallet not found: $walletId")
            return@withContext Result.Error("Wallet not found")
        }

        // Get the specific Bitcoin coin for this network
        val bitcoinCoin = wallet.bitcoinCoins.find { it.network == network }
        if (bitcoinCoin == null) {
            logger.e(tag, "Bitcoin not enabled for network $network in wallet: $walletId")
            return@withContext Result.Error("Bitcoin not enabled for $network")
        }

        // Get private key
        val keyType = when (bitcoinCoin.network) {
            BitcoinNetwork.Mainnet -> KEY_BITCOIN_MAINNET
            BitcoinNetwork.Testnet -> KEY_BITCOIN_TESTNET
        }

        // Get private key
        val encryptedData = securityPreferencesRepository.getEncryptedPrivateKey(
            walletId = walletId,
            keyType = keyType
        )

        if (encryptedData == null) {
            logger.e(tag, "No private key found for wallet: $walletId")
            return@withContext Result.Error("No private key found")
        }

        val privateKeyWIF = keyStoreRepository.decryptString(
            encryptedData.first,
            encryptedData.second.toHex()
        )

        val networkParams = when (bitcoinCoin.network) {
            BitcoinNetwork.Mainnet -> MainNetParams.get()
            BitcoinNetwork.Testnet -> TestNet3Params.get()
        }

        val ecKey = DumpedPrivateKey.fromBase58(networkParams, privateKeyWIF).key

        // Verify key matches address
        if (LegacyAddress.fromKey(networkParams, ecKey).toString() != bitcoinCoin.address) {
            logger.e(tag, "Private key does not match wallet address")
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

                // Broadcast and save after successful broadcast
                broadcastAndSaveTransaction(
                    signedTx = signedTx,
                    preparedTx = preparedTransaction,
                    walletId = walletId,
                    network = bitcoinCoin.network
                )
            }

            is Result.Error -> {
                logger.e(tag, "Failed to create signed transaction: ${signResult.message}")
                Result.Error("Failed to create signed transaction: ${signResult.message}")
            }

            else -> Result.Error("Unknown signing error")
        }
    }

    private suspend fun broadcastAndSaveTransaction(
        signedTx: Transaction,
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
                // Create and save transaction
                val transaction = BitcoinTransaction(
                    id = preparedTx.transactionId,
                    walletId = walletId,
                    coinType = CoinType.BITCOIN,
                    fromAddress = preparedTx.fromAddress,
                    toAddress = preparedTx.toAddress,
                    amountSatoshis = preparedTx.amountSatoshis,
                    amountBtc = preparedTx.amountBtc.toPlainString(),
                    feeSatoshis = preparedTx.feeSatoshis,
                    feeBtc = preparedTx.feeBtc.toPlainString(),
                    feePerByte = preparedTx.feePerByte,
                    estimatedSize = preparedTx.estimatedSize.toLong(),
                    signedHex = signedHex,
                    txHash = broadcastResult.data,
                    status = TransactionStatus.SUCCESS,
                    note = null,
                    timestamp = System.currentTimeMillis(),
                    feeLevel = preparedTx.feeLevel,
                    network = network,
                    isIncoming = false
                )

                bitcoinTransactionRepository.saveTransaction(transaction)
                logger.d(tag, "Transaction saved after successful broadcast: ${transaction.id}")

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
                logger.e(tag, "Failed to broadcast transaction: ${broadcastResult.message}")
                Result.Error(broadcastResult.message)
            }

            else -> Result.Error("Unknown broadcast error")
        }
    }
}