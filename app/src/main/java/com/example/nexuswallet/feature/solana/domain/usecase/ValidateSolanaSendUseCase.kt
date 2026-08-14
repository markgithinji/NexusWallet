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
        selectedToken: SPLToken? = null
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
        val feeSol = feeEstimate?.feeSol?.toBigDecimalOrNull() ?: BigDecimal("0.000005")
        
        if (selectedToken == null) {
            // SOL Transfer
            val totalRequired = amountValue + feeSol
            if (totalRequired > solBalance) {
                return SendValidationResult(
                    isValid = false,
                    balanceError = "Insufficient SOL. You need at least ${totalRequired.stripTrailingZeros().toPlainString()} SOL (including fees)"
                )
            }
        } else {
            // SPL Token Transfer
            if (amountValue > balance) {
                return SendValidationResult(
                    isValid = false,
                    balanceError = "Insufficient $symbol balance"
                )
            }
            
            // Need enough SOL for gas (and potentially rent for ATA)
            val rentExemption = BigDecimal("0.00204") // Typical rent for Token account
            val solRequired = feeSol + rentExemption 
            
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
