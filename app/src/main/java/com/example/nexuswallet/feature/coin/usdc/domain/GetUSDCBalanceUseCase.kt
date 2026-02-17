package com.example.nexuswallet.feature.coin.usdc.domain

import android.util.Log
import com.example.nexuswallet.feature.wallet.data.model.BroadcastResult
import com.example.nexuswallet.feature.coin.bitcoin.FeeLevel
import com.example.nexuswallet.feature.coin.ethereum.EthereumBlockchainRepository
import com.example.nexuswallet.feature.coin.usdc.USDCBlockchainRepository
import com.example.nexuswallet.feature.coin.usdc.Web3jFactory
import com.example.nexuswallet.feature.wallet.data.repository.KeyManager
import com.example.nexuswallet.feature.wallet.data.repository.WalletRepository
import com.example.nexuswallet.feature.wallet.domain.ChainType
import com.example.nexuswallet.feature.wallet.domain.TokenBalance
import com.example.nexuswallet.feature.wallet.domain.Transaction
import com.example.nexuswallet.feature.wallet.domain.TransactionStatus
import com.example.nexuswallet.feature.wallet.domain.WalletType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.web3j.protocol.core.DefaultBlockParameterName
import java.math.BigDecimal
import java.math.RoundingMode
import javax.inject.Inject
import com.example.nexuswallet.feature.coin.Result
import com.example.nexuswallet.feature.coin.ethereum.EthereumTransaction
import com.example.nexuswallet.feature.coin.ethereum.EthereumTransactionRepository
import com.example.nexuswallet.feature.coin.usdc.USDCTransactionRepository
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.USDCBalance
import java.math.BigInteger
import javax.inject.Singleton

@Singleton
class SendUSDCUseCase @Inject constructor(
    private val walletRepository: WalletRepository,
    private val usdcBlockchainRepository: USDCBlockchainRepository,
    private val keyManager: KeyManager,
    private val usdcTransactionRepository: USDCTransactionRepository
) {
    suspend operator fun invoke(
        walletId: String,
        toAddress: String,
        amount: BigDecimal,
        feeLevel: FeeLevel = FeeLevel.NORMAL
    ): Result<SendUSDCResult> = withContext(Dispatchers.IO) {
        try {
            Log.d("SendUSDCUC", "========== SEND USDC START ==========")
            Log.d("SendUSDCUC", "WalletId: $walletId, To: $toAddress, Amount: $amount USDC")

            // 1. Get wallet
            val wallet = walletRepository.getWallet(walletId)
                ?: return@withContext Result.Error("Wallet not found", IllegalArgumentException("Wallet not found"))

            // 2. Get USDC coin info
            val usdcCoin = wallet.usdc
                ?: return@withContext Result.Error(
                    message = "USDC not enabled for this wallet",
                    throwable = IllegalArgumentException("No USDC coin found")
                )

            val address = usdcCoin.address
            val network = usdcCoin.network
            val contractAddress = usdcCoin.contractAddress

            Log.d("SendUSDCUC", "Address: $address, Network: ${network.displayName}")

            // 3. Get fee estimate
            Log.d("SendUSDCUC", "Getting fee estimate...")
            val feeResult = usdcBlockchainRepository.getFeeEstimate(feeLevel, network)
            val feeEstimate = when (feeResult) {
                is Result.Success -> feeResult.data
                is Result.Error -> return@withContext Result.Error(
                    message = feeResult.message,
                    throwable = feeResult.throwable
                )
                Result.Loading -> return@withContext Result.Error("Failed to get fee estimate: timeout", null)
            }
            Log.d("SendUSDCUC", "Fee estimate: ${feeEstimate.totalFeeEth} ETH")

            // 4. Get private key
            val privateKeyResult = keyManager.getPrivateKeyForSigning(walletId)
            if (privateKeyResult.isFailure) {
                return@withContext Result.Error(
                    message = "Cannot access private key",
                    throwable = privateKeyResult.exceptionOrNull()
                )
            }
            val privateKey = privateKeyResult.getOrThrow()

            // 5. Get nonce
            Log.d("SendUSDCUC", "Getting nonce...")
            val nonceResult = usdcBlockchainRepository.getNonce(address, network)
            val nonce = when (nonceResult) {
                is Result.Success -> nonceResult.data
                is Result.Error -> return@withContext Result.Error(
                    message = nonceResult.message,
                    throwable = nonceResult.throwable
                )
                Result.Loading -> return@withContext Result.Error("Failed to get nonce: timeout", null)
            }
            Log.d("SendUSDCUC", "Nonce: $nonce")

            // 6. Get chainId
            val chainId = network.chainId.toLong()

            // 7. Create and sign transaction
            Log.d("SendUSDCUC", "Creating and signing USDC transfer...")
            val createResult = usdcBlockchainRepository.createAndSignUSDCTransfer(
                fromAddress = address,
                fromPrivateKey = privateKey,
                toAddress = toAddress,
                amount = amount,
                gasPriceWei = BigInteger(feeEstimate.gasPriceWei),
                nonce = nonce,
                chainId = chainId,
                network = network
            )

            val (rawTransaction, signedHex, txHash) = when (createResult) {
                is Result.Success -> createResult.data
                is Result.Error -> return@withContext Result.Error(
                    message = createResult.message,
                    throwable = createResult.throwable
                )
                Result.Loading -> return@withContext Result.Error("Failed to create USDC transfer: timeout", null)
            }

            Log.d("SendUSDCUC", "Transaction created and signed: $txHash")

            // 8. Create USDC transaction (using fee estimate)
            val amountUnits = amount.multiply(BigDecimal("1000000")).toBigInteger()
            val transaction = USDCSendTransaction(
                id = "usdc_tx_${System.currentTimeMillis()}",
                walletId = walletId,
                fromAddress = address,
                toAddress = toAddress,
                status = TransactionStatus.PENDING,
                timestamp = System.currentTimeMillis(),
                note = null,
                feeLevel = feeLevel,
                amount = amountUnits.toString(),
                amountDecimal = amount.toPlainString(),
                contractAddress = contractAddress,
                network = network,
                gasPriceWei = feeEstimate.gasPriceWei,
                gasPriceGwei = feeEstimate.gasPriceGwei,
                gasLimit = feeEstimate.gasLimit,
                feeWei = feeEstimate.totalFeeWei,
                feeEth = feeEstimate.totalFeeEth,
                nonce = nonce.toInt(),
                chainId = chainId,
                signedHex = signedHex,
                txHash = txHash,
                ethereumTransactionId = null
            )

            // 9. Save transaction
            usdcTransactionRepository.saveTransaction(transaction)
            Log.d("SendUSDCUC", "Transaction saved to repository")

            // 10. Broadcast
            Log.d("SendUSDCUC", "Broadcasting transaction...")
            val broadcastResult = usdcBlockchainRepository.broadcastUSDCTransaction(
                signedHex = signedHex,
                network = network
            )

            val broadcastData = when (broadcastResult) {
                is Result.Success -> broadcastResult.data
                is Result.Error -> return@withContext Result.Error(
                    message = broadcastResult.message,
                    throwable = broadcastResult.throwable
                )
                Result.Loading -> return@withContext Result.Error("Broadcast timeout", null)
            }

            Log.d("SendUSDCUC", "Broadcast result: success=${broadcastData.success}, hash=${broadcastData.hash}")

            // 11. Update transaction status
            val updatedTransaction = if (broadcastData.success) {
                transaction.copy(
                    status = TransactionStatus.SUCCESS,
                    txHash = broadcastData.hash ?: txHash
                )
            } else {
                transaction.copy(
                    status = TransactionStatus.FAILED
                )
            }
            usdcTransactionRepository.updateTransaction(updatedTransaction)

            val result = SendUSDCResult(
                transactionId = transaction.id,
                txHash = broadcastData.hash ?: txHash,
                success = broadcastData.success,
                error = broadcastData.error
            )

            Log.d("SendUSDCUC", "========== SEND USDC COMPLETE ==========")
            Result.Success(result)

        } catch (e: Exception) {
            Log.e("SendUSDCUC", "Exception: ${e.message}", e)
            Result.Error("Send failed: ${e.message}", e)
        }
    }
}

data class SendUSDCResult(
    val transactionId: String,
    val txHash: String,
    val success: Boolean,
    val error: String? = null
)

@Singleton
class GetUSDCBalanceUseCase @Inject constructor(
    private val walletRepository: WalletRepository,
    private val usdcBlockchainRepository: USDCBlockchainRepository
) {
    suspend operator fun invoke(walletId: String): Result<USDCBalance> {
        val wallet = walletRepository.getWallet(walletId)
            ?: return Result.Error("Wallet not found")

        val usdcCoin = wallet.usdc
            ?: return Result.Error("USDC not enabled for this wallet")

        return usdcBlockchainRepository.getUSDCBalance(usdcCoin.address, usdcCoin.network)
    }
}

@Singleton
class GetETHBalanceForGasUseCase @Inject constructor(
    private val walletRepository: WalletRepository,
    private val ethereumBlockchainRepository: EthereumBlockchainRepository
) {
    suspend operator fun invoke(walletId: String): Result<BigDecimal> {
        val wallet = walletRepository.getWallet(walletId)
            ?: return Result.Error("Wallet not found", IllegalArgumentException("Wallet not found"))

        // Check for Ethereum coin first
        val ethereumCoin = wallet.ethereum
        if (ethereumCoin != null) {
            val balanceResult = ethereumBlockchainRepository.getEthereumBalance(
                address = ethereumCoin.address,
                network = ethereumCoin.network
            )

            return when (balanceResult) {
                is Result.Success -> Result.Success(balanceResult.data)
                is Result.Error -> Result.Error(
                    message = "Failed to get ETH balance: ${balanceResult.message}",
                    throwable = balanceResult.throwable
                )
                Result.Loading -> Result.Error("Failed to get ETH balance: timeout", null)
            }
        }

        // Fallback to USDC coin if it exists (same address as Ethereum)
        val usdcCoin = wallet.usdc
        if (usdcCoin != null) {
            val balanceResult = ethereumBlockchainRepository.getEthereumBalance(
                address = usdcCoin.address,
                network = usdcCoin.network
            )

            return when (balanceResult) {
                is Result.Success -> Result.Success(balanceResult.data)
                is Result.Error -> Result.Error(
                    message = "Failed to get ETH balance: ${balanceResult.message}",
                    throwable = balanceResult.throwable
                )
                Result.Loading -> Result.Error("Failed to get ETH balance: timeout", null)
            }
        }

        return Result.Success(BigDecimal.ZERO)
    }
}

@Singleton
class GetUSDCFeeEstimateUseCase @Inject constructor(
    private val usdcBlockchainRepository: USDCBlockchainRepository
) {
    suspend operator fun invoke(
        feeLevel: FeeLevel = FeeLevel.NORMAL,
        network: EthereumNetwork = EthereumNetwork.Sepolia
    ): Result<USDCFeeEstimate> {
        return usdcBlockchainRepository.getFeeEstimate(feeLevel, network)
    }
}