package com.example.nexuswallet.feature.ethereum.domain.usecase

import com.example.nexuswallet.feature.core.domain.model.SendValidationResult
import com.example.nexuswallet.feature.ethereum.domain.model.EVMFeeEstimate
import com.example.nexuswallet.feature.logging.Logger
import com.example.nexuswallet.feature.wallet.domain.model.EVMToken
import com.example.nexuswallet.feature.wallet.domain.model.NativeETH
import org.web3j.abi.datatypes.Address
import java.math.BigDecimal
import java.math.RoundingMode
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ValidateEVMSendUseCase @Inject constructor(
    private val logger: Logger
) {

    operator fun invoke(
        toAddress: String,
        amountValue: BigDecimal,
        fromAddress: String,
        tokenBalance: BigDecimal,
        ethBalance: BigDecimal,
        feeEstimate: EVMFeeEstimate?,
        token: EVMToken,
        isFeeLoading: Boolean = false
    ): SendValidationResult {

        // Validate address is not empty
        if (toAddress.isBlank()) {
            logger.w(TAG, "Address is empty")
            return SendValidationResult(
                isValid = false,
                addressError = "Please enter a recipient address"
            )
        }

        // Validate address format
        if (!isValidEthereumAddress(toAddress)) {
            logger.w(TAG, "Invalid Ethereum address format: $toAddress")
            return SendValidationResult(
                isValid = false,
                addressError = "Invalid Ethereum address format"
            )
        }

        // Validate not sending to self
        if (toAddress.equals(fromAddress, ignoreCase = true)) {
            logger.w(TAG, "Attempted self-send")
            return SendValidationResult(
                isValid = false,
                selfSendError = "Cannot send to yourself"
            )
        }

        // Validate amount > 0
        if (amountValue <= BigDecimal.ZERO) {
            logger.w(TAG, "Invalid amount: $amountValue")
            return SendValidationResult(
                isValid = false,
                amountError = "Amount must be greater than zero"
            )
        }

        // Calculate total required including fees
        val feeEth = if (feeEstimate != null && !isFeeLoading) {
            feeEstimate.totalFeeEth.toBigDecimalOrNull() ?: BigDecimal("0.001")
        } else {
            // If fee is loading, don't block validation with a stale or placeholder fee
            BigDecimal.ZERO
        }

        // Check if it's a token transfer
        if (token !is NativeETH) {
            // For token transfers, need enough token balance AND enough ETH for gas
            if (amountValue > tokenBalance) {
                logger.w(TAG, "Insufficient token balance")
                return SendValidationResult(
                    isValid = false,
                    balanceError = "Insufficient ${token.symbol} balance"
                )
            }

            if (ethBalance < feeEth) {
                logger.w(TAG, "Insufficient ETH for gas")
                val formattedFee = feeEth.setScale(6, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()
                return SendValidationResult(
                    isValid = false,
                    gasError = "Insufficient ETH for gas fees. You need at least $formattedFee ETH"
                )
            }
        } else {
            // For ETH transfers, total amount + fee must be <= balance
            val totalRequired = amountValue + feeEth
            if (totalRequired > ethBalance) {
                logger.w(TAG, "Insufficient ETH balance")
                val formattedBalance = ethBalance.setScale(6, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()
                val formattedRequired = totalRequired.setScale(6, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()
                return SendValidationResult(
                    isValid = false,
                    balanceError = "Insufficient balance. You have $formattedBalance ETH but need $formattedRequired ETH (including fees)"
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

    companion object {
        private const val TAG = "ValidateEVMSendUC"
    }
}