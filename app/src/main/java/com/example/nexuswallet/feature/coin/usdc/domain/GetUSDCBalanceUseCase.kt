package com.example.nexuswallet.feature.coin.usdc.domain

import android.util.Log
import com.example.nexuswallet.feature.wallet.data.local.TransactionLocalDataSource
import com.example.nexuswallet.feature.wallet.data.model.BroadcastResult
import com.example.nexuswallet.feature.coin.bitcoin.FeeLevel
import com.example.nexuswallet.feature.wallet.data.model.SendTransaction
import com.example.nexuswallet.feature.coin.ethereum.EthereumBlockchainRepository
import com.example.nexuswallet.feature.coin.usdc.USDCBlockchainRepository
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
import java.math.BigDecimal
import java.math.RoundingMode
import javax.inject.Inject
import javax.inject.Singleton

class GetUSDCBalanceUseCase @Inject constructor(
    private val walletRepository: WalletRepository,
    private val usdcBlockchainRepository: USDCBlockchainRepository
) {
    suspend operator fun invoke(walletId: String): TokenBalance {
        val wallet = walletRepository.getWallet(walletId)
        val (address, network) = when (wallet) {
            is USDCWallet -> Pair(wallet.address, wallet.network)
            is EthereumWallet -> Pair(wallet.address, wallet.network)
            else -> throw IllegalArgumentException("Wallet is not USDC or Ethereum type")
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
        val wallet = walletRepository.getWallet(walletId)
        val (address, network, contractAddress) = when (wallet) {
            is USDCWallet -> Triple(wallet.address, wallet.network, wallet.contractAddress)
            is EthereumWallet -> {
                val contract = getUSDCContractAddress(wallet.network)
                    ?: return Result.failure(IllegalArgumentException("USDC not supported on ${wallet.network}"))
                Triple(wallet.address, wallet.network, contract)
            }
            else -> return Result.failure(IllegalArgumentException("Wallet is not USDC or Ethereum wallet"))
        }

        // Get private key
        val privateKeyResult = keyManager.getPrivateKeyForSigning(walletId)
        if (privateKeyResult.isFailure) {
            return Result.failure(IllegalStateException("Cannot access private key"))
        }
        val privateKey = privateKeyResult.getOrThrow()

        // Create transaction
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
            gasPrice = "0",
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

        // Save to local storage
        transactionLocalDataSource.saveSendTransaction(transaction)

        // Create and sign transaction
        val (rawTransaction, signedHex, txHash) = usdcBlockchainRepository.createAndSignUSDCTransfer(
            fromAddress = address,
            fromPrivateKey = privateKey,
            toAddress = toAddress,
            amount = amount,
            network = network
        )

        // Update transaction with signed data
        val gasPriceWei = rawTransaction.gasPrice
        val gasLimit = rawTransaction.gasLimit
        val feeWei = gasPriceWei.multiply(gasLimit)
        val feeEth = feeWei.toBigDecimal().divide(
            BigDecimal("1000000000000000000"),
            8,
            RoundingMode.HALF_UP
        )

        val updatedTransaction = transaction.copy(
            gasPrice = gasPriceWei.toString(),
            gasLimit = gasLimit.toString(),
            fee = feeWei.toString(),
            feeDecimal = feeEth.toPlainString(),
            total = feeWei.toString(),
            totalDecimal = feeEth.toPlainString(),
            signedHex = signedHex,
            metadata = transaction.metadata + mapOf(
                "gasPriceWei" to gasPriceWei.toString(),
                "gasLimit" to gasLimit.toString(),
                "rawTransaction" to rawTransaction.toString()
            )
        )

        transactionLocalDataSource.saveSendTransaction(updatedTransaction)

        // Broadcast transaction
        val broadcastResult = usdcBlockchainRepository.broadcastUSDCTransaction(
            signedHex = signedHex,
            network = network
        )

        // Update transaction status
        val finalTransaction = updatedTransaction.copy(
            status = if (broadcastResult.success) TransactionStatus.PENDING else TransactionStatus.FAILED,
            hash = broadcastResult.hash ?: txHash,
            metadata = updatedTransaction.metadata + mapOf(
                "broadcastTime" to System.currentTimeMillis().toString(),
                "broadcastSuccess" to broadcastResult.success.toString(),
                "broadcastError" to (broadcastResult.error ?: "")
            )
        )
        transactionLocalDataSource.saveSendTransaction(finalTransaction)

        return Result.success(broadcastResult)
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

// Use Case to get ETH balance for gas
class GetETHBalanceForGasUseCase @Inject constructor(
    private val walletRepository: WalletRepository,
    private val ethereumBlockchainRepository: EthereumBlockchainRepository
) {
    suspend operator fun invoke(walletId: String): BigDecimal {
        val wallet = walletRepository.getWallet(walletId)
        return when (wallet) {
            is USDCWallet -> ethereumBlockchainRepository.getEthereumBalance(
                address = wallet.address,
                network = wallet.network
            )
            is EthereumWallet -> ethereumBlockchainRepository.getEthereumBalance(
                address = wallet.address,
                network = wallet.network
            )
            else -> BigDecimal.ZERO
        }
    }
}