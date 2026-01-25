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

    // Session management
    private var lastAuthenticationTime = Long.MIN_VALUE
    private var sessionTimeout = 5 * 1000L // 5 minutes by default


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
    /**
     * Set PIN for additional security
     */
    suspend fun setPin(pin: String): Boolean {
        Log.d("SECURITY_PIN", " === SET PIN START ===")
        Log.d("SECURITY_PIN", "Setting PIN: '$pin' (length: ${pin.length})")

        return try {
            // Hash the PIN before storing
            Log.d("SECURITY_PIN", "Generating hash for PIN...")
            val pinHash = hashPin(pin)
            Log.d("SECURITY_PIN", "Generated PIN hash (first 30 chars): ${pinHash.take(30)}...")
            Log.d("SECURITY_PIN", "Full hash length: ${pinHash.length} chars")

            // Store in secure storage
            Log.d("SECURITY_PIN", "Storing PIN hash in secure storage...")
            secureStorage.storePinHash(pinHash)
            Log.d("SECURITY_PIN", " PIN hash stored")

            // Verify storage
            Log.d("SECURITY_PIN", "Verifying storage...")
            val storedHash = secureStorage.getPinHash()
            if (storedHash != null) {
                Log.d("SECURITY_PIN", " Retrieved stored hash")
                Log.d("SECURITY_PIN", "Stored hash matches original: ${storedHash == pinHash}")
                Log.d("SECURITY_PIN", " === SET PIN SUCCESS ===")
                true
            } else {
                Log.d("SECURITY_PIN", " Failed to retrieve stored hash!")
                Log.d("SECURITY_PIN", " === SET PIN FAILED ===")
                false
            }
        } catch (e: Exception) {
            Log.e("SECURITY_PIN", " Exception in setPin", e)
            Log.d("SECURITY_PIN", " === SET PIN ERROR ===")
            false
        }
    }
    /**
     * Verify PIN
     */
    /**
     * Verify PIN - FIXED VERSION
     */
    suspend fun verifyPin(pin: String): Boolean {
        Log.d("SECURITY_PIN", " === VERIFY PIN START ===")
        Log.d("SECURITY_PIN", "Verifying PIN: '$pin' (length: ${pin.length})")

        return try {
            Log.d("SECURITY_PIN", "Retrieving stored PIN hash...")
            val storedHash = secureStorage.getPinHash()

            if (storedHash == null) {
                Log.d("SECURITY_PIN", " No PIN hash stored in secure storage")
                Log.d("SECURITY_PIN", " === VERIFY PIN FAILED (no hash) ===")
                return false
            }

            Log.d("SECURITY_PIN", " Stored PIN hash retrieved")
            Log.d("SECURITY_PIN", "Stored hash (first 30 chars): ${storedHash.take(30)}...")
            Log.d("SECURITY_PIN", "Full stored hash length: ${storedHash.length} chars")

            //  CORRECT: Use verifyPinHash with the stored hash (extracts salt from it)
            Log.d("SECURITY_PIN", "Using verifyPinHash() with stored hash...")
            val isValid = verifyPinHash(pin, storedHash)
            Log.d("SECURITY_PIN", "PIN verification result: $isValid")

            if (!isValid) {
                // Debug: Show what happens when we use hashPin() (wrong way)
                Log.d("SECURITY_PIN", "⚠ DEBUG: Using hashPin() for comparison (WRONG WAY)...")
                val wrongHash = hashPin(pin)
                Log.d("SECURITY_PIN", "Wrong hash (first 30 chars): ${wrongHash.take(30)}...")
                Log.d("SECURITY_PIN", "Wrong hash == stored hash? ${wrongHash == storedHash}")
                Log.d("SECURITY_PIN", "This fails because hashPin() generates NEW random salt!")
            }

            Log.d("SECURITY_PIN", " === VERIFY PIN END (result: $isValid) ===")
            isValid

        } catch (e: Exception) {
            Log.e("SECURITY_PIN", " Exception in verifyPin", e)
            Log.d("SECURITY_PIN", " === VERIFY PIN ERROR ===")
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
    /**
     * Hash PIN using SHA-256 with salt
     */
    private fun hashPin(pin: String): String {
        Log.d("SECURITY_HASH", " Hashing PIN: '$pin'")

        val salt = generateSalt()
        Log.d("SECURITY_HASH", " Generated NEW random salt (hex): ${salt.toHex()}")
        Log.d("SECURITY_HASH", "Salt bytes: ${salt.joinToString { "%02x".format(it) }}")

        val message = "$pin${salt.toHex()}"
        Log.d("SECURITY_HASH", "Message to hash: '$message'")

        val digest = java.security.MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(message.toByteArray())

        val result = "${hash.toHex()}:${salt.toHex()}"
        Log.d("SECURITY_HASH", "Final hash (first 40 chars): ${result.take(40)}...")
        Log.d("SECURITY_HASH", "Total hash length: ${result.length} chars")

        return result
    }

    /**
     * Verify PIN hash
     */
    /**
     * Verify PIN hash using stored salt
     */
    private fun verifyPinHash(inputPin: String, storedHash: String): Boolean {
        Log.d("SECURITY_HASH", " === VERIFY PIN HASH START ===")
        Log.d("SECURITY_HASH", "Input PIN: '$inputPin'")
        Log.d("SECURITY_HASH", "Stored hash (first 40 chars): ${storedHash.take(40)}...")

        val parts = storedHash.split(":")
        if (parts.size != 2) {
            Log.d("SECURITY_HASH", " Invalid stored hash format")
            return false
        }

        val storedHashPart = parts[0]
        val saltHex = parts[1]

        Log.d("SECURITY_HASH", "Extracted stored hash part (first 10 chars): ${storedHashPart.take(10)}...")
        Log.d("SECURITY_HASH", "Extracted salt (hex): $saltHex")
        Log.d("SECURITY_HASH", "Salt length: ${saltHex.length} chars")

        val message = "$inputPin$saltHex"
        Log.d("SECURITY_HASH", "Message to hash: '$message'")

        val digest = java.security.MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(message.toByteArray())
        val inputHashHex = hash.toHex()

        Log.d("SECURITY_HASH", "Generated hash (first 10 chars): ${inputHashHex.take(10)}...")
        Log.d("SECURITY_HASH", "Comparing: ${inputHashHex.take(10)}... == ${storedHashPart.take(10)}...")

        val isValid = inputHashHex == storedHashPart
        Log.d("SECURITY_HASH", "Hash comparison result: $isValid")

        if (!isValid) {
            Log.d("SECURITY_HASH", " Hash mismatch!")
            Log.d("SECURITY_HASH", "  Input hash: $inputHashHex")
            Log.d("SECURITY_HASH", "  Stored hash: $storedHashPart")
            Log.d("SECURITY_HASH", "  Equal? ${inputHashHex == storedHashPart}")
            Log.d("SECURITY_HASH", "  Length equal? ${inputHashHex.length == storedHashPart.length}")
        }

        Log.d("SECURITY_HASH", " === VERIFY PIN HASH END (result: $isValid) ===")
        return isValid
    }

    // Utility functions
    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    private fun hexToBytes(hex: String): ByteArray {
        return hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    }

    fun recordAuthentication() {
        lastAuthenticationTime = System.currentTimeMillis()
        Log.d("SecurityDebug", " AUTHENTICATION RECORDED at $lastAuthenticationTime")
        Log.d("SecurityDebug", "Session will be valid for ${sessionTimeout/1000} seconds")
    }

    /**
     * Check if session is still valid
     */
    fun isSessionValid(): Boolean {
        // If never authenticated, session is not valid
        if (lastAuthenticationTime == Long.MIN_VALUE) {
            Log.d("SecurityDebug", " Session NOT valid: Never authenticated")
            return false
        }

        // Check if session has expired
        val currentTime = System.currentTimeMillis()

        // Handle potential overflow (though unlikely with timestamps)
        if (lastAuthenticationTime > currentTime) {
            Log.d("SecurityDebug", "⚠ Clock anomaly detected: lastAuth > currentTime")
            return false
        }

        val timeSinceAuth = currentTime - lastAuthenticationTime
        val isValid = timeSinceAuth < sessionTimeout

        Log.d("SecurityDebug", "Session check:")
        Log.d("SecurityDebug", "- Last auth: ${formatTime(lastAuthenticationTime)}")
        Log.d("SecurityDebug", "- Current: ${formatTime(currentTime)}")
        Log.d("SecurityDebug", "- Time since auth: ${timeSinceAuth / 1000} seconds")
        Log.d("SecurityDebug", "- Timeout: ${sessionTimeout / 1000} seconds")
        Log.d("SecurityDebug", "- ${if (isValid) " Session valid" else " Session expired"}")

        return isValid
    }

    private fun formatTime(timestamp: Long): String {
        return if (timestamp == Long.MIN_VALUE) {
            "Never"
        } else {
            val sdf = java.text.SimpleDateFormat("HH:mm:ss.SSS", java.util.Locale.getDefault())
            sdf.format(java.util.Date(timestamp))
        }
    }

    /**
     * Clear authentication session
     */
    fun clearSession() {
        lastAuthenticationTime = Long.MIN_VALUE
        Log.d("SecurityManager", "Session cleared")
    }

    /**
     * Set session timeout in seconds
     */
    suspend fun setSessionTimeout(seconds: Int) {
        sessionTimeout = seconds * 1000L
        secureStorage.saveSessionTimeout(seconds)
        Log.d("SecurityManager", "Session timeout set to ${seconds}s")
    }

    /**
     * Get current session timeout
     */
    suspend fun getSessionTimeout(): Long {
        return secureStorage.getSessionTimeout() * 1000L
    }

    /**
     * Check if authentication is required for an action
     */
    suspend fun isAuthenticationRequired(
        action: AuthAction = AuthAction.VIEW_WALLET
    ): Boolean {
        Log.d("SecurityDebug", " === CHECKING AUTHENTICATION ===")
        Log.d("SecurityDebug", "Action: $action")

        // Check session
        val sessionValid = isSessionValid()
        Log.d("SecurityDebug", "Session valid: $sessionValid")
        Log.d("SecurityDebug", "lastAuthenticationTime: $lastAuthenticationTime")
        Log.d("SecurityDebug", "currentTime: ${System.currentTimeMillis()}")
        Log.d("SecurityDebug", "timeDiff: ${System.currentTimeMillis() - lastAuthenticationTime}")
        Log.d("SecurityDebug", "sessionTimeout: $sessionTimeout")

        if (sessionValid) {
            Log.d("SecurityDebug", " Session valid, NO auth required")
            return false
        }

        // Check if auth methods are set up
        val pinSet = isPinSet()
        val biometricEnabled = isBiometricEnabled()
        val hasAuthEnabled = pinSet || biometricEnabled

        Log.d("SecurityDebug", "PIN set: $pinSet")
        Log.d("SecurityDebug", "Biometric enabled: $biometricEnabled")
        Log.d("SecurityDebug", "hasAuthEnabled: $hasAuthEnabled")

        if (!hasAuthEnabled) {
            Log.d("SecurityDebug", " No auth methods enabled, NO auth required")
            return false
        }

        // Check based on action
        val requiresAuth = when (action) {
            AuthAction.VIEW_WALLET -> true
            AuthAction.SEND_TRANSACTION -> true
            AuthAction.VIEW_PRIVATE_KEY -> true
            AuthAction.BACKUP_WALLET -> true
        }

        Log.d("SecurityDebug", "requiresAuth for $action: $requiresAuth")
        Log.d("SecurityDebug", " === RESULT: ${if (requiresAuth) "AUTH REQUIRED" else "NO AUTH"} ===")

        return requiresAuth
    }
    /**
     * Get available authentication methods for user
     */
    suspend fun getAvailableAuthMethods(): List<AuthMethod> {
        val methods = mutableListOf<AuthMethod>()

        if (isPinSet()) {
            methods.add(AuthMethod.PIN)
        }

        if (isBiometricEnabled()) {
            methods.add(AuthMethod.BIOMETRIC)
        }

        return methods
    }

    /**
     * Clear PIN (remove PIN protection)
     */
    suspend fun clearPin() {
        secureStorage.clearPinHash()
        Log.d("SecurityManager", "PIN cleared")
    }

    /**
     * Check if any authentication is set up
     */
    suspend fun isAnyAuthEnabled(): Boolean {
        return isPinSet() || isBiometricEnabled()
    }
}

enum class AuthAction {
    VIEW_WALLET,
    SEND_TRANSACTION,
    VIEW_PRIVATE_KEY,
    BACKUP_WALLET
}

enum class AuthMethod {
    PIN,
    BIOMETRIC
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