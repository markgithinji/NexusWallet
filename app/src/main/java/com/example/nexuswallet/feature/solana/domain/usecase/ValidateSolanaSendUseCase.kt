package com.example.nexuswallet.feature.solana.domain.usecase

import com.example.nexuswallet.feature.core.domain.model.SendValidationResult
import com.example.nexuswallet.feature.logging.Logger
import com.example.nexuswallet.feature.solana.domain.model.SolanaFeeEstimate
import com.example.nexuswallet.feature.wallet.domain.model.SPLToken
import org.sol4k.PublicKey
import java.math.BigDecimal
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ValidateSolanaSendUseCase @Inject constructor(
    private val logger: Logger
) {
    private val solanaAddressRegex = Regex("^[1-9A-HJ-NP-Za-km-z]{32,44}$")

    operator fun invoke(
        toAddress: String,
        amountValue: BigDecimal,
        walletAddress: String,
        balance: BigDecimal, // Selected asset balance
        solBalance: BigDecimal, // Native SOL balance for fees
        feeEstimate: SolanaFeeEstimate?,
        selectedToken: SPLToken? = null,
        isFeeLoading: Boolean = false
    ): SendValidationResult {

        // Validate address is not empty
        if (toAddress.isBlank()) {
            return SendValidationResult(
                isValid = false,
                addressError = "Please enter a recipient address"
            )
        }

        // Validate Solana address format
        if (!solanaAddressRegex.matches(toAddress)) {
            val message = if (toAddress.length !in 32..44) {
                "Invalid Solana address length (expected 32-44 characters)"
            } else {
                "Address contains invalid characters (0, O, I, or l are not allowed)"
            }
            return SendValidationResult(
                isValid = false,
                addressError = message
            )
        }

        try {
            PublicKey(toAddress)
        } catch (_: Exception) {
            return SendValidationResult(
                isValid = false,
                addressError = "Invalid Solana address format"
            )
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

        // 1. Check selected asset balance (SOL or Token)
        val symbol = selectedToken?.symbol ?: "SOL"
        
        // If fee estimate is null or loading, it's likely still being fetched. 
        // We use 0 as fallback to avoid false "Insufficient funds" error during calculations.
        // The UI already disables the send button while isFeeLoading is true.
        val feeSol = if (feeEstimate != null && !isFeeLoading) {
            feeEstimate.feeSol.toBigDecimalOrNull() ?: BigDecimal.ZERO
        } else {
            BigDecimal.ZERO
        }
        
        val rentExemptThreshold = BigDecimal(com.example.nexuswallet.feature.solana.util.SolanaConstants.RENT_EXEMPT_MINIMUM_LAMPORTS)
            .divide(BigDecimal(com.example.nexuswallet.feature.solana.util.SolanaConstants.LAMPORTS_PER_SOL), 9, java.math.RoundingMode.HALF_UP)

        if (selectedToken == null) {
            // SOL Transfer
            val totalRequired = amountValue + feeSol
            
            // To be safe, the remaining balance after sending should either be 0 (full sweep)
            // or >= rent-exempt threshold.
            val remaining = solBalance - totalRequired
            
            logger.d(TAG, "SOL Validation: Amount=$amountValue, Fee=$feeSol, TotalRequired=$totalRequired, Balance=$solBalance, Remaining=$remaining")
            
            if (totalRequired > solBalance) {
                return SendValidationResult(
                    isValid = false,
                    balanceError = "Insufficient SOL. You need at least ${totalRequired.stripTrailingZeros().toPlainString()} SOL (including fees)"
                )
            }
            
            if (remaining > BigDecimal.ZERO && remaining < rentExemptThreshold) {
                return SendValidationResult(
                    isValid = false,
                    balanceError = "The remaining balance must be at least ${rentExemptThreshold.toPlainString()} SOL for rent exemption, or send your entire balance."
                )
            }
        } else {
            // SPL Token Transfer
            logger.d(TAG, "Token Validation: Asset=$symbol, Amount=$amountValue, Balance=$balance, Fee=$feeSol, SOLBalance=$solBalance")
            if (amountValue > balance) {
                return SendValidationResult(
                    isValid = false,
                    balanceError = "Insufficient $symbol balance"
                )
            }
            
            // Need enough SOL for gas (and potentially rent for ATA)
            val rentExemption = BigDecimal("0.00204") // Typical rent for Token account
            val solRequired = feeSol + rentExemption 
            
            logger.d(TAG, "Token Gas Check: SolRequired=$solRequired, SOLBalance=$solBalance")

            if (solBalance < solRequired) {
                return SendValidationResult(
                    isValid = false,
                    gasError = "Insufficient SOL for gas fees. You need at least ${solRequired.stripTrailingZeros().toPlainString()} SOL"
                )
            }
        }

        // All validations passed
        val result = SendValidationResult(isValid = true)
        
        // Add a warning if sending to a known program address
        if (KNOWN_PROGRAM_IDS.contains(toAddress)) {
            return result.copy(addressWarning = "Warning: This address belongs to a known Solana Program. Sending funds here may result in loss of access unless the program supports it.")
        }

        return result
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
