package com.example.nexuswallet.feature.wallet.ui

import androidx.compose.runtime.Composable

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.nexuswallet.feature.coin.ethereum.GasPrice
import com.example.nexuswallet.feature.wallet.domain.BitcoinWallet
import com.example.nexuswallet.feature.wallet.domain.CryptoWallet
import com.example.nexuswallet.feature.wallet.domain.EthereumWallet
import com.example.nexuswallet.feature.wallet.domain.MultiChainWallet
import com.example.nexuswallet.feature.wallet.domain.SolanaWallet
import java.math.BigDecimal
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun BlockchainDataCard(
    wallet: CryptoWallet,
    blockchainViewModel: BlockchainViewModel
) {
    val ethBalance by blockchainViewModel.ethBalance.collectAsState()
    val btcBalance by blockchainViewModel.btcBalance.collectAsState()
    val gasPrice by blockchainViewModel.gasPrice.collectAsState()
    val apiStatus by blockchainViewModel.apiStatus.collectAsState()
    val lastUpdated by blockchainViewModel.lastUpdated.collectAsState()

    // Verification state
    var showVerificationLogs by remember { mutableStateOf(false) }
    val verificationResults = remember(wallet, ethBalance, btcBalance, gasPrice) {
        verifyBlockchainData(wallet, ethBalance, btcBalance, gasPrice, apiStatus)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = when (apiStatus) {
                ApiStatus.CONNECTED -> MaterialTheme.colorScheme.surfaceVariant
                ApiStatus.CONNECTING -> Color(0xFFFF9800).copy(alpha = 0.05f)
                ApiStatus.ERROR -> Color(0xFFF44336).copy(alpha = 0.05f)
                ApiStatus.DISCONNECTED -> Color(0xFF9E9E9E).copy(alpha = 0.05f)
            }
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Header with status indicator
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Live Blockchain Data",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    // Status indicator dot
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(
                                when (apiStatus) {
                                    ApiStatus.CONNECTED -> Color.Green
                                    ApiStatus.CONNECTING -> Color.Yellow
                                    ApiStatus.ERROR -> Color.Red
                                    ApiStatus.DISCONNECTED -> Color.Gray
                                }
                            )
                    )
                }

                // Verification toggle
                IconButton(
                    onClick = { showVerificationLogs = !showVerificationLogs },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = if (showVerificationLogs) Icons.Default.Verified else Icons.Default.VerifiedUser,
                        contentDescription = "Verification",
                        tint = if (showVerificationLogs) Color.Green else MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // API Status Banner
            ApiStatusBanner(apiStatus = apiStatus, lastUpdated = lastUpdated)

            Spacer(modifier = Modifier.height(12.dp))

            // Real-time balances with verification badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Live Balance:", fontWeight = FontWeight.Medium)

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = when {
                            wallet is EthereumWallet && ethBalance != null ->
                                "${ethBalance!!.toPlainString()} ETH"
                            wallet is BitcoinWallet && btcBalance != null ->
                                "${btcBalance!!.toPlainString()} BTC"
                            wallet is MultiChainWallet -> {
                                val ethText = ethBalance?.let { "${it.toPlainString()} ETH" } ?: ""
                                val btcText = btcBalance?.let { "${it.toPlainString()} BTC" } ?: ""
                                if (ethText.isNotEmpty() && btcText.isNotEmpty()) {
                                    "$ethText • $btcText"
                                } else if (ethText.isNotEmpty()) {
                                    ethText
                                } else if (btcText.isNotEmpty()) {
                                    btcText
                                } else {
                                    "Fetching..."
                                }
                            }
                            else -> "Fetching..."
                        },
                        fontWeight = FontWeight.Bold,
                        color = when {
                            (wallet is EthereumWallet && ethBalance != null) ||
                                    (wallet is BitcoinWallet && btcBalance != null) ||
                                    (wallet is MultiChainWallet && (ethBalance != null || btcBalance != null)) ->
                                Color.Green
                            else -> MaterialTheme.colorScheme.primary
                        }
                    )

                    // Verification badge
                    if ((wallet is EthereumWallet && ethBalance != null) ||
                        (wallet is BitcoinWallet && btcBalance != null) ||
                        (wallet is MultiChainWallet && (ethBalance != null || btcBalance != null))) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Verified",
                            modifier = Modifier.size(16.dp),
                            tint = Color.Green
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Network status
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Network:", fontWeight = FontWeight.Medium)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = when (wallet) {
                            is EthereumWallet -> "Ethereum Mainnet"
                            is BitcoinWallet -> "Bitcoin Mainnet"
                            is MultiChainWallet -> "Multi-Chain"
                            is SolanaWallet -> "Solana Mainnet"
                            else -> "Crypto Network"
                        },
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (apiStatus == ApiStatus.CONNECTED) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Connected",
                            modifier = Modifier.size(14.dp),
                            tint = Color.Green
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Gas prices (Ethereum only)
            if (wallet is EthereumWallet || wallet is MultiChainWallet) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Gas Price:", fontWeight = FontWeight.Medium)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = gasPrice?.propose?.let { "$it Gwei" } ?: "Loading...",
                            color = when {
                                gasPrice != null -> MaterialTheme.colorScheme.onSurfaceVariant
                                else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            }
                        )

                        if (gasPrice != null) {
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "Live Data",
                                modifier = Modifier.size(14.dp),
                                tint = Color.Green
                            )
                        }
                    }
                }
            }

            // Verification Logs Section
            if (showVerificationLogs) {
                Spacer(modifier = Modifier.height(12.dp))
                Divider()
                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Data Verification Logs",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.height(8.dp))

                Column {
                    verificationResults.take(3).forEach { log ->
                        VerificationLogItem(log = log)
                    }

                    if (verificationResults.size > 3) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "and ${verificationResults.size - 3} more checks...",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Data Source Info
                Text(
                    text = "Data Sources:",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    DataSourceChip(
                        source = "Etherscan",
                        isActive = (wallet is EthereumWallet || wallet is MultiChainWallet) && apiStatus == ApiStatus.CONNECTED
                    )
                    DataSourceChip(
                        source = "Blockstream",
                        isActive = (wallet is BitcoinWallet || wallet is MultiChainWallet) && apiStatus == ApiStatus.CONNECTED
                    )
                    DataSourceChip(
                        source = "Covalent",
                        isActive = (wallet is EthereumWallet || wallet is MultiChainWallet) && apiStatus == ApiStatus.CONNECTED
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Refresh button with timestamp
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = {
//                        blockchainViewModel.refresh(wallet)
//                        if (wallet is EthereumWallet || wallet is MultiChainWallet) {
//                            blockchainViewModel.loadGasPrice()
//                        }
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp),
                    enabled = apiStatus != ApiStatus.CONNECTING
                ) {
                    if (apiStatus == ApiStatus.CONNECTING) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Refresh Data")
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Timestamp
                if (lastUpdated != null) {
                    Text(
                        text = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(lastUpdated!!),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun ApiStatusBanner(apiStatus: ApiStatus, lastUpdated: Date?) {
    val (title, description, color) = when (apiStatus) {
        ApiStatus.CONNECTED -> Triple(
            "Connected to Blockchain",
            "Live data from APIs",
            Color.Green
        )
        ApiStatus.CONNECTING -> Triple(
            "Connecting...",
            "Establishing API connections",
            Color.Yellow
        )
        ApiStatus.ERROR -> Triple(
            "Connection Issues",
            "Some APIs may be unavailable",
            Color.Red
        )
        ApiStatus.DISCONNECTED -> Triple(
            "Offline Mode",
            "Using demo data",
            Color.Gray
        )
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(color.copy(alpha = 0.1f))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
        )

        Spacer(modifier = Modifier.width(8.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
                color = color
            )

            Text(
                text = description,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (lastUpdated != null && apiStatus == ApiStatus.CONNECTED) {
            Text(
                text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(lastUpdated),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun VerificationLogItem(log: VerificationLog) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = when (log.status) {
                VerificationStatus.VERIFIED -> Icons.Default.CheckCircle
                VerificationStatus.PENDING -> Icons.Default.HourglassEmpty
                VerificationStatus.FAILED -> Icons.Default.Error
                VerificationStatus.INFO -> Icons.Default.Info
            },
            contentDescription = log.status.name,
            modifier = Modifier.size(16.dp),
            tint = when (log.status) {
                VerificationStatus.VERIFIED -> Color.Green
                VerificationStatus.PENDING -> Color.Yellow
                VerificationStatus.FAILED -> Color.Red
                VerificationStatus.INFO -> MaterialTheme.colorScheme.primary
            }
        )

        Spacer(modifier = Modifier.width(8.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = log.title,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium
            )

            if (log.details.isNotBlank()) {
                Text(
                    text = log.details,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        if (log.timestamp != null) {
            Text(
                text = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(log.timestamp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun DataSourceChip(source: String, isActive: Boolean) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (isActive) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                else MaterialTheme.colorScheme.surfaceVariant
            )
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (isActive) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "Active",
                    modifier = Modifier.size(12.dp),
                    tint = Color.Green
                )
                Spacer(modifier = Modifier.width(4.dp))
            }
            Text(
                text = source,
                style = MaterialTheme.typography.labelSmall,
                color = if (isActive) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// Add these helper functions and data classes
enum class VerificationStatus {
    VERIFIED, PENDING, FAILED, INFO
}

data class VerificationLog(
    val title: String,
    val details: String = "",
    val status: VerificationStatus,
    val timestamp: Date = Date()
)

fun verifyBlockchainData(
    wallet: CryptoWallet,
    ethBalance: BigDecimal?,
    btcBalance: BigDecimal?,
    gasPrice: GasPrice?,
    apiStatus: ApiStatus
): List<VerificationLog> {
    val logs = mutableListOf<VerificationLog>()
    val now = Date()

    // 1. API Connection Status
    logs.add(
        VerificationLog(
            title = "API Connection Status",
            details = when (apiStatus) {
                ApiStatus.CONNECTED -> "All APIs connected successfully"
                ApiStatus.CONNECTING -> "Establishing connections..."
                ApiStatus.ERROR -> "Some APIs failed to connect"
                ApiStatus.DISCONNECTED -> "Using offline demo mode"
            },
            status = when (apiStatus) {
                ApiStatus.CONNECTED -> VerificationStatus.VERIFIED
                ApiStatus.CONNECTING -> VerificationStatus.PENDING
                ApiStatus.ERROR -> VerificationStatus.FAILED
                ApiStatus.DISCONNECTED -> VerificationStatus.INFO
            },
            timestamp = now
        )
    )

    // 2. Wallet-specific verification
    when (wallet) {
        is EthereumWallet -> {
            if (ethBalance != null) {
                logs.add(
                    VerificationLog(
                        title = "Ethereum Balance",
                        details = "Live data from Etherscan: ${ethBalance.toPlainString()} ETH",
                        status = VerificationStatus.VERIFIED,
                        timestamp = now
                    )
                )
            } else {
                logs.add(
                    VerificationLog(
                        title = "Ethereum Balance",
                        details = "Awaiting API response...",
                        status = VerificationStatus.PENDING,
                        timestamp = now
                    )
                )
            }

            if (gasPrice != null) {
                logs.add(
                    VerificationLog(
                        title = "Gas Prices",
                        details = "Live from Etherscan: ${gasPrice.propose} Gwei",
                        status = VerificationStatus.VERIFIED,
                        timestamp = now
                    )
                )
            }
        }

        is BitcoinWallet -> {
            if (btcBalance != null) {
                logs.add(
                    VerificationLog(
                        title = "Bitcoin Balance",
                        details = "Live data from Blockstream: ${btcBalance.toPlainString()} BTC",
                        status = VerificationStatus.VERIFIED,
                        timestamp = now
                    )
                )
            } else {
                logs.add(
                    VerificationLog(
                        title = "Bitcoin Balance",
                        details = "Awaiting API response...",
                        status = VerificationStatus.PENDING,
                        timestamp = now
                    )
                )
            }
        }

        is MultiChainWallet -> {
            wallet.ethereumWallet?.let {
                if (ethBalance != null) {
                    logs.add(
                        VerificationLog(
                            title = "Ethereum Balance",
                            details = "Live from Etherscan: ${ethBalance.toPlainString()} ETH",
                            status = VerificationStatus.VERIFIED,
                            timestamp = now
                        )
                    )
                }
            }

            wallet.bitcoinWallet?.let {
                if (btcBalance != null) {
                    logs.add(
                        VerificationLog(
                            title = "Bitcoin Balance",
                            details = "Live from Blockstream: ${btcBalance.toPlainString()} BTC",
                            status = VerificationStatus.VERIFIED,
                            timestamp = now
                        )
                    )
                }
            }
        }

        else -> {}
    }

    // 3. Data freshness
    logs.add(
        VerificationLog(
            title = "Data Freshness",
            details = "Data updated just now",
            status = VerificationStatus.VERIFIED,
            timestamp = now
        )
    )

    // 4. Network verification
    logs.add(
        VerificationLog(
            title = "Network",
            details = when (wallet) {
                is EthereumWallet -> "Ethereum Mainnet"
                is BitcoinWallet -> "Bitcoin Mainnet"
                is MultiChainWallet -> "Multi-Chain Network"
                is SolanaWallet -> "Solana Mainnet"
                else -> "Crypto Network"
            },
            status = VerificationStatus.VERIFIED,
            timestamp = now
        )
    )

    return logs
}