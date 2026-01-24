package com.example.nexuswallet

import com.example.nexuswallet.data.model.WalletBackup
import kotlinx.serialization.json.Json
import android.content.Context
import android.util.Log
import com.example.nexuswallet.data.model.CryptoWallet
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.security.SecureRandom

/**
 * Main security manager that coordinates all security operations
 * Uses Android KeyStore for encryption and DataStore for secure storage
 */
class SecurityManager(private val context: Context) {

    private val scope = CoroutineScope(Dispatchers.IO)
    private val keyStoreEncryption = KeyStoreEncryption(context)
    private val secureStorage = SecureStorage(context)

    private val _securityState = MutableStateFlow<SecurityState>(SecurityState.IDLE)
    val securityState: StateFlow<SecurityState> = _securityState

    /**
     * Encrypt and store mnemonic phrase
     */
    suspend fun secureMnemonic(
        walletId: String,
        mnemonic: List<String>
    ): EncryptionResult {
        return try {
            _securityState.value = SecurityState.ENCRYPTING

            // Convert mnemonic to string
            val mnemonicString = mnemonic.joinToString(" ")

            // Encrypt using KeyStore
            val (encryptedHex, ivHex) = keyStoreEncryption.encryptString(mnemonicString)

            // Store in secure storage
            secureStorage.storeEncryptedMnemonic(
                walletId = walletId,
                encryptedMnemonic = encryptedHex,
                iv = hexToBytes(ivHex)
            )

            _securityState.value = SecurityState.IDLE
            EncryptionResult.Success(encryptedHex)

        } catch (e: Exception) {
            Log.e("SecurityManager", "Failed to secure mnemonic", e)
            _securityState.value = SecurityState.ERROR(e.message ?: "Encryption failed")
            EncryptionResult.Error(e)
        }
    }

    /**
     * Retrieve and decrypt mnemonic phrase
     */
    suspend fun retrieveMnemonic(walletId: String): List<String>? {
        return try {
            _securityState.value = SecurityState.DECRYPTING

            // Get encrypted data
            val encryptedData = secureStorage.getEncryptedMnemonic(walletId)
            if (encryptedData == null) {
                _securityState.value = SecurityState.IDLE
                return null
            }

            val (encryptedHex, iv) = encryptedData

            // Decrypt using KeyStore
            val decryptedString = keyStoreEncryption.decryptString(
                encryptedHex,
                iv.toHex()
            )

            _securityState.value = SecurityState.IDLE
            decryptedString.split(" ")

        } catch (e: Exception) {
            Log.e("SecurityManager", "Failed to retrieve mnemonic", e)
            _securityState.value = SecurityState.ERROR(e.message ?: "Decryption failed")
            null
        }
    }

    /**
     * Encrypt and store private key
     */
    suspend fun securePrivateKey(
        walletId: String,
        keyType: String,
        privateKey: String
    ): EncryptionResult {
        return try {
            _securityState.value = SecurityState.ENCRYPTING

            val (encryptedHex, ivHex) = keyStoreEncryption.encryptString(privateKey)

            secureStorage.storeEncryptedPrivateKey(
                walletId = walletId,
                keyType = keyType,
                encryptedKey = encryptedHex,
                iv = hexToBytes(ivHex)
            )

            _securityState.value = SecurityState.IDLE
            EncryptionResult.Success(encryptedHex)

        } catch (e: Exception) {
            Log.e("SecurityManager", "Failed to secure private key", e)
            _securityState.value = SecurityState.ERROR(e.message ?: "Encryption failed")
            EncryptionResult.Error(e)
        }
    }

    /**
     * Create encrypted backup of wallet
     */
    suspend fun createEncryptedBackup(walletId: String, wallet: CryptoWallet): BackupResult {
        return try {
            _securityState.value = SecurityState.BACKING_UP

            // Get mnemonic first
            val mnemonic = retrieveMnemonic(walletId)
            if (mnemonic == null) {
                throw IllegalStateException("No mnemonic found for wallet")
            }

            // Create backup data
            val backupData = WalletBackup(
                walletId = walletId,
                encryptedMnemonic = "encrypted_placeholder", // Will be replaced
                encryptedPrivateKey = "encrypted_placeholder",
                encryptionIV = "",
                backupDate = System.currentTimeMillis(),
                walletType = wallet.walletType,
                metadata = mapOf(
                    "walletName" to wallet.name,
                    "address" to wallet.address,
                    "createdAt" to wallet.createdAt.toString()
                )
            )

            // Encrypt entire backup
            val backupJson = Json.encodeToString(backupData)
            val (encryptedHex, ivHex) = keyStoreEncryption.encryptString(backupJson)

            // Update backup with actual encrypted data
            val finalBackup = backupData.copy(
                encryptedMnemonic = encryptedHex,
                encryptionIV = ivHex
            )

            // Store backup
            secureStorage.storeEncryptedBackup(
                walletId = walletId,
                backupData = finalBackup,
                encryptedData = encryptedHex,
                iv = hexToBytes(ivHex)
            )

            _securityState.value = SecurityState.IDLE
            BackupResult.Success(finalBackup)

        } catch (e: Exception) {
            Log.e("SecurityManager", "Failed to create backup", e)
            _securityState.value = SecurityState.ERROR(e.message ?: "Backup failed")
            BackupResult.Error(e)
        }
    }

    /**
     * Restore wallet from encrypted backup
     */
    suspend fun restoreFromBackup(walletId: String): RestoreResult {
        return try {
            _securityState.value = SecurityState.RESTORING

            // Get encrypted backup
            val backupData = secureStorage.getEncryptedBackup(walletId)
            if (backupData == null) {
                throw IllegalStateException("No backup found")
            }

            val (encryptedHex, iv) = backupData

            // Decrypt backup
            val decryptedJson = keyStoreEncryption.decryptString(
                encryptedHex,
                iv.toHex()
            )

            val backup = Json.decodeFromString<WalletBackup>(decryptedJson)

            _securityState.value = SecurityState.IDLE
            RestoreResult.Success(backup)

        } catch (e: Exception) {
            Log.e("SecurityManager", "Failed to restore from backup", e)
            _securityState.value = SecurityState.ERROR(e.message ?: "Restore failed")
            RestoreResult.Error(e)
        }
    }

    /**
     * Set PIN for additional security
     */
    suspend fun setPin(pin: String): Boolean {
        return try {
            // Hash the PIN before storing
            val pinHash = hashPin(pin)
            secureStorage.storePinHash(pinHash)
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Verify PIN
     */
    suspend fun verifyPin(pin: String): Boolean {
        return try {
            val storedHash = secureStorage.getPinHash()
            if (storedHash == null) return false

            val inputHash = hashPin(pin)
            inputHash == storedHash
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Check if PIN is set
     */
    suspend fun isPinSet(): Boolean {
        return secureStorage.getPinHash() != null
    }

    /**
     * Enable/disable biometric authentication
     */
    suspend fun setBiometricEnabled(enabled: Boolean) {
        secureStorage.setBiometricEnabled(enabled)
    }

    /**
     * Check if biometric is enabled
     */
    suspend fun isBiometricEnabled(): Boolean {
        return secureStorage.isBiometricEnabled()
    }

    /**
     * Clear all security data (logout)
     */
    suspend fun clearAll() {
        secureStorage.clearAll()
        keyStoreEncryption.clearKey()
        _securityState.value = SecurityState.IDLE
    }

    /**
     * Check if KeyStore is available
     */
    fun isKeyStoreAvailable(): Boolean {
        return keyStoreEncryption.isKeyStoreAvailable()
    }

    /**
     * Generate secure random salt
     */
    private fun generateSalt(): ByteArray {
        val salt = ByteArray(16)
        SecureRandom().nextBytes(salt)
        return salt
    }

    /**
     * Hash PIN using SHA-256 with salt
     */
    private fun hashPin(pin: String): String {
        val salt = generateSalt()
        val message = "$pin${salt.toHex()}"
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(message.toByteArray())
        return "${hash.toHex()}:${salt.toHex()}"
    }

    /**
     * Verify PIN hash
     */
    private fun verifyPinHash(inputPin: String, storedHash: String): Boolean {
        val parts = storedHash.split(":")
        if (parts.size != 2) return false

        val storedHashPart = parts[0]
        val saltHex = parts[1]

        val message = "$inputPin$saltHex"
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(message.toByteArray())

        return hash.toHex() == storedHashPart
    }

    // Utility functions
    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    private fun hexToBytes(hex: String): ByteArray {
        return hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    }
}

sealed class EncryptionResult {
    data class Success(val encryptedData: String) : EncryptionResult()
    data class Error(val exception: Exception) : EncryptionResult()
}

sealed class BackupResult {
    data class Success(val backup: WalletBackup) : BackupResult()
    data class Error(val exception: Exception) : BackupResult()
}

sealed class RestoreResult {
    data class Success(val backup: WalletBackup) : RestoreResult()
    data class Error(val exception: Exception) : RestoreResult()
}

sealed class SecurityState {
    object IDLE : SecurityState()
    object ENCRYPTING : SecurityState()
    object DECRYPTING : SecurityState()
    object BACKING_UP : SecurityState()
    object RESTORING : SecurityState()
    data class ERROR(val message: String) : SecurityState()
}