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

    operator fun invoke(wordCount: Int): List<String> {
        logger.d(TAG, "Generating mnemonic with word count: $wordCount")

        val strength = when (wordCount) {
            WORDS_12 -> STRENGTH_128
            WORDS_15 -> STRENGTH_160
            WORDS_18 -> STRENGTH_192
            WORDS_21 -> STRENGTH_224
            WORDS_24 -> STRENGTH_256
            else -> {
                logger.w(TAG, "Invalid word count: $wordCount, defaulting to 12 words")
                DEFAULT_STRENGTH
            }
        }

        val entropy = ByteArray(strength / BITS_TO_BYTES_DIVISOR)
        SecureRandom().nextBytes(entropy)

        return try {
            val mnemonic = MnemonicUtils.generateMnemonic(entropy).split(" ")
            logger.d(TAG, "Successfully generated ${mnemonic.size} word mnemonic")
            mnemonic
        } catch (e: Exception) {
            logger.e(
                TAG,
                "Failed to generate mnemonic with MnemonicUtils, falling back to MnemonicCode",
                e
            )
            val mnemonic = MnemonicCode.INSTANCE.toMnemonic(entropy)
            logger.d(TAG, "Successfully generated ${mnemonic.size} word mnemonic using fallback")
            mnemonic
        }
    }

    companion object {
        private const val TAG = "GenerateMnemonicUC"

        // Word count constants
        private const val WORDS_12 = 12
        private const val WORDS_15 = 15
        private const val WORDS_18 = 18
        private const val WORDS_21 = 21
        private const val WORDS_24 = 24

        // Strength constants (bits)
        private const val STRENGTH_128 = 128
        private const val STRENGTH_160 = 160
        private const val STRENGTH_192 = 192
        private const val STRENGTH_224 = 224
        private const val STRENGTH_256 = 256
        private const val DEFAULT_STRENGTH = STRENGTH_128

        // Conversion constant
        private const val BITS_TO_BYTES_DIVISOR = 8
    }
}