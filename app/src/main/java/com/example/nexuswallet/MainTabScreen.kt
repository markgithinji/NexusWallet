package com.example.nexuswallet

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.automirrored.outlined.ShowChart
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.nexuswallet.feature.coin.CoinType
import com.example.nexuswallet.feature.market.ui.MarketScreen
import com.example.nexuswallet.feature.settings.ui.SettingsScreen
import com.example.nexuswallet.feature.wallet.ui.WalletDashboardScreen

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.contentColorFor
import com.example.nexuswallet.feature.coin.NetworkType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainTabScreen(
    onNavigateToCreateWallet: () -> Unit,
    onNavigateToWalletDetail: (String) -> Unit,
    onNavigateToCoinDetail: (String, CoinType, NetworkType?) -> Unit,
    onNavigateToTokenDetail: (String) -> Unit,
    onNavigateToReceive: (String, CoinType, NetworkType?) -> Unit,
    onNavigateToSend: (String, CoinType, NetworkType?) -> Unit,
    padding: PaddingValues,
    navigationViewModel: NavigationViewModel
) {
    var selectedTab by remember { mutableIntStateOf(0) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = when (selectedTab) {
                                0 -> Icons.Outlined.AccountBalanceWallet
                                1 -> Icons.Outlined.ShowChart
                                2 -> Icons.Outlined.Settings
                                else -> Icons.Outlined.Wallet
                            },
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = when (selectedTab) {
                                0 -> "Wallets"
                                1 -> "Market"
                                2 -> "Settings"
                                else -> "Nexus Wallet"
                            },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                actions = {
                    if (selectedTab == 0) {
                        IconButton(
                            onClick = onNavigateToCreateWallet
                        ) {
                            Icon(
                                Icons.Outlined.Add,
                                contentDescription = "Create Wallet",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                shape = RoundedCornerShape(30.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(0.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Wallets Tab
                    NavigationBarItem(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        icon = {
                            Icon(
                                imageVector = if (selectedTab == 0)
                                    Icons.Filled.AccountBalanceWallet
                                else
                                    Icons.Outlined.AccountBalanceWallet,
                                contentDescription = "Wallets",
                                modifier = Modifier.size(24.dp)
                            )
                        },
                        label = {
                            Text(
                                "Wallets",
                                style = MaterialTheme.typography.labelSmall
                            )
                        },
                        alwaysShowLabel = true,
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            indicatorColor = Color.Transparent
                        )
                    )

                    // Market Tab
                    NavigationBarItem(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        icon = {
                            Icon(
                                imageVector = if (selectedTab == 1)
                                    Icons.AutoMirrored.Filled.ShowChart
                                else
                                    Icons.AutoMirrored.Outlined.ShowChart,
                                contentDescription = "Market",
                                modifier = Modifier.size(24.dp)
                            )
                        },
                        label = {
                            Text(
                                "Market",
                                style = MaterialTheme.typography.labelSmall
                            )
                        },
                        alwaysShowLabel = true,
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            indicatorColor = Color.Transparent
                        )
                    )

                    // Settings Tab
                    NavigationBarItem(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        icon = {
                            Icon(
                                imageVector = if (selectedTab == 2)
                                    Icons.Filled.Settings
                                else
                                    Icons.Outlined.Settings,
                                contentDescription = "Settings",
                                modifier = Modifier.size(24.dp)
                            )
                        },
                        label = {
                            Text(
                                "Settings",
                                style = MaterialTheme.typography.labelSmall
                            )
                        },
                        alwaysShowLabel = true,
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            indicatorColor = Color.Transparent
                        )
                    )
                }
            }
        },
        floatingActionButton = {
            if (selectedTab == 0) {
                FloatingActionButton(
                    onClick = onNavigateToCreateWallet,
                    shape = RoundedCornerShape(16.dp),
                    containerColor = MaterialTheme.colorScheme.primary,
                    elevation = FloatingActionButtonDefaults.elevation(0.dp)
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = "Create New Wallet",
                        tint = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { scaffoldPadding ->
        when (selectedTab) {
            0 -> WalletDashboardScreen(
                onNavigateToWalletDetail = onNavigateToWalletDetail,
                onNavigateToCoinDetail = onNavigateToCoinDetail,
                onNavigateToReceive = onNavigateToReceive,
                onNavigateToSend = onNavigateToSend,
                onNavigateToCreateWallet = onNavigateToCreateWallet,
                padding = PaddingValues(
                    top = scaffoldPadding.calculateTopPadding(),
                    bottom = scaffoldPadding.calculateBottomPadding()
                )
            )
            1 -> MarketScreen(
                onNavigateUp = { /* Handle market screen back navigation*/ },
                onNavigateToTokenDetail = onNavigateToTokenDetail,
                padding = PaddingValues(
                    top = scaffoldPadding.calculateTopPadding(),
                    bottom = scaffoldPadding.calculateBottomPadding()
                )
            )
            2 -> SettingsScreen(
                onNavigateUp = { /* Handle settings screen back navigation */ },
                onNavigateToSecurity = { /* Navigate to security settings */ }
            )
        }
    }
}