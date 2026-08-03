package com.example.nexuswallet.feature.ethereum.domain.usecase

import com.example.nexuswallet.feature.core.domain.model.FeeLevel
import com.example.nexuswallet.feature.core.util.Result
import com.example.nexuswallet.feature.ethereum.domain.model.EVMFeeEstimate
import com.example.nexuswallet.feature.ethereum.domain.repository.EVMBlockchainRepository
import com.example.nexuswallet.feature.ethereum.util.EVMConstants.DEFAULT_TOKEN_GAS_LIMIT
import com.example.nexuswallet.feature.ethereum.util.EVMConstants.GAS_LIMIT_STANDARD
import com.example.nexuswallet.feature.ethereum.util.EVMConstants.GWEI_TO_WEI
import com.example.nexuswallet.feature.ethereum.util.EVMConstants.USDT_GAS_LIMIT
import com.example.nexuswallet.feature.ethereum.util.EVMConstants.WEI_PER_ETH
import com.example.nexuswallet.feature.logging.Logger
import com.example.nexuswallet.feature.wallet.domain.model.EthereumNetwork
import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GetFeeEstimateUseCase @Inject constructor(
    private val evmBlockchainRepository: EVMBlockchainRepository,
    private val logger: Logger
) {

    suspend operator fun invoke(
        feeLevel: FeeLevel,
        network: EthereumNetwork,
        isToken: Boolean,
        fromAddress: String? = null,
        toAddress: String? = null,
        amount: BigInteger? = null,
        tokenContract: String? = null
    ): Result<EVMFeeEstimate> {
        logger.d(TAG, "Getting fee estimate for $feeLevel on ${network.name} (isToken=$isToken)")

        // 1. Get gas limit - try dynamic estimation if we have data
        val gasLimitResult = if (fromAddress != null && toAddress != null && amount != null) {
            evmBlockchainRepository.estimateGas(
                fromAddress = fromAddress,
                toAddress = toAddress,
                amount = amount,
                tokenContract = tokenContract,
                network = network
            )
        } else {
            null
        }

        val gasLimit = when (gasLimitResult) {
            is Result.Success -> gasLimitResult.data
            else -> {
                // Fallback logic if dynamic estimation fails
                when {
                    !isToken -> BigInteger.valueOf(GAS_LIMIT_STANDARD)
                    tokenContract?.equals(network.usdtContractAddress, ignoreCase = true) == true -> 
                        BigInteger.valueOf(USDT_GAS_LIMIT)
                    else -> BigInteger.valueOf(DEFAULT_TOKEN_GAS_LIMIT)
                }
            }
        }

        // 2. Get current gas price in Gwei from repository
        return when (val gasPriceResult = evmBlockchainRepository.getCurrentGasPrice(network)) {
            is Result.Success -> {
                val gasPrice = gasPriceResult.data
                val isEIP1559 = gasPrice.baseFee != null

                if (isEIP1559) {
                    val baseFeeGwei = BigDecimal(gasPrice.baseFee)
                    val priorityFeeGwei = when (feeLevel) {
                        FeeLevel.SLOW -> BigDecimal(gasPrice.safePriorityFee!!)
                        FeeLevel.NORMAL -> BigDecimal(gasPrice.proposePriorityFee!!)
                        FeeLevel.FAST -> BigDecimal(gasPrice.fastPriorityFee!!)
                    }

                    // Max Fee = (Base Fee * 2) + Priority Fee
                    val maxFeeGwei = baseFeeGwei.multiply(BigDecimal("2")).add(priorityFeeGwei)

                    // Convert to Wei
                    val maxFeeWei = maxFeeGwei.multiply(BigDecimal(GWEI_TO_WEI)).toBigInteger()

                    // Total fee (estimated max)
                    val totalFeeWei = maxFeeWei.multiply(gasLimit)

                    // Convert to ETH for display
                    val totalFeeEth = BigDecimal(totalFeeWei).divide(
                        BigDecimal(WEI_PER_ETH),
                        18,
                        RoundingMode.HALF_UP
                    ).toPlainString()

                    val estimatedTime = when (feeLevel) {
                        FeeLevel.SLOW -> ESTIMATED_TIME_SLOW
                        FeeLevel.NORMAL -> ESTIMATED_TIME_NORMAL
                        FeeLevel.FAST -> ESTIMATED_TIME_FAST
                    }

                    Result.Success(
                        EVMFeeEstimate(
                            gasPriceGwei = maxFeeGwei.setScale(6, RoundingMode.HALF_UP).toString(),
                            gasPriceWei = maxFeeWei.toString(),
                            gasLimit = gasLimit.toLong(),
                            totalFeeWei = totalFeeWei.toString(),
                            totalFeeEth = totalFeeEth,
                            estimatedTime = estimatedTime,
                            priority = feeLevel,
                            baseFee = baseFeeGwei.setScale(6, RoundingMode.HALF_UP).toString(),
                            maxPriorityFeeGwei = priorityFeeGwei.setScale(6, RoundingMode.HALF_UP).toString(),
                            isEIP1559 = true
                        )
                    )
                } else {
                    // Legacy fallback
                    val gasPriceGwei = when (feeLevel) {
                        FeeLevel.SLOW -> gasPrice.safe
                        FeeLevel.NORMAL -> gasPrice.propose
                        FeeLevel.FAST -> gasPrice.fast
                    }

                    val gasPriceWei = BigDecimal(gasPriceGwei)
                        .multiply(BigDecimal(GWEI_TO_WEI))
                        .toBigInteger()

                    val totalFeeWei = gasPriceWei.multiply(gasLimit)

                    val totalFeeEth = BigDecimal(totalFeeWei).divide(
                        BigDecimal(WEI_PER_ETH),
                        18,
                        RoundingMode.HALF_UP
                    ).toPlainString()

                    val estimatedTime = when (feeLevel) {
                        FeeLevel.SLOW -> ESTIMATED_TIME_SLOW
                        FeeLevel.NORMAL -> ESTIMATED_TIME_NORMAL
                        FeeLevel.FAST -> ESTIMATED_TIME_FAST
                    }

                    Result.Success(
                        EVMFeeEstimate(
                            gasPriceGwei = gasPriceGwei,
                            gasPriceWei = gasPriceWei.toString(),
                            gasLimit = gasLimit.toLong(),
                            totalFeeWei = totalFeeWei.toString(),
                            totalFeeEth = totalFeeEth,
                            estimatedTime = estimatedTime,
                            priority = feeLevel,
                            isEIP1559 = false
                        )
                    )
                }
            }

            is Result.Error -> {
                logger.e(TAG, "Failed to get gas price: ${gasPriceResult.message}")
                Result.Error(gasPriceResult.message, gasPriceResult.throwable)
            }

            Result.Loading -> Result.Error("Gas price request timed out")
        }
    }

    companion object {
        private const val TAG = "GetFeeEstimateUC"
        private const val ESTIMATED_TIME_SLOW = 120
        private const val ESTIMATED_TIME_NORMAL = 60
        private const val ESTIMATED_TIME_FAST = 30
    }
}