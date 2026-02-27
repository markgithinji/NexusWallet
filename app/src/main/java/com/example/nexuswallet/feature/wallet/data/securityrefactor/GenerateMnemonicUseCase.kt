package com.example.nexuswallet.feature.wallet.data.securityrefactor

interface GenerateMnemonicUseCase {
    operator fun invoke(wordCount: Int): List<String>
}

interface ValidateMnemonicUseCase {
    operator fun invoke(mnemonic: List<String>): Boolean
}