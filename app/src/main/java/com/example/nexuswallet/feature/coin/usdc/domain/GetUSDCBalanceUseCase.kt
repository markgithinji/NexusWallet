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
import javax.inject.Singleton

@Singleton
class SendUSDCUseCase @Inject constructor(
    private val walletRepository: WalletRepository,
    private val usdcBlockchainRepository: USDCBlockchainRepository,
    private val ethereumBlockchainRepository: EthereumBlockchainRepository,
    private val keyManager: KeyManager,
    private val usdcTransactionRepository: USDCTransactionRepository
) {
    suspend operator fun invoke(
        walletId: String,
        toAddress: String,
        amount: BigDecimal
    ): Result<SendUSDCResult> = withContext(Dispatchers.IO) {
        try {
            Log.d("SendUSDCUC", "========== SEND USDC START ==========")
            Log.d("SendUSDCUC", "WalletId: $walletId, To: $toAddress, Amount: $amount USDC")

            // 1. Get wallet
            val wallet = walletRepository.getWallet(walletId)
                ?: return@withContext Result.Error("Wallet not found", IllegalArgumentException("Wallet not found"))

            // 2. Get USDC coin info
            val usdcCoin = wallet.usdc
            val ethereumCoin = wallet.ethereum

            val (address, network, contractAddress) = when {
                usdcCoin != null -> Triple(usdcCoin.address, usdcCoin.network, usdcCoin.contractAddress)
                ethereumCoin != null -> {
                    val contract = getUSDCContractAddress(ethereumCoin.network)
                        ?: return@withContext Result.Error(
                            message = "USDC not supported on ${ethereumCoin.network.displayName}",
                            throwable = IllegalArgumentException("Unsupported network for USDC")
                        )
                    Triple(ethereumCoin.address, ethereumCoin.network, contract)
                }
                else -> return@withContext Result.Error(
                    message = "Wallet does not support USDC",
                    throwable = IllegalArgumentException("No USDC or Ethereum coin found")
                )
            }

            Log.d("SendUSDCUC", "Address: $address, Network: ${network.displayName}")

            // 3. Get private key
            val privateKeyResult = keyManager.getPrivateKeyForSigning(walletId)
            if (privateKeyResult.isFailure) {
                return@withContext Result.Error(
                    message = "Cannot access private key",
                    throwable = privateKeyResult.exceptionOrNull()
                )
            }
            val privateKey = privateKeyResult.getOrThrow()

            // 4. Get gas price
            Log.d("SendUSDCUC", "Getting gas price...")
            val gasPriceResult = ethereumBlockchainRepository.getCurrentGasPrice(network)
            val gasPriceResponse = when (gasPriceResult) {
                is Result.Success -> gasPriceResult.data
                is Result.Error -> return@withContext Result.Error(
                    message = "Failed to get gas price: ${gasPriceResult.message}",
                    throwable = gasPriceResult.throwable
                )
                Result.Loading -> return@withContext Result.Error("Failed to get gas price: timeout", null)
            }

            val gasPriceGwei = BigDecimal(gasPriceResponse.propose)
            val gasPriceWei = gasPriceGwei.multiply(BigDecimal("1000000000")).toBigInteger()
            Log.d("SendUSDCUC", "Gas price: $gasPriceGwei Gwei")

            // 5. Get nonce
            Log.d("SendUSDCUC", "Getting nonce...")
            val nonceResult = usdcBlockchainRepository.getNonce(address, network)
            val nonce = when (nonceResult) {
                is Result.Success -> nonceResult.data.toInt()
                is Result.Error -> return@withContext Result.Error(
                    message = nonceResult.message,
                    throwable = nonceResult.throwable
                )
                Result.Loading -> return@withContext Result.Error("Failed to get nonce: timeout", null)
            }
            Log.d("SendUSDCUC", "Nonce: $nonce")

            // 6. Get chainId (now from the sealed class)
            val chainId = network.chainId.toLong()

            // 7. Create and sign transaction
            Log.d("SendUSDCUC", "Creating and signing USDC transfer...")
            val createResult = usdcBlockchainRepository.createAndSignUSDCTransfer(
                fromAddress = address,
                fromPrivateKey = privateKey,
                toAddress = toAddress,
                amount = amount,
                gasPriceWei = gasPriceWei,
                nonce = nonce.toBigInteger(),
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

            // 8. Calculate fees
            val gasLimit = rawTransaction.gasLimit
            val feeWei = gasPriceWei.multiply(gasLimit)
            val feeEth = BigDecimal(feeWei).divide(
                BigDecimal("1000000000000000000"),
                8,
                RoundingMode.HALF_UP
            )

            // 9. Create USDC transaction
            val amountUnits = amount.multiply(BigDecimal("1000000")).toBigInteger()
            val transaction = USDCSendTransaction(
                id = "usdc_tx_${System.currentTimeMillis()}",
                walletId = walletId,
                fromAddress = address,
                toAddress = toAddress,
                status = TransactionStatus.PENDING,
                timestamp = System.currentTimeMillis(),
                note = null,
                feeLevel = FeeLevel.NORMAL,
                amount = amountUnits.toString(),
                amountDecimal = amount.toPlainString(),
                contractAddress = contractAddress,
                network = network,
                gasPriceWei = gasPriceWei.toString(),
                gasPriceGwei = gasPriceGwei.toPlainString(),
                gasLimit = gasLimit.toLong(),
                feeWei = feeWei.toString(),
                feeEth = feeEth.toPlainString(),
                nonce = nonce,
                chainId = chainId,
                signedHex = signedHex,
                txHash = txHash,
                ethereumTransactionId = null
            )

            // 10. Save transaction
            usdcTransactionRepository.saveTransaction(transaction)
            Log.d("SendUSDCUC", "Transaction saved to repository")

            // 11. Broadcast
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

            // 12. Update transaction status
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
            Log.e("SendUSDCUC", " Exception: ${e.message}", e)
            Result.Error("Send failed: ${e.message}", e)
        }
    }

    private fun getUSDCContractAddress(network: EthereumNetwork): String? {
        return when (network) {
            is EthereumNetwork.Mainnet -> network.usdcContractAddress
            is EthereumNetwork.Sepolia -> network.usdcContractAddress
            else -> null
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

        // Check if wallet has USDC coin
        val usdcCoin = wallet.usdc
        if (usdcCoin != null) {
            return usdcBlockchainRepository.getUSDCBalance(usdcCoin.address, usdcCoin.network)
        }

        // Fallback: check if wallet has Ethereum coin (for backwards compatibility)
        val ethereumCoin = wallet.ethereum
        if (ethereumCoin != null) {
            val contract = getUSDCContractAddress(ethereumCoin.network)
            if (contract != null) {
                return usdcBlockchainRepository.getUSDCBalance(ethereumCoin.address, ethereumCoin.network)
            }
        }

        return Result.Error("Wallet does not support USDC")
    }

    private fun getUSDCContractAddress(network: EthereumNetwork): String? {
        return when (network) {
            is EthereumNetwork.Mainnet -> network.usdcContractAddress
            is EthereumNetwork.Sepolia -> network.usdcContractAddress
            else -> null
        }
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