package com.example.nexuswallet.feature.bitcoin.domain.usecase

import com.example.nexuswallet.feature.bitcoin.domain.model.BitcoinFeeEstimate
import com.example.nexuswallet.feature.core.domain.model.SendValidationResult
import com.example.nexuswallet.feature.logging.Logger
import com.example.nexuswallet.feature.wallet.domain.model.BitcoinNetwork
import com.example.nexuswallet.feature.wallet.domain.model.Wallet
import org.bitcoinj.core.Address
import org.bitcoinj.params.MainNetParams
import org.bitcoinj.params.TestNet3Params
import java.math.BigDecimal
import java.math.RoundingMode
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ValidateBitcoinTransactionUseCase @Inject constructor(
    private val logger: Logger
) {

    operator fun invoke(
        walletId: String,
        wallet: Wallet?,
        toAddress: String,
        amount: BigDecimal,
        network: BitcoinNetwork,
        balance: BigDecimal,
        feeEstimate: BitcoinFeeEstimate?
    ): SendValidationResult {

        // Validate wallet exists
        if (wallet == null) {
            logger.w(TAG, "Wallet not found: $walletId")
            return SendValidationResult(
                isValid = false,
                addressError = "Wallet not found"
            )
        }

        // Validate Bitcoin is enabled for this network
        val bitcoinCoin = wallet.bitcoinCoins.find { it.network == network }
        if (bitcoinCoin == null) {
            logger.w(TAG, "Bitcoin not enabled for $network in wallet: ${wallet.name}")
            return SendValidationResult(
                isValid = false,
                addressError = "Bitcoin not enabled for $network"
            )
        }

        // Validate address is not empty
        if (toAddress.isBlank()) {
            logger.w(TAG, "Address is empty")
            return SendValidationResult(
                isValid = false,
                addressError = "Please enter a recipient address"
            )
        }

        // Validate address format
        val isValidAddress = try {
            val params = when (network) {
                BitcoinNetwork.Mainnet -> MainNetParams.get()
                BitcoinNetwork.Testnet -> TestNet3Params.get()
            }
            Address.fromString(params, toAddress)
            logger.d(TAG, "Valid $network address: ${toAddress.take(8)}...")
            true
        } catch (e: Exception) {
            logger.e(TAG, "Invalid $network address: ${toAddress.take(8)}...")
            false
        }

        if (!isValidAddress) {
            return SendValidationResult(
                isValid = false,
                addressError = "Invalid Bitcoin address for ${network.name.lowercase()}"
            )
        }

        // Validate not sending to self
        if (toAddress == bitcoinCoin.address) {
            logger.w(TAG, "Attempted self-send")
            return SendValidationResult(
                isValid = false,
                selfSendError = "Cannot send to yourself"
            )
        }

        // Validate amount > 0
        if (amount <= BigDecimal.ZERO) {
            logger.w(TAG, "Invalid amount: $amount")
            return SendValidationResult(
                isValid = false,
                amountError = "Amount must be greater than zero"
            )
        }

        // Calculate total required including fees
        val feeBtc = if (feeEstimate != null) {
            BigDecimal(feeEstimate.totalFeeBtc)
        } else {
            // If the user entered an amount that looks like a Max amount (e.g. they clicked Max)
            // don't use a fixed fee fallback that might push them over the limit and show a false error.
            // The SendBottomBar already disables the button while isFeeLoading is true.
            BigDecimal.ZERO
        }

        val totalRequired = amount + feeBtc
        
        logger.d(TAG, "Validating: Balance=$balance, Amount=$amount, Fee=${feeEstimate?.totalFeeBtc ?: "NULL"}, Total=$totalRequired")

        // Check against user's actual balance
        if (totalRequired > balance) {
            val needed = totalRequired - balance
            logger.w(TAG, "Insufficient balance: have $balance BTC, need $totalRequired BTC (short by $needed)")
            
            val message = if (feeEstimate == null) {
                "Insufficient balance for transaction and estimated network fees."
            } else {
                "Insufficient balance. You have ${balance.setScale(8, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()} BTC but need ${
                    totalRequired.setScale(8, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()
                } BTC (including fees)"
            }

            return SendValidationResult(
                isValid = false,
                balanceError = message
            )
        }

        // All validations passed
        return SendValidationResult(isValid = true)
    }

    companion object {
        private const val TAG = "ValidateBitcoinTxUC"
    }
}