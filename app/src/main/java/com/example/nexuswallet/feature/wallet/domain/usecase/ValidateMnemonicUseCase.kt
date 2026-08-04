package com.example.nexuswallet.feature.wallet.domain.usecase

import com.example.nexuswallet.feature.logging.Logger
import org.web3j.crypto.MnemonicUtils
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ValidateMnemonicUseCase @Inject constructor(
    private val logger: Logger
) {

    operator fun invoke(mnemonic: List<CharArray>): Boolean {
        logger.d(TAG, "Validating mnemonic with ${mnemonic.size} words")

        return try {
            // SECURITY: Convert to String only at the point of validation
            val mnemonicString = mnemonic.map { String(it) }.joinToString(" ")
            val isValid = MnemonicUtils.validateMnemonic(mnemonicString)
            logger.d(TAG, "Mnemonic validation result: $isValid")
            isValid
        } catch (e: Exception) {
            logger.e(TAG, "Error validating mnemonic", e)
            false
        }
    }

    companion object {
        private const val TAG = "ValidateMnemonicUC"
    }
}