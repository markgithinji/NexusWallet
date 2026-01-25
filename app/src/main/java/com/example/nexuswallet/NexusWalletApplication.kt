package com.example.nexuswallet


import android.app.Application
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class NexusWalletApplication : Application() {
    companion object {
        lateinit var instance: NexusWalletApplication
            private set
    }

    val walletDataManager by lazy { WalletDataManager(this) }
    val securityManager by lazy { SecurityManager(this) }
    val secureStorage by lazy { SecureStorage(this) }

    override fun onCreate() {
        super.onCreate()
        instance = this

        // Initialize security
        initializeSecurity()

        // Debug DataStore on app start
        debugStorageOnStart()
    }

    private fun initializeSecurity() {
        // Check if KeyStore is available
        val isKeyStoreAvailable = securityManager.isKeyStoreAvailable()
        Log.d("NexusWallet", "KeyStore available: $isKeyStoreAvailable")

        if (!isKeyStoreAvailable) {
            Log.w("NexusWallet", " Android KeyStore not available, using software encryption")
        }
    }

    private fun debugStorageOnStart() {
//        if (BuildConfig.DEBUG) {
            CoroutineScope(Dispatchers.IO).launch {
                Log.d("NexusWallet", " === APP START - DATASTORE DEBUG ===")

                // Test DataStore persistence
                val persistenceTest = secureStorage.testPersistence()
                Log.d("NexusWallet", " DataStore persistence test: $persistenceTest")

                // Check if PIN is saved
                val pinHash = secureStorage.getPinHash()
                Log.d("NexusWallet", " PIN saved on app start? ${pinHash != null}")
                if (pinHash != null) {
                    Log.d("NexusWallet", " PIN hash length: ${pinHash.length}")
                }

                // Check biometric status
                val biometricEnabled = secureStorage.isBiometricEnabled()
                Log.d("NexusWallet", " Biometric enabled on app start? $biometricEnabled")

                // Show all DataStore contents
                secureStorage.debugDataStoreContents()

                Log.d("NexusWallet", " === APP START DEBUG END ===")
//            }
        }
    }

    /**
     * Clear all secure data (for logout)
     */
    fun clearAllSecureData() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Clear from SecureStorage
                secureStorage.clearAll()

                // Clear session
                securityManager.clearSession()

                Log.d("NexusWallet", " All secure data cleared")
            } catch (e: Exception) {
                Log.e("NexusWallet", " Error clearing secure data", e)
            }
        }
    }
}