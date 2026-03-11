package com.example.nexuswallet.feature.coin.ethereum.domain.usecase

import com.example.nexuswallet.feature.coin.FeeLevel
import com.example.nexuswallet.feature.coin.Result
import com.example.nexuswallet.feature.coin.SendValidationResult
import com.example.nexuswallet.feature.coin.ethereum.domain.repository.EVMBlockchainRepository
import com.example.nexuswallet.feature.logging.Logger
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.EVMToken
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.NativeETH
import org.web3j.abi.datatypes.Address
import java.math.BigDecimal
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ValidateEVMSendUseCase @Inject constructor(
    private val evmBlockchainRepository: EVMBlockchainRepository,
    private val logger: Logger
) {

    private val tag = "ValidateEVMSendUC"

    suspend operator fun invoke(
        toAddress: String,
        amountValue: BigDecimal,
        fromAddress: String,
        tokenBalance: BigDecimal,
        ethBalance: BigDecimal,
        feeLevel: FeeLevel,
        token: EVMToken
    ): SendValidationResult {

        // Validate address is not empty
        if (toAddress.isBlank()) {
            logger.w(tag, "Address is empty")
            return SendValidationResult(
                isValid = false,
                addressError = "Please enter a recipient address"
            )
        }

        // Validate address format using web3j
        if (!isValidEthereumAddress(toAddress)) {
            logger.w(tag, "Invalid Ethereum address format: $toAddress")
            return SendValidationResult(
                isValid = false,
                addressError = "Invalid Ethereum address format"
            )
        }

        // Validate not sending to self
        if (toAddress.equals(fromAddress, ignoreCase = true)) {
            logger.w(tag, "Attempted self-send")
            return SendValidationResult(
                isValid = false,
                selfSendError = "Cannot send to yourself"
            )
        }

        // Validate amount > 0
        if (amountValue <= BigDecimal.ZERO) {
            logger.w(tag, "Invalid amount: $amountValue")
            return SendValidationResult(
                isValid = false,
                amountError = "Amount must be greater than zero"
            )
        }

        // Get fee estimate directly from repository
        val feeResult = evmBlockchainRepository.getFeeEstimate(
            feeLevel = feeLevel,
            network = token.network,
            isToken = token !is NativeETH
        )

        val feeEstimate = when (feeResult) {
            is Result.Success -> feeResult.data
            is Result.Error -> {
                logger.e(tag, "Failed to get fee estimate: ${feeResult.message}")
                return SendValidationResult(
                    isValid = false,
                    gasError = "Failed to estimate gas fee"
                )
            }

            else -> return SendValidationResult(
                isValid = false,
                gasError = "Failed to estimate gas fee"
            )
        }

        val feeEth = feeEstimate.totalFeeEth.toBigDecimalOrNull() ?: BigDecimal("0.001")

        // Check if it's a token transfer
        if (token !is NativeETH) {
            // For token transfers, need enough token balance AND enough ETH for gas
            if (amountValue > tokenBalance) {
                logger.w(tag, "Insufficient token balance")
                return SendValidationResult(
                    isValid = false,
                    balanceError = "Insufficient ${token.symbol} balance"
                )
            }

            if (ethBalance < feeEth) {
                logger.w(tag, "Insufficient ETH for gas")
                return SendValidationResult(
                    isValid = false,
                    gasError = "Insufficient ETH for gas fees. You need at least ${feeEth.setScale(6)} ETH"
                )
            }
        } else {
            // For ETH transfers, total amount + fee must be <= balance
            val totalRequired = amountValue + feeEth
            if (totalRequired > ethBalance) {
                logger.w(tag, "Insufficient ETH balance")
                return SendValidationResult(
                    isValid = false,
                    balanceError = "Insufficient balance. You have ${ethBalance.setScale(6)} ETH but need ${
                        totalRequired.setScale(
                            6
                        )
                    } ETH (including fees)"
                )
            }
        }

        // All validations passed
        return SendValidationResult(isValid = true)
    }

    private fun isValidEthereumAddress(address: String): Boolean {
        return try {
            Address(address)
            true
        } catch (e: Exception) {
            false
        }
    }
}