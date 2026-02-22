package com.example.nexuswallet.feature.wallet.data.securityrefactor
import org.bitcoinj.crypto.MnemonicCode
import org.web3j.crypto.MnemonicUtils
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GenerateMnemonicUseCase @Inject constructor() {
    operator fun invoke(wordCount: Int = 12): List<String> {
        val strength = when (wordCount) {
            12 -> 128
            15 -> 160
            18 -> 192
            21 -> 224
            24 -> 256
            else -> 128
        }

        val entropy = ByteArray(strength / 8)
        SecureRandom().nextBytes(entropy)

        return try {
            MnemonicUtils.generateMnemonic(entropy).split(" ")
        } catch (e: Exception) {
            MnemonicCode.INSTANCE.toMnemonic(entropy)
        }
    }
}

@Singleton
class ValidateMnemonicUseCase @Inject constructor() {
    operator fun invoke(mnemonic: List<String>): Boolean {
        return try {
            MnemonicUtils.validateMnemonic(mnemonic.joinToString(" "))
        } catch (e: Exception) {
            false
        }
    }
}