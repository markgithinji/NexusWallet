package com.example.nexuswallet.feature.navigation

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import com.example.nexuswallet.MainViewModel
import com.example.nexuswallet.feature.navigation.navtype.AuthTargetNavType
import com.example.nexuswallet.feature.navigation.navtype.CoinNavType
import com.example.nexuswallet.feature.navigation.navtype.NetworkNavType
import com.example.nexuswallet.feature.wallet.domain.model.Coin
import com.example.nexuswallet.feature.wallet.domain.model.Network
import com.example.nexuswallet.feature.wallet.ui.common.FullScreenLoading
import kotlin.reflect.typeOf

@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun Navigation(
    canAuthenticate: Boolean
) {
    val navController = rememberNavController()
    val mainViewModel: MainViewModel = hiltViewModel()

    val wallets by mainViewModel.wallets.collectAsStateWithLifecycle()
    val isWalletsLoading by mainViewModel.isWalletsLoading.collectAsStateWithLifecycle()

    if (isWalletsLoading) {
        FullScreenLoading(message = "Loading wallets...")
        return
    }

    // Determine start destination based on wallets.
    // We compute this ONLY ONCE when loading completes to prevent navigation
    // from jumping while a restore operation is adding wallets to an empty database.
    val startDestination = remember {
        if (wallets.isNotEmpty()) MainRoute else WelcomeRoute
    }

    val typeMap = remember {
        mapOf(
            typeOf<Coin>() to CoinNavType,
            typeOf<AuthTarget>() to AuthTargetNavType,
            typeOf<Network>() to NetworkNavType
        )
    }

    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        onboardingGraph(navController)
        
        mainGraph(
            navController = navController,
            isAuthenticationRequired = { mainViewModel.isAuthenticationRequired.value }
        )
        
        walletGraph(
            navController = navController,
            typeMap = typeMap,
            isRequireAuthForSendEnabled = { mainViewModel.isRequireAuthForSendEnabled.value }
        )
        
        settingsGraph(navController)
        
        authGraph(
            navController = navController,
            typeMap = typeMap,
            canAuthenticate = canAuthenticate
        )
    }
}
