package com.example.nexuswallet.feature.settings.ui.security

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.nexuswallet.R
import com.example.nexuswallet.feature.ethereum.domain.model.EVMTokenType
import com.example.nexuswallet.feature.settings.domain.model.BackupBundle
import com.example.nexuswallet.feature.settings.domain.model.RestoreSelection
import com.example.nexuswallet.feature.wallet.domain.model.Wallet
import com.example.nexuswallet.feature.wallet.ui.common.Checkbox
import com.example.nexuswallet.feature.wallet.ui.common.CustomCheckboxDefaults
import com.example.nexuswallet.feature.wallet.util.TransactionFormatHelper
import com.example.nexuswallet.ui.theme.bitcoinLight
import com.example.nexuswallet.ui.theme.ethereumLight
import com.example.nexuswallet.ui.theme.solanaLight
import com.example.nexuswallet.ui.theme.usdcLight
import com.example.nexuswallet.ui.theme.usdtLight

@Composable
fun RestoreSelectionDialog(
    bundle: BackupBundle,
    selection: RestoreSelection,
    onWalletToggle: (String, Boolean) -> Unit,
    onNetworkToggle: (String, String, Boolean) -> Unit,
    onTokenToggle: (String, String, EVMTokenType, Boolean) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .fillMaxHeight(0.85f),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header
                Column(modifier = Modifier.padding(24.dp)) {
                    Text(
                        text = "Restore Backup",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Select wallets and assets to restore",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Selection List
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    items(bundle.wallets) { wallet ->
                        WalletRestoreCard(
                            wallet = wallet,
                            isSelected = selection.selectedWallets.contains(wallet.id),
                            selectedNetworks = selection.selectedNetworks[wallet.id] ?: emptySet(),
                            selectedTokens = selection.selectedTokens[wallet.id] ?: emptyMap(),
                            onWalletToggle = { checked: Boolean -> onWalletToggle(wallet.id, checked) },
                            onNetworkToggle = { net: String, checked: Boolean -> onNetworkToggle(wallet.id, net, checked) },
                            onTokenToggle = { net: String, type: EVMTokenType, checked: Boolean -> onTokenToggle(wallet.id, net, type, checked) }
                        )
                    }
                }

                // Footer Actions
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    tonalElevation = 2.dp,
                    color = MaterialTheme.colorScheme.surface
                ) {
                    Row(
                        modifier = Modifier
                            .padding(16.dp)
                            .fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = onDismiss,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Cancel")
                        }
                        Button(
                            onClick = onConfirm,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            enabled = selection.selectedWallets.isNotEmpty()
                        ) {
                            Text("Restore")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun WalletRestoreCard(
    wallet: Wallet,
    isSelected: Boolean,
    selectedNetworks: Set<String>,
    selectedTokens: Map<String, Set<EVMTokenType>>,
    onWalletToggle: (Boolean) -> Unit,
    onNetworkToggle: (String, Boolean) -> Unit,
    onTokenToggle: (String, EVMTokenType, Boolean) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f) 
                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
            border = BorderStroke(
                1.dp, 
                if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f) 
                else MaterialTheme.colorScheme.outlineVariant
            )
        ) {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = isSelected,
                        onCheckedChange = onWalletToggle
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = wallet.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Created ${TransactionFormatHelper.formatTimestamp(wallet.createdAt)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = { expanded = !expanded }) {
                        Icon(
                            imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = "Details"
                        )
                    }
                }

                if (expanded && isSelected) {
                    Column(modifier = Modifier.padding(start = 52.dp, end = 16.dp, bottom = 16.dp)) {
                        HorizontalDivider(
                            modifier = Modifier.padding(bottom = 12.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )
                        
                        // Bitcoin
                        wallet.bitcoinCoins.forEach { coin ->
                            AssetRestoreItem(
                                name = "Bitcoin",
                                symbol = "BTC",
                                network = coin.network.name,
                                iconRes = R.drawable.bitcoin,
                                color = bitcoinLight,
                                isSelected = selectedNetworks.contains(coin.network.name),
                                onToggle = { checked: Boolean -> onNetworkToggle(coin.network.name, checked) }
                            )
                        }

                        // Solana
                        wallet.solanaCoins.forEach { coin ->
                            AssetRestoreItem(
                                name = "Solana",
                                symbol = "SOL",
                                network = coin.network.name,
                                iconRes = R.drawable.solana,
                                color = solanaLight,
                                isSelected = selectedNetworks.contains(coin.network.name),
                                onToggle = { checked: Boolean -> onNetworkToggle(coin.network.name, checked) }
                            )
                        }

                        // Ethereum & Tokens
                        wallet.evmTokens.groupBy { it.network }.forEach { (network, tokens) ->
                            AssetRestoreItem(
                                name = network.name,
                                symbol = "ETH",
                                network = network.name,
                                iconRes = R.drawable.ethereum,
                                color = ethereumLight,
                                isSelected = selectedNetworks.contains(network.name),
                                onToggle = { checked: Boolean -> onNetworkToggle(network.name, checked) }
                            )

                            if (selectedNetworks.contains(network.name)) {
                                tokens.filter { it.evmTokenType != EVMTokenType.NATIVE }.forEach { token ->
                                    val tokenColor = when (token.evmTokenType) {
                                        EVMTokenType.USDC -> usdcLight
                                        EVMTokenType.USDT -> usdtLight
                                        else -> MaterialTheme.colorScheme.primary
                                    }
                                    val icon = when (token.evmTokenType) {
                                        EVMTokenType.USDC -> R.drawable.usdc
                                        EVMTokenType.USDT -> R.drawable.tether
                                        else -> R.drawable.ethereum
                                    }
                                    
                                    AssetRestoreItem(
                                        name = token.name,
                                        symbol = token.symbol,
                                        network = network.name,
                                        iconRes = icon,
                                        color = tokenColor,
                                        isSelected = selectedTokens[network.name]?.contains(token.evmTokenType) == true,
                                        isToken = true,
                                        onToggle = { checked: Boolean -> onTokenToggle(network.name, token.evmTokenType, checked) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AssetRestoreItem(
    name: String,
    symbol: String,
    network: String,
    iconRes: Int,
    color: Color,
    isSelected: Boolean,
    isToken: Boolean = false,
    onToggle: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isToken) Spacer(modifier = Modifier.width(16.dp))
        
        Box(
            modifier = Modifier
                .size(if (isToken) 32.dp else 40.dp)
                .clip(CircleShape)
                .background(color.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(id = iconRes),
                contentDescription = null,
                tint = Color.Unspecified,
                modifier = Modifier.size(if (isToken) 20.dp else 24.dp)
            )
        }
        
        Spacer(modifier = Modifier.width(12.dp))
        
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = name,
                style = if (isToken) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodyLarge,
                fontWeight = if (isToken) FontWeight.Medium else FontWeight.Bold
            )
            Text(
                text = if (isToken) symbol else "$symbol • $network",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        
        Checkbox(
            checked = isSelected,
            onCheckedChange = onToggle,
            colors = CustomCheckboxDefaults.colors(
                checkedBackgroundColor = color,
                checkedBorderColor = color
            )
        )
    }
}
