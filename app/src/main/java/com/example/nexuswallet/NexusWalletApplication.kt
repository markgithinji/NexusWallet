package com.example.nexuswallet

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber

@HiltAndroidApp
class NexusWalletApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        } else {
            // TODO: Plant release tree (Crashlytics)
            // Timber.plant(CrashlyticsTree())
        }
    }
}