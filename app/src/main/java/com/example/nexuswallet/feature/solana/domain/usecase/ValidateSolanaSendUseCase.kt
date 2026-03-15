package com.example.nexuswallet.feature.solana.domain.usecase

import com.example.nexuswallet.feature.core.domain.model.SendValidationResult
import com.example.nexuswallet.feature.core.util.Result
import com.example.nexuswallet.feature.logging.Logger
import com.example.nexuswallet.feature.solana.domain.model.SolanaFeeEstimate
import com.example.nexuswallet.feature.solana.domain.repository.SolanaBlockchainRepository
import java.math.BigDecimal
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ValidateSolanaSendUseCase @Inject constructor(
    private val logger: Logger
) {

    private val tag = "ValidateSolanaSendUC"

    operator fun invoke(
        toAddress: String,
        amountValue: BigDecimal,
        walletAddress: String,
        balance: BigDecimal,
        feeEstimate: SolanaFeeEstimate?,
        isAddressValid: Boolean = true
    ): SendValidationResult {

        // Validate address is not empty
        if (toAddress.isBlank()) {
            logger.w(tag, "Address is empty")
            return SendValidationResult(
                isValid = false,
                addressError = "Please enter a recipient address"
            )
        }

        if (!isAddressValid) {
            logger.w(tag, "Invalid address format")
            return SendValidationResult(
                isValid = false,
                addressError = "Invalid Solana address"
            )
        }

        // Validate not sending to self
        if (toAddress == walletAddress) {
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

        // Calculate total required including fees
        val feeSol = feeEstimate?.feeSol?.toBigDecimalOrNull() ?: BigDecimal("0.000005")
        val totalRequired = amountValue + feeSol

        // Check against user's actual balance
        if (totalRequired > balance) {
            logger.w(tag, "Insufficient balance: have $balance SOL, need $totalRequired SOL")
            return SendValidationResult(
                isValid = false,
                balanceError = "Insufficient balance. You have ${balance.setScale(9)} SOL but need ${
                    totalRequired.setScale(9)
                } SOL (including fees)"
            )
        }

        // All validations passed
        return SendValidationResult(isValid = true)
    }
}