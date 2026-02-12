package com.example.nexuswallet.feature.coin.usdc.domain

import android.util.Log
import com.example.nexuswallet.feature.wallet.data.local.TransactionLocalDataSource
import com.example.nexuswallet.feature.wallet.data.model.BroadcastResult
import com.example.nexuswallet.feature.coin.bitcoin.FeeLevel
import com.example.nexuswallet.feature.wallet.data.model.SendTransaction
import com.example.nexuswallet.feature.coin.ethereum.EthereumBlockchainRepository
import com.example.nexuswallet.feature.coin.usdc.USDCBlockchainRepository
import com.example.nexuswallet.feature.coin.usdc.Web3jFactory
import com.example.nexuswallet.feature.wallet.data.repository.KeyManager
import com.example.nexuswallet.feature.wallet.data.repository.WalletRepository
import com.example.nexuswallet.feature.wallet.domain.ChainType
import com.example.nexuswallet.feature.wallet.domain.EthereumNetwork
import com.example.nexuswallet.feature.wallet.domain.EthereumWallet
import com.example.nexuswallet.feature.wallet.domain.TokenBalance
import com.example.nexuswallet.feature.wallet.domain.Transaction
import com.example.nexuswallet.feature.wallet.domain.TransactionStatus
import com.example.nexuswallet.feature.wallet.domain.USDCWallet
import com.example.nexuswallet.feature.wallet.domain.WalletType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.web3j.protocol.core.DefaultBlockParameterName
import java.math.BigDecimal
import java.math.RoundingMode
import javax.inject.Inject
import com.example.nexuswallet.feature.coin.Result
import javax.inject.Singleton

class GetUSDCBalanceUseCase @Inject constructor(
    private val walletRepository: WalletRepository,
    private val usdcBlockchainRepository: USDCBlockchainRepository
) {
    suspend operator fun invoke(walletId: String): Result<TokenBalance> {
        val wallet = walletRepository.getWallet(walletId)

        val (address, network) = when (wallet) {
            is USDCWallet -> Pair(wallet.address, wallet.network)
            is EthereumWallet -> Pair(wallet.address, wallet.network)
            else -> return Result.Error(
                message = "Wallet is not USDC or Ethereum type"
            )
        }

        return usdcBlockchainRepository.getUSDCBalance(address, network)
    }
}

class SendUSDCUseCase @Inject constructor(
    private val walletRepository: WalletRepository,
    private val usdcBlockchainRepository: USDCBlockchainRepository,
    private val ethereumBlockchainRepository: EthereumBlockchainRepository,
    private val keyManager: KeyManager,
    private val transactionLocalDataSource: TransactionLocalDataSource
) {
    suspend operator fun invoke(
        walletId: String,
        toAddress: String,
        amount: BigDecimal
    ): Result<BroadcastResult> {

        // 1. Get wallet
        val wallet = walletRepository.getWallet(walletId)
        val (address, network, contractAddress) = when (wallet) {
            is USDCWallet -> Triple(wallet.address, wallet.network, wallet.contractAddress)
            is EthereumWallet -> {
                val contract = getUSDCContractAddress(wallet.network)
                    ?: return Result.Error(
                        message = "USDC not supported on ${wallet.network}"
                    )
                Triple(wallet.address, wallet.network, contract)
            }
            else -> return Result.Error(
                message = "Wallet is not USDC or Ethereum wallet"
            )
        }

        // 2. Get private key
        val privateKeyResult = keyManager.getPrivateKeyForSigning(walletId)
        if (privateKeyResult.isFailure) {
            return Result.Error(
                message = "Cannot access private key",
                throwable = privateKeyResult.exceptionOrNull()
            )
        }
        val privateKey = privateKeyResult.getOrThrow()

        // 3. Get gas price
        val gasPriceResponse = try {
            ethereumBlockchainRepository.getCurrentGasPrice(network)
        } catch (e: Exception) {
            return Result.Error(
                message = "Failed to get gas price: ${e.message}",
                throwable = e
            )
        }
        val gasPriceGwei = BigDecimal(gasPriceResponse.propose)
        val gasPriceWei = gasPriceGwei.multiply(BigDecimal("1000000000")).toBigInteger()

        // 4. Get nonce
        val nonceResult = usdcBlockchainRepository.getNonce(address, network)
        if (nonceResult !is Result.Success) {
            return Result.Error(
                message = (nonceResult as Result.Error).message,
                throwable = (nonceResult as Result.Error).throwable
            )
        }
        val nonce = nonceResult.data

        // 5. Get chainId
        val chainId = when (network) {
            EthereumNetwork.MAINNET -> 1L
            EthereumNetwork.SEPOLIA -> 11155111L
            EthereumNetwork.POLYGON -> 137L
            EthereumNetwork.BSC -> 56L
            EthereumNetwork.ARBITRUM -> 42161L
            EthereumNetwork.OPTIMISM -> 10L
            else -> 11155111L
        }

        // 6. Create transaction object
        val transaction = SendTransaction(
            id = "usdc_tx_${System.currentTimeMillis()}",
            walletId = walletId,
            walletType = if (wallet is USDCWallet) WalletType.USDC else WalletType.ETHEREUM,
            fromAddress = address,
            toAddress = toAddress,
            amount = amount.multiply(BigDecimal("1000000")).toBigInteger().toString(),
            amountDecimal = amount.toPlainString(),
            fee = "0",
            feeDecimal = "0",
            total = "0",
            totalDecimal = "0",
            chain = when (network) {
                EthereumNetwork.SEPOLIA -> ChainType.ETHEREUM_SEPOLIA
                else -> ChainType.ETHEREUM
            },
            status = TransactionStatus.PENDING,
            gasPrice = gasPriceGwei.toString(),
            gasLimit = "65000",
            note = null,
            feeLevel = FeeLevel.NORMAL,
            metadata = mapOf(
                "token" to "USDC",
                "isTokenTransfer" to "true",
                "amountUsdc" to amount.toPlainString(),
                "contractAddress" to contractAddress
            )
        )

        // 7. Save to local storage
        transactionLocalDataSource.saveSendTransaction(transaction)

        // 8. Create and sign transaction
        val createResult = usdcBlockchainRepository.createAndSignUSDCTransfer(
            fromAddress = address,
            fromPrivateKey = privateKey,
            toAddress = toAddress,
            amount = amount,
            gasPriceWei = gasPriceWei,
            nonce = nonce,
            chainId = chainId,
            network = network
        )

        if (createResult !is Result.Success) {
            return Result.Error(
                message = (createResult as Result.Error).message,
                throwable = (createResult as Result.Error).throwable
            )
        }

        val (rawTransaction, signedHex, txHash) = createResult.data

        // 9. Calculate fees
        val transactionGasPriceWei = rawTransaction.gasPrice
        val gasLimit = rawTransaction.gasLimit
        val feeWei = gasPriceWei.multiply(gasLimit)
        val feeEth = feeWei.toBigDecimal().divide(
            BigDecimal("1000000000000000000"),
            8,
            RoundingMode.HALF_UP
        )

        // 10. Update transaction with signed data
        val updatedTransaction = transaction.copy(
            gasPrice = transactionGasPriceWei.toString(),
            gasLimit = gasLimit.toString(),
            fee = feeWei.toString(),
            feeDecimal = feeEth.toPlainString(),
            total = feeWei.toString(),
            totalDecimal = feeEth.toPlainString(),
            signedHex = signedHex,
            metadata = transaction.metadata + mapOf(
                "gasPriceWei" to transactionGasPriceWei.toString(),
                "gasLimit" to gasLimit.toString(),
                "rawTransaction" to rawTransaction.toString()
            )
        )

        transactionLocalDataSource.saveSendTransaction(updatedTransaction)

        // 11. Broadcast transaction
        val broadcastResult = usdcBlockchainRepository.broadcastUSDCTransaction(
            signedHex = signedHex,
            network = network
        )

        if (broadcastResult !is Result.Success) {
            return Result.Error(
                message = (broadcastResult as Result.Error).message,
                throwable = (broadcastResult as Result.Error).throwable
            )
        }

        val broadcastData = broadcastResult.data

        // 12. Update transaction status
        val finalTransaction = updatedTransaction.copy(
            status = if (broadcastData.success) TransactionStatus.PENDING else TransactionStatus.FAILED,
            hash = broadcastData.hash ?: txHash,
            metadata = updatedTransaction.metadata + mapOf(
                "broadcastTime" to System.currentTimeMillis().toString(),
                "broadcastSuccess" to broadcastData.success.toString(),
                "broadcastError" to (broadcastData.error ?: "")
            )
        )
        transactionLocalDataSource.saveSendTransaction(finalTransaction)

        return Result.Success(broadcastData)
    }

    private fun getUSDCContractAddress(network: EthereumNetwork): String? {
        return when (network) {
            EthereumNetwork.MAINNET -> "0xA0b86991c6218b36c1d19D4a2e9Eb0cE3606eB48"
            EthereumNetwork.SEPOLIA -> "0x1c7D4B196Cb0C7B01d743Fbc6116a902379C7238"
            EthereumNetwork.POLYGON -> "0x2791Bca1f2de4661ED88A30C99A7a9449Aa84174"
            EthereumNetwork.BSC -> "0x8AC76a51cc950d9822D68b83fE1Ad97B32Cd580d"
            EthereumNetwork.ARBITRUM -> "0xFF970A61A04b1cA14834A43f5dE4533eBDDB5CC8"
            EthereumNetwork.OPTIMISM -> "0x7F5c764cBc14f9669B88837ca1490cCa17c31607"
            else -> null
        }
    }
}

class GetETHBalanceForGasUseCase @Inject constructor(
    private val walletRepository: WalletRepository,
    private val ethereumBlockchainRepository: EthereumBlockchainRepository
) {
    suspend operator fun invoke(walletId: String): Result<BigDecimal> {
        val wallet = walletRepository.getWallet(walletId)

        return when (wallet) {
            is USDCWallet -> try {
                val balance = ethereumBlockchainRepository.getEthereumBalance(
                    address = wallet.address,
                    network = wallet.network
                )
                Result.Success(balance)
            } catch (e: Exception) {
                Result.Error(
                    message = "Failed to get ETH balance: ${e.message}",
                    throwable = e
                )
            }
            is EthereumWallet -> try {
                val balance = ethereumBlockchainRepository.getEthereumBalance(
                    address = wallet.address,
                    network = wallet.network
                )
                Result.Success(balance)
            } catch (e: Exception) {
                Result.Error(
                    message = "Failed to get ETH balance: ${e.message}",
                    throwable = e
                )
            }
            else -> Result.Success(BigDecimal.ZERO)
        }
    }
}