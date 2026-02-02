package com.example.nexuswallet.feature.authentication.domain

import com.example.nexuswallet.feature.wallet.domain.WalletBackup
import kotlinx.serialization.json.Json
import android.content.Context
import android.util.Log
import com.example.nexuswallet.feature.authentication.data.repository.SecurityPreferencesRepository
import com.example.nexuswallet.feature.wallet.domain.CryptoWallet
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.security.MessageDigest
import java.security.SecureRandom
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max

/**
 * Main security manager that coordinates all security operations
 * Uses Android KeyStore for encryption and DataStore for secure storage
 */
@Singleton
class SecurityManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val securityPreferencesRepository: SecurityPreferencesRepository
) {

    private val scope = CoroutineScope(Dispatchers.IO)
    private val keyStoreEncryption = KeyStoreEncryption(context)

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
            securityPreferencesRepository.storeEncryptedMnemonic(
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

    suspend fun encryptAndStorePrivateKey(
        walletId: String,
        privateKey: String,
        keyType: String = "ETH_PRIVATE_KEY"
    ): EncryptionResult {
        return try {
            _securityState.value = SecurityState.ENCRYPTING

            Log.d("SecurityManager", "Encrypting private key for wallet: $walletId")

            // Encrypt the private key
            val (encryptedHex, ivHex) = keyStoreEncryption.encryptString(privateKey)

            // Store in secure storage WITH keyType
            securityPreferencesRepository.storeEncryptedPrivateKey(
                walletId = walletId,
                keyType = keyType,  // ADD this
                encryptedKey = encryptedHex,
                iv = hexToBytes(ivHex)
            )

            Log.d("SecurityManager", "Private key encrypted and stored successfully")
            _securityState.value = SecurityState.IDLE
            EncryptionResult.Success(encryptedHex)

        } catch (e: Exception) {
            Log.e("SecurityManager", "Failed to encrypt private key", e)
            _securityState.value = SecurityState.ERROR(e.message ?: "Private key encryption failed")
            EncryptionResult.Error(e)
        }
    }


    /**
     * Retrieve and decrypt private key for signing
     * Requires authentication (PIN/Biometric) for sensitive operations
     */
    suspend fun getPrivateKeyForSigning(
        walletId: String,
        requireAuth: Boolean = true,
        keyType: String = "ETH_PRIVATE_KEY"  // ADD this parameter
    ): Result<String> {
        return try {
            _securityState.value = SecurityState.DECRYPTING

            // Check authentication for sensitive operations
            if (requireAuth) {
                val authRequired = isAuthenticationRequired(AuthAction.SEND_TRANSACTION)
                if (authRequired) {
                    throw SecurityException("Authentication required for private key access")
                }
            }

            // Get encrypted private key from storage WITH keyType
            val encryptedData = securityPreferencesRepository.getEncryptedPrivateKey(walletId, keyType)  // ADD keyType
            if (encryptedData == null) {
                Log.e("SecurityManager", "No private key found for wallet: $walletId")
                return Result.failure(IllegalStateException("Private key not found"))
            }

            val (encryptedHex, iv) = encryptedData

            // Decrypt using KeyStore
            Log.d("SecurityManager", "Decrypting private key for wallet: $walletId")
            val privateKey = keyStoreEncryption.decryptString(encryptedHex, iv.toHex())

            // Record authentication time for session management
            recordAuthentication()

            Log.d("SecurityManager", "Private key retrieved successfully")
            _securityState.value = SecurityState.IDLE
            Result.success(privateKey)

        } catch (e: SecurityException) {
            Log.e("SecurityManager", "Authentication required: ${e.message}")
            Result.failure(e)
        } catch (e: Exception) {
            Log.e("SecurityManager", "Failed to retrieve private key", e)
            _securityState.value = SecurityState.ERROR(e.message ?: "Private key decryption failed")
            Result.failure(e)
        }
    }

    /**
     * Check if private key exists for a wallet
     */
    suspend fun hasPrivateKey(
        walletId: String,
        keyType: String = "ETH_PRIVATE_KEY"  // ADD this parameter
    ): Boolean {
        return securityPreferencesRepository.getEncryptedPrivateKey(walletId, keyType) != null  // ADD keyType
    }

    /**
     * Retrieve and decrypt mnemonic phrase
     */
    suspend fun retrieveMnemonic(walletId: String): List<String>? {
        return try {
            _securityState.value = SecurityState.DECRYPTING

            // Get encrypted data
            val encryptedData = securityPreferencesRepository.getEncryptedMnemonic(walletId)
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

            securityPreferencesRepository.storeEncryptedPrivateKey(
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
            securityPreferencesRepository.storeEncryptedBackup(
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
            val backupData = securityPreferencesRepository.getEncryptedBackup(walletId)
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
            securityPreferencesRepository.storePinHash(pinHash)
            Log.d("SECURITY_PIN", " PIN hash stored")

            // Verify storage
            Log.d("SECURITY_PIN", "Verifying storage...")
            val storedHash = securityPreferencesRepository.getPinHash()
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
            val storedHash = securityPreferencesRepository.getPinHash()

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
        return securityPreferencesRepository.getPinHash() != null
    }

    /**
     * Enable/disable biometric authentication
     */
    suspend fun setBiometricEnabled(enabled: Boolean) {
        securityPreferencesRepository.setBiometricEnabled(enabled)
    }

    /**
     * Check if biometric is enabled
     */
    suspend fun isBiometricEnabled(): Boolean {
        return securityPreferencesRepository.isBiometricEnabled()
    }

    /**
     * Clear all security data (logout)
     */
    suspend fun clearAll() {
        securityPreferencesRepository.clearAll()
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

        val digest = MessageDigest.getInstance("SHA-256")
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

        val digest = MessageDigest.getInstance("SHA-256")
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
        val previousAuthTime = lastAuthenticationTime
        lastAuthenticationTime = System.currentTimeMillis()

        Log.d(" AUTH_DEBUG", "══════════════════════════════════════════════")
        Log.d(" AUTH_DEBUG", " AUTHENTICATION RECORDED")
        Log.d(" AUTH_DEBUG", "══════════════════════════════════════════════")
        Log.d(" AUTH_DEBUG", " Previous auth time: $previousAuthTime")
        Log.d(" AUTH_DEBUG", " New auth time: $lastAuthenticationTime")
        Log.d(" AUTH_DEBUG", " Session will expire in: ${sessionTimeout/1000} seconds")
        Log.d(" AUTH_DEBUG", "══════════════════════════════════════════════")
    }

    /**
     * Check if session is still valid
     */
    fun isSessionValid(): Boolean {
        Log.d(" SESSION_DEBUG", "══════════════════════════════════════════════")
        Log.d(" SESSION_DEBUG", " CHECKING SESSION VALIDITY")
        Log.d(" SESSION_DEBUG", "══════════════════════════════════════════════")

        Log.d(" SESSION_DEBUG", " Session Details:")
        Log.d(" SESSION_DEBUG", "    lastAuthenticationTime: $lastAuthenticationTime")
        Log.d(" SESSION_DEBUG", "    Is MIN_VALUE? ${lastAuthenticationTime == Long.MIN_VALUE}")
        Log.d(" SESSION_DEBUG", "    sessionTimeout: ${sessionTimeout}ms (${sessionTimeout/1000}s)")

        if (lastAuthenticationTime == Long.MIN_VALUE) {
            Log.d(" SESSION_DEBUG", " Session NOT valid: Never authenticated (MIN_VALUE)")
            Log.d(" SESSION_DEBUG", "══════════════════════════════════════════════")
            return false
        }

        val currentTime = System.currentTimeMillis()
        val timeSinceAuth = currentTime - lastAuthenticationTime

        Log.d(" SESSION_DEBUG", " Time Calculation:")
        Log.d(" SESSION_DEBUG", "    Current time: $currentTime")
        Log.d(" SESSION_DEBUG", "    Last auth time: $lastAuthenticationTime")
        Log.d(" SESSION_DEBUG", "    Time since auth: ${timeSinceAuth}ms (${timeSinceAuth/1000}s)")
        Log.d(" SESSION_DEBUG", "    Timeout: ${sessionTimeout}ms (${sessionTimeout/1000}s)")
        Log.d(" SESSION_DEBUG", "    Time remaining: ${max(0, sessionTimeout - timeSinceAuth)}ms")

        val isValid = timeSinceAuth < sessionTimeout

        if (isValid) {
            Log.d(" SESSION_DEBUG", " Session is VALID")
            Log.d(" SESSION_DEBUG", "    User authenticated ${timeSinceAuth/1000}s ago")
        } else {
            Log.d(" SESSION_DEBUG", " Session is EXPIRED")
            Log.d(" SESSION_DEBUG", "    Authentication expired ${(timeSinceAuth - sessionTimeout)/1000}s ago")
        }

        Log.d(" SESSION_DEBUG", "══════════════════════════════════════════════")
        return isValid
    }

    private fun formatTime(timestamp: Long): String {
        return if (timestamp == Long.MIN_VALUE) {
            "Never"
        } else {
            val sdf = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())
            sdf.format(Date(timestamp))
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
        securityPreferencesRepository.saveSessionTimeout(seconds)
        Log.d("SecurityManager", "Session timeout set to ${seconds}s")
    }

    /**
     * Get current session timeout
     */
    suspend fun getSessionTimeout(): Long {
        return securityPreferencesRepository.getSessionTimeout() * 1000L
    }

    /**
     * Check if authentication is required for an action
     */
    suspend fun isAuthenticationRequired(
        action: AuthAction = AuthAction.VIEW_WALLET
    ): Boolean {
        Log.d(" SECURITY_DEBUG", "══════════════════════════════════════════════")
        Log.d(" SECURITY_DEBUG", " CHECKING AUTHENTICATION")
        Log.d(" SECURITY_DEBUG", "══════════════════════════════════════════════")

        // 1. Check session
        val sessionValid = isSessionValid()
        Log.d(" SECURITY_DEBUG", " Session valid? $sessionValid")

        if (sessionValid) {
            Log.d(" SECURITY_DEBUG", " Session valid - NO auth required")
            return false
        }

        Log.d("SECURITY_DEBUG", " Session invalid - Checking PIN/Biometric...")

        // 2. Check if auth methods are set up
        val pinSet = isPinSet()
        val biometricEnabled = isBiometricEnabled()
        val hasAuthEnabled = pinSet || biometricEnabled

        Log.d(" SECURITY_DEBUG", " Auth Methods Status:")
        Log.d("SECURITY_DEBUG", "   • PIN set? $pinSet")
        Log.d(" SECURITY_DEBUG", "   • Biometric enabled? $biometricEnabled")
        Log.d(" SECURITY_DEBUG", "   • Any auth enabled? $hasAuthEnabled")

        if (!hasAuthEnabled) {
            Log.d(" SECURITY_DEBUG", "⚠ No auth methods enabled - allowing access")
            Log.d(" SECURITY_DEBUG", " User needs to set up PIN/biometric in Security Settings")
            return false
        }

        Log.d(" SECURITY_DEBUG", " AUTHENTICATION REQUIRED!")
        Log.d(" SECURITY_DEBUG", " Reason: AuthEnabled=true + SessionInvalid=true")
        return true
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
        securityPreferencesRepository.clearPinHash()
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

sealed class PrivateKeyResult {
    data class Success(val privateKey: String) : PrivateKeyResult()
    data class Error(val exception: Exception? = null) : PrivateKeyResult()
    object AuthenticationRequired : PrivateKeyResult()
}