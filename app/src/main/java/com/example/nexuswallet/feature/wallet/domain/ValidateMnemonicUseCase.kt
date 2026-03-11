package com.example.nexuswallet.feature.wallet.domain

import com.example.nexuswallet.feature.logging.Logger
import org.web3j.crypto.MnemonicUtils
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ValidateMnemonicUseCase @Inject constructor(
    private val logger: Logger
) {

    private val tag = "ValidateMnemonicUC"

    operator fun invoke(mnemonic: List<String>): Boolean {
        logger.d(tag, "Validating mnemonic with ${mnemonic.size} words")

        return try {
            val isValid = MnemonicUtils.validateMnemonic(mnemonic.joinToString(" "))
            logger.d(tag, "Mnemonic validation result: $isValid")
            isValid
        } catch (e: Exception) {
            logger.e(tag, "Error validating mnemonic", e)
            false
        }
    }
}