package com.example.nexuswallet.feature.wallet.domain.usecase

import com.example.nexuswallet.feature.logging.Logger
import org.bitcoinj.crypto.MnemonicCode
import org.web3j.crypto.MnemonicUtils
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GenerateMnemonicUseCase @Inject constructor(
    private val logger: Logger
) {

    private val tag = "GenerateMnemonicUC"

    operator fun invoke(wordCount: Int): List<String> {
        logger.d(tag, "Generating mnemonic with word count: $wordCount")

        val strength = when (wordCount) {
            12 -> 128
            15 -> 160
            18 -> 192
            21 -> 224
            24 -> 256
            else -> {
                logger.w(tag, "Invalid word count: $wordCount, defaulting to 12 words")
                128
            }
        }

        val entropy = ByteArray(strength / 8)
        SecureRandom().nextBytes(entropy)

        return try {
            val mnemonic = MnemonicUtils.generateMnemonic(entropy).split(" ")
            logger.d(tag, "Successfully generated ${mnemonic.size} word mnemonic")
            mnemonic
        } catch (e: Exception) {
            logger.e(
                tag,
                "Failed to generate mnemonic with MnemonicUtils, falling back to MnemonicCode",
                e
            )
            val mnemonic = MnemonicCode.INSTANCE.toMnemonic(entropy)
            logger.d(tag, "Successfully generated ${mnemonic.size} word mnemonic using fallback")
            mnemonic
        }
    }
}