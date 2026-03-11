package com.example.nexuswallet.feature.bitcoin.domain.usecase

import com.example.nexuswallet.feature.core.domain.model.SendValidationResult
import com.example.nexuswallet.feature.coin.bitcoin.domain.model.BitcoinFeeEstimate
import com.example.nexuswallet.feature.logging.Logger
import com.example.nexuswallet.feature.wallet.domain.BitcoinNetwork
import com.example.nexuswallet.feature.wallet.domain.model.Wallet
import org.bitcoinj.core.Address
import org.bitcoinj.params.MainNetParams
import org.bitcoinj.params.TestNet3Params
import java.math.BigDecimal
import javax.inject.Inject
import javax.inject.Singleton


@Singleton
class ValidateBitcoinTransactionUseCase @Inject constructor(
    private val logger: Logger
) {

    private val tag = "ValidateBitcoinTxUC"

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
            logger.w(tag, "Wallet not found: $walletId")
            return SendValidationResult(
                isValid = false,
                addressError = "Wallet not found"
            )
        }

        // Validate Bitcoin is enabled for this network
        val bitcoinCoin = wallet.bitcoinCoins.find { it.network == network }
        if (bitcoinCoin == null) {
            logger.w(tag, "Bitcoin not enabled for $network in wallet: ${wallet.name}")
            return SendValidationResult(
                isValid = false,
                addressError = "Bitcoin not enabled for $network"
            )
        }

        // Validate address is not empty
        if (toAddress.isBlank()) {
            logger.w(tag, "Address is empty")
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
            logger.d(tag, "Valid $network address: ${toAddress.take(8)}...")
            true
        } catch (e: Exception) {
            logger.e(tag, "Invalid $network address: ${toAddress.take(8)}...")
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
            logger.w(tag, "Attempted self-send")
            return SendValidationResult(
                isValid = false,
                selfSendError = "Cannot send to yourself"
            )
        }

        // Validate amount > 0
        if (amount <= BigDecimal.ZERO) {
            logger.w(tag, "Invalid amount: $amount")
            return SendValidationResult(
                isValid = false,
                amountError = "Amount must be greater than zero"
            )
        }

        // Calculate total required including fees
        val feeBtc = if (feeEstimate != null) {
            BigDecimal(feeEstimate.totalFeeBtc)
        } else {
            BigDecimal("0.00001") // Default fallback for estimation
        }

        val totalRequired = amount + feeBtc

        // Check against user's actual balance
        if (totalRequired > balance) {
            logger.w(tag, "Insufficient balance: have $balance BTC, need $totalRequired BTC")
            return SendValidationResult(
                isValid = false,
                balanceError = "Insufficient balance. You have ${balance.setScale(8)} BTC but need ${
                    totalRequired.setScale(
                        8
                    )
                } BTC (including fees)"
            )
        }

        // All validations passed
        return SendValidationResult(isValid = true)
    }
}