package com.example.nexuswallet

import android.app.Application
import com.example.nexuswallet.feature.authentication.domain.SecureStorage
import com.example.nexuswallet.feature.authentication.domain.SecurityManager
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class NexusWalletApplication : Application() {
}