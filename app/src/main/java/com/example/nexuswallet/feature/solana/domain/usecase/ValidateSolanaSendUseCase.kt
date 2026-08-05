package com.example.nexuswallet.feature.solana.domain.usecase

import com.example.nexuswallet.feature.core.domain.model.SendValidationResult
import com.example.nexuswallet.feature.logging.Logger
import com.example.nexuswallet.feature.solana.domain.model.SolanaFeeEstimate
import org.sol4k.PublicKey
import java.math.BigDecimal
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ValidateSolanaSendUseCase @Inject constructor(
    private val logger: Logger
) {

    operator fun invoke(
        toAddress: String,
        amountValue: BigDecimal,
        walletAddress: String,
        balance: BigDecimal,
        feeEstimate: SolanaFeeEstimate?
    ): SendValidationResult {

        // Validate address is not empty
        if (toAddress.isBlank()) {
            logger.w(TAG, "Address is empty")
            return SendValidationResult(
                isValid = false,
                addressError = "Please enter a recipient address"
            )
        }

        // Validate Solana address format using sol4k
        val addressValidationResult = validateSolanaAddress(toAddress)
        if (!addressValidationResult.isValid) {
            return addressValidationResult
        }

        // Validate not sending to self
        if (toAddress == walletAddress) {
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
        val feeSol = feeEstimate?.feeSol?.toBigDecimalOrNull() ?: BigDecimal("0.000005")
        val totalRequired = amountValue + feeSol

        // Check against user's actual balance
        if (totalRequired > balance) {
            logger.w(TAG, "Insufficient balance: have $balance SOL, need $totalRequired SOL")
            return SendValidationResult(
                isValid = false,
                balanceError = "Insufficient balance. You have ${balance.setScale(9).stripTrailingZeros().toPlainString()} SOL but need ${
                    totalRequired.setScale(9).stripTrailingZeros().toPlainString()
                } SOL (including fees)"
            )
        }

        // All validations passed
        val result = SendValidationResult(isValid = true)
        
        // Add a warning if sending to a known program address
        if (KNOWN_PROGRAM_IDS.contains(toAddress)) {
            return result.copy(addressWarning = "Warning: This address belongs to a known Solana Program. Sending funds here may result in loss of access unless the program supports it.")
        }

        return result
    }

    private fun validateSolanaAddress(address: String): SendValidationResult {
        return try {
            // sol4k's PublicKey constructor validates the address format
            PublicKey(address)
            SendValidationResult(isValid = true)
        } catch (e: Exception) {
            SendValidationResult(
                isValid = false,
                addressError = "Invalid Solana address format"
            )
        }
    }

    companion object {
        private const val TAG = "ValidateSolanaSendUC"
        
        private val KNOWN_PROGRAM_IDS = setOf(
            "11111111111111111111111111111111", // System Program
            "TokenkegQFEZ9iYVz3kn7p5LpLxyG99m966F6LxhS", // Token Program
            "ATokenGPvbdGVxr1b2hvZbsiqW5xWH25efTNsLJA8knL", // Associated Token Program
            "Config1111111111111111111111111111111111111", // Config Program
            "Stake11111111111111111111111111111111111111", // Stake Program
            "Vote111111111111111111111111111111111111111", // Vote Program
            "BPFLoaderUpgradeab1e11111111111111111111111", // BPF Loader
        )
    }
}
