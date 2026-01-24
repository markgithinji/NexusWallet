package com.example.nexuswallet

import android.app.Application
import android.util.Log


class NexusWalletApplication : Application() {
    companion object {
        lateinit var instance: NexusWalletApplication
            private set
    }

    val walletDataManager by lazy { WalletDataManager(this) }
    val securityManager by lazy { SecurityManager(this) }

    override fun onCreate() {
        super.onCreate()
        instance = this

        initializeSecurity()
    }

    private fun initializeSecurity() {
        // Check if KeyStore is available
        val isKeyStoreAvailable = securityManager.isKeyStoreAvailable()
        Log.d("NexusWallet", "KeyStore available: $isKeyStoreAvailable")

        if (!isKeyStoreAvailable) {
            // Show warning or use software-based fallback
            Log.w("NexusWallet", "Android KeyStore not available, using software encryption")
        }
    }
}