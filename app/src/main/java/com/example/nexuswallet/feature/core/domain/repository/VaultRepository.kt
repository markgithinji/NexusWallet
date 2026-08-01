package com.example.nexuswallet.feature.core.domain.repository

interface VaultRepository {
    suspend fun storeEncryptedMnemonic(walletId: String, encryptedMnemonic: String, iv: ByteArray)
    suspend fun getEncryptedMnemonic(walletId: String): Pair<String, ByteArray>?

    suspend fun storeEncryptedPrivateKey(walletId: String, keyType: String, encryptedKey: String, iv: ByteArray)
    suspend fun getEncryptedPrivateKey(walletId: String, keyType: String): Pair<String, ByteArray>?

    suspend fun storeSecurityBundle(
        walletId: String,
        mnemonic: Pair<String, ByteArray>,
        privateKeys: Map<String, Pair<String, ByteArray>>
    )

    suspend fun getEncryptedBackup(walletId: String): Pair<String, ByteArray>?

    suspend fun clearVault()
}
