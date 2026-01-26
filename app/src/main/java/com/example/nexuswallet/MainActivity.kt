package com.example.nexuswallet

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
//import com.example.nexuswallet.feature.wallet.domain.WalletDataManager
import com.example.nexuswallet.ui.theme.NexusWalletTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

//        WalletDataManager.initialize(applicationContext)

        setContent {
            NexusWalletTheme {
                Navigation()
            }
        }
    }
}