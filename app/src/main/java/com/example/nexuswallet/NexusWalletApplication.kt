package com.example.nexuswallet

import android.app.Application

class NexusWalletApplication : Application() {
    companion object {
        lateinit var instance: NexusWalletApplication
            private set
    }

    val walletDataManager by lazy { WalletDataManager(this) }

    override fun onCreate() {
        super.onCreate()
        instance = this
    }
}