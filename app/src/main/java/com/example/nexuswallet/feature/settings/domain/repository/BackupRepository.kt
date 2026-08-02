package com.example.nexuswallet.feature.settings.domain.repository

import com.example.nexuswallet.feature.core.util.Result
import com.example.nexuswallet.feature.settings.domain.model.BackupBundle

interface BackupRepository {
    /**
     * Encrypts a [BackupBundle] using a key derived from the given [pin].
     */
    suspend fun encryptBackup(bundle: BackupBundle, pin: String): Result<ByteArray>

    /**
     * Decrypts [backupData] into a [BackupBundle] using the given [pin].
     */
    suspend fun decryptBackup(backupData: ByteArray, pin: String): Result<BackupBundle>
}
