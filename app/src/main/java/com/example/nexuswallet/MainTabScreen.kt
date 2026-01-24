package com.example.nexuswallet

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import coil.compose.AsyncImage
import com.example.nexuswallet.domain.Token
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainTabScreen(navController: NavController) {
    var selectedTab by remember { mutableIntStateOf(0) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        when (selectedTab) {
                            0 -> "Wallets"
                            1 -> "Market"
                            2 -> "Settings"
                            else -> "Nexus Wallet"
                        }
                    )
                },
                actions = {
                    if (selectedTab == 0) {
                        IconButton(
                            onClick = { navController.navigate("createWallet") }
                        ) {
                            Icon(Icons.Default.Add, "Create Wallet")
                        }
                    }
                }
            )
        },
        bottomBar = {
            TabRow(
                selectedTabIndex = selectedTab,
                modifier = Modifier.height(64.dp)
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Wallets") },
                    icon = { Icon(Icons.Default.AccountBalanceWallet, "Wallets") }
                )

                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Market") },
                    icon = { Icon(Icons.Default.TrendingUp, "Market") }
                )

                Tab(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    text = { Text("Settings") },
                    icon = { Icon(Icons.Default.Settings, "Settings") }
                )
            }
        },
        floatingActionButton = {
            if (selectedTab == 0) {
                FloatingActionButton(
                    onClick = { navController.navigate("createWallet") },
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(Icons.Default.Add, "Create New Wallet")
                }
            }
        }
    ) { padding ->
        when (selectedTab) {
            0 -> WalletDashboardScreen(
                navController = navController,
                padding = padding
            )
            1 -> MarketScreen(
                navController = navController,
                padding = padding
            )
            2 -> SettingsScreen(
                navController = navController,
            )
        }
    }
}