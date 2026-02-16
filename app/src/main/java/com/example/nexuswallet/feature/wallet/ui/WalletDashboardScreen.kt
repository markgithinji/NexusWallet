package com.example.nexuswallet.feature.wallet.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.CurrencyBitcoin
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Diamond
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import java.math.BigDecimal
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.nexuswallet.NavigationViewModel
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.Wallet

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WalletDashboardScreen(
    navController: NavController,
    navigationViewModel: NavigationViewModel,
    padding: PaddingValues
) {
    val wallets by navigationViewModel.wallets.collectAsState()
    val viewModel: WalletDashboardViewModel = hiltViewModel()
    val totalPortfolio by viewModel.totalPortfolioValue.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()

    if (isLoading) {
        LoadingScreen()
        return
    }

    error?.let {
        ErrorScreen(
            message = it,
            onRetry = { viewModel.refresh() }
        )
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
    ) {
        // Portfolio Summary
        PortfolioSummaryCard(
            totalPortfolio = totalPortfolio,
            walletCount = wallets.size
        )

        Button(
            onClick = { navController.navigate("sepoliaTest") }
        ) {
            Text("Test Sepolia Network")
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Wallets Section
        if (wallets.isEmpty()) {
            EmptyWalletsView(
                onCreateWallet = { navController.navigate("createWallet") }
            )
        } else {
            WalletsList(
                wallets = wallets,
                balances = viewModel.balances.collectAsState().value,
                onWalletClick = { wallet ->
                    // Use NavigationViewModel to check auth before navigating
                    navigationViewModel.navigateToWalletDetail(wallet.id)
                },
                onDeleteWallet = { walletId ->
                    viewModel.deleteWallet(walletId)
                }
            )
        }
    }
}

@Composable
fun PortfolioSummaryCard(
    totalPortfolio: BigDecimal,
    walletCount: Int
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            Text(
                text = "Total Portfolio",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "$${String.format("%.2f", totalPortfolio)}",
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "Wallets",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = walletCount.toString(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                Column(
                    horizontalAlignment = Alignment.End
                ) {
                    Text(
                        text = "Secure",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = "Secure",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Encrypted",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun EmptyWalletsView(onCreateWallet: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.AccountBalanceWallet,
            contentDescription = "No Wallets",
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "No Wallets Found",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Create your first secure cryptocurrency wallet to get started",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = onCreateWallet,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Create First Wallet")
        }
    }
}

@Composable
fun WalletsList(
    wallets: List<Wallet>,
    balances: Map<String, com.example.nexuswallet.feature.wallet.data.walletsrefactor.WalletBalance>,
    onWalletClick: (Wallet) -> Unit,
    onDeleteWallet: (String) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(wallets) { wallet ->
            WalletCard(
                wallet = wallet,
                balance = balances[wallet.id],
                onClick = { onWalletClick(wallet) },
                onDelete = { onDeleteWallet(wallet.id) }
            )
        }
    }
}

@Composable
fun WalletCard(
    wallet: Wallet,
    balance: com.example.nexuswallet.feature.wallet.data.walletsrefactor.WalletBalance?,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    var showDeleteDialog by remember { mutableStateOf(false) }

    if (showDeleteDialog) {
        DeleteWalletDialog(
            walletName = wallet.name,
            onConfirm = {
                onDelete()
                showDeleteDialog = false
            },
            onDismiss = { showDeleteDialog = false }
        )
    }

    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Wallet Icon
            WalletIcon(wallet = wallet)

            Spacer(modifier = Modifier.width(16.dp))

            // Wallet Info
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = wallet.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    // Show badges for enabled coins
                    wallet.bitcoin?.let {
                        Badge(text = "BTC", color = Color(0xFFF7931A))
                    }
                    wallet.ethereum?.let {
                        Badge(text = "ETH", color = Color(0xFF627EEA))
                    }
                    wallet.solana?.let {
                        Badge(text = "SOL", color = Color(0xFF00FFA3))
                    }
                    wallet.usdc?.let {
                        Badge(text = "USDC", color = Color(0xFF2775CA))
                    }
                }

                // Show primary address
                val primaryAddress = wallet.ethereum?.address
                    ?: wallet.bitcoin?.address
                    ?: wallet.solana?.address
                    ?: wallet.usdc?.address

                primaryAddress?.let {
                    Text(
                        text = it.take(8) + "..." + it.takeLast(6),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontFamily = FontFamily.Monospace
                    )
                }

                // Show balances
                balance?.let {
                    Spacer(modifier = Modifier.height(4.dp))

                    // Show each coin's balance
                    it.bitcoin?.let { btc ->
                        BalanceRow(
                            symbol = "BTC",
                            amount = btc.btc,
                            usdValue = btc.usdValue
                        )
                    }
                    it.ethereum?.let { eth ->
                        BalanceRow(
                            symbol = "ETH",
                            amount = eth.eth,
                            usdValue = eth.usdValue
                        )
                    }
                    it.solana?.let { sol ->
                        BalanceRow(
                            symbol = "SOL",
                            amount = sol.sol,
                            usdValue = sol.usdValue
                        )
                    }
                    it.usdc?.let { usdc ->
                        BalanceRow(
                            symbol = "USDC",
                            amount = usdc.amountDecimal,
                            usdValue = usdc.usdValue
                        )
                    }
                }
            }

            // Actions
            IconButton(
                onClick = { showDeleteDialog = true }
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete Wallet",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
fun Badge(text: String, color: Color) {
    Box(
        modifier = Modifier
            .background(color.copy(alpha = 0.1f), RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun BalanceRow(symbol: String, amount: String, usdValue: Double) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "$amount $symbol",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium
        )
        Text(
            text = "$${String.format("%.2f", usdValue)}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
fun WalletIcon(wallet: Wallet) {
    val (icon, color) = when {
        wallet.bitcoin != null -> Pair(Icons.Default.CurrencyBitcoin, Color(0xFFF7931A))
        wallet.ethereum != null -> Pair(Icons.Default.Diamond, Color(0xFF627EEA))
        wallet.solana != null -> Pair(Icons.Default.FlashOn, Color(0xFF00FFA3))
        wallet.usdc != null -> Pair(Icons.Default.AttachMoney, Color(0xFF2775CA))
        else -> Pair(Icons.Default.AccountBalanceWallet, MaterialTheme.colorScheme.primary)
    }

    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(color.copy(alpha = 0.1f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = "Wallet Type",
            tint = color,
            modifier = Modifier.size(24.dp)
        )
    }
}

@Composable
fun DeleteWalletDialog(
    walletName: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete Wallet") },
        text = {
            Text("Are you sure you want to delete \"$walletName\"? This action cannot be undone.")
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm
            ) {
                Text("Delete", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun LoadingScreen() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator()
    }
}

@Composable
fun ErrorScreen(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Error,
            contentDescription = "Error",
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.error
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Error",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.error
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = onRetry,
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("Try Again")
        }
    }
}