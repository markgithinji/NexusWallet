package com.example.nexuswallet.feature.wallet.ui.transactiondetail

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Error
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.OpenInBrowser
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.nexuswallet.R
import com.example.nexuswallet.feature.core.domain.model.CoinType
import com.example.nexuswallet.feature.wallet.domain.model.TransactionDetail
import com.example.nexuswallet.feature.wallet.domain.model.TransactionStatus
import com.example.nexuswallet.feature.wallet.ui.common.ErrorScreen
import com.example.nexuswallet.feature.wallet.ui.common.FullScreenLoading
import com.example.nexuswallet.ui.theme.bitcoinLight
import com.example.nexuswallet.ui.theme.ethereumLight
import com.example.nexuswallet.ui.theme.solanaLight
import com.example.nexuswallet.ui.theme.success
import com.example.nexuswallet.ui.theme.usdcLight
import com.example.nexuswallet.ui.theme.warning

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionDetailScreen(
    onNavigateUp: () -> Unit,
    walletId: String,
    transactionId: String,
    viewModel: TransactionDetailViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.loadTransactionDetail(walletId, transactionId)
    }

    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is TransactionDetailViewModel.TransactionDetailEffect.ShowError -> {
                    Toast.makeText(context, effect.message, Toast.LENGTH_SHORT).show()
                }

                is TransactionDetailViewModel.TransactionDetailEffect.CopyToClipboard -> {
                    val clipboard =
                        context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val clip = ClipData.newPlainText(effect.label, effect.text)
                    clipboard.setPrimaryClip(clip)
                    Toast.makeText(context, "${effect.label} copied", Toast.LENGTH_SHORT).show()
                }

                is TransactionDetailViewModel.TransactionDetailEffect.ShareTransaction -> {
                    shareTransaction(
                        context,
                        state.transaction,
                        state.formattedAmount,
                        state.formattedTime
                    )
                }

                is TransactionDetailViewModel.TransactionDetailEffect.OpenExplorer -> {
                    val intent = Intent(Intent.ACTION_VIEW, effect.url.toUri())
                    context.startActivity(intent)
                }
            }
        }
    }

    val coinType = state.transaction?.coinType

    val (coinColor, iconRes) = if (coinType != null) {
        getCoinDetailConfig(coinType)
    } else {
        Pair(MaterialTheme.colorScheme.primary, R.drawable.bitcoin)
    }

    Scaffold(
        topBar = {
            TransactionDetailTopBar(
                onNavigateUp = onNavigateUp,
                onShare = { viewModel.shareTransaction() },
                onRefresh = { viewModel.refresh() },
                isRefreshing = state.isRefreshing,
                iconRes = iconRes,
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        when {
            state.isLoading && state.transaction == null -> {
                FullScreenLoading(message = "Loading transaction...")
            }

            state.error != null && state.transaction == null -> {
                ErrorScreen(
                    message = state.error!!,
                    onRetry = { viewModel.loadTransactionDetail(walletId, transactionId) }
                )
            }

            state.transaction != null -> {
                TransactionDetailContent(
                    transaction = state.transaction!!,
                    formattedAmount = state.formattedAmount,
                    formattedFee = state.formattedFee,
                    formattedTime = state.formattedTime,
                    formattedUsd = state.formattedUsd,
                    coinColor = coinColor,
                    iconRes = iconRes,
                    onCopyAddress = { address, label ->
                        viewModel.copyToClipboard(address, label)
                    },
                    onCopyHash = { hash ->
                        viewModel.copyToClipboard(hash, "Transaction hash")
                    },
                    onViewOnExplorer = {
                        viewModel.openInExplorer()
                    },
                    modifier = Modifier.padding(padding)
                )
            }
        }
    }
}

private fun shareTransaction(
    context: Context,
    transaction: TransactionDetail?,
    formattedAmount: String,
    formattedTime: String
) {
    transaction?.let { tx ->
        val shareText = buildString {
            appendLine("Transaction Details")
            appendLine("Status: ${tx.status}")
            appendLine("Amount: $formattedAmount")
            appendLine("From: ${tx.fromAddress}")
            appendLine("To: ${tx.toAddress}")
            appendLine("Hash: ${tx.hash}")
            appendLine("Network: ${tx.network.displayName}")
            appendLine("Fee: ${tx.fee}")
            appendLine("Time: $formattedTime")
        }

        val shareIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, shareText)
            type = "text/plain"
        }
        context.startActivity(Intent.createChooser(shareIntent, "Share Transaction"))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TransactionDetailTopBar(
    onNavigateUp: () -> Unit,
    onShare: () -> Unit,
    onRefresh: () -> Unit,
    isRefreshing: Boolean,
    iconRes: Int,
) {
    TopAppBar(
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painter = painterResource(id = iconRes),
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = Color.Unspecified
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Transaction Details",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        },
        navigationIcon = {
            IconButton(onClick = onNavigateUp) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    "Back",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        actions = {
            IconButton(onClick = onShare) {
                Icon(
                    Icons.Outlined.Share,
                    "Share",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(
                onClick = onRefresh,
                enabled = !isRefreshing
            ) {
                if (isRefreshing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                } else {
                    Icon(
                        Icons.Outlined.Refresh,
                        "Refresh",
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
}

@Composable
fun TransactionDetailContent(
    transaction: TransactionDetail,
    formattedAmount: String,
    formattedFee: String,
    formattedTime: String,
    formattedUsd: String,
    coinColor: Color,
    iconRes: Int,
    onCopyAddress: (String, String) -> Unit,
    onCopyHash: (String) -> Unit,
    onViewOnExplorer: () -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Status Card
        item {
            TransactionStatusCard(
                transaction = transaction,
                formattedTime = formattedTime,
                coinColor = coinColor
            )
        }

        // Amount Card
        item {
            TransactionAmountCard(
                transaction = transaction,
                formattedAmount = formattedAmount,
                formattedUsd = formattedUsd,
                coinColor = coinColor,
                iconRes = iconRes,
                coinType = transaction.coinType
            )
        }

        // Transaction Hash Card
        item {
            TransactionHashCard(
                hash = transaction.hash,
                onCopy = { onCopyHash(transaction.hash) }
            )
        }

        // Addresses Card
        item {
            TransactionAddressesCard(
                transaction = transaction,
                onCopyAddress = onCopyAddress
            )
        }

        // Network & Fee Card
        item {
            NetworkFeeCard(
                transaction = transaction,
                formattedFee = formattedFee,
                coinColor = coinColor,
                coinType = transaction.coinType
            )
        }

        // Additional Details Card (if any)
        if (transaction.slot != null || transaction.gasPrice != null || transaction.feePerByte != null) {
            item {
                AdditionalDetailsCard(transaction = transaction)
            }
        }

        // Explorer Button
        item {
            Button(
                onClick = onViewOnExplorer,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = coinColor,
                    contentColor = Color.White
                )
            ) {
                Icon(
                    Icons.Outlined.OpenInBrowser,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "View on ${transaction.coinType.explorerName}",
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}

@Composable
fun TransactionStatusCard(
    transaction: TransactionDetail,
    formattedTime: String,
    coinColor: Color
) {
    val (statusColor, statusIcon, statusText) = when (transaction.status) {
        TransactionStatus.SUCCESS -> Triple(
            MaterialTheme.colorScheme.success,
            Icons.Outlined.CheckCircle,
            "Success"
        )

        TransactionStatus.PENDING -> Triple(
            MaterialTheme.colorScheme.warning,
            Icons.Outlined.HourglassEmpty,
            "Pending"
        )

        TransactionStatus.FAILED -> Triple(
            MaterialTheme.colorScheme.error,
            Icons.Outlined.Error,
            "Failed"
        )
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Status icon
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(statusColor.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = statusIcon,
                    contentDescription = null,
                    tint = statusColor,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Status and time
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = statusColor
                )
                Text(
                    text = formattedTime,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Network chip
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = coinColor.copy(alpha = 0.1f),
                contentColor = coinColor
            ) {
                Text(
                    text = when {
                        transaction.network.isTestnet -> "${transaction.network.displayName} Testnet"
                        else -> transaction.network.displayName
                    },
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                )
            }
        }
    }
}

@Composable
fun TransactionAmountCard(
    transaction: TransactionDetail,
    formattedAmount: String,
    formattedUsd: String,
    coinColor: Color,
    iconRes: Int,
    coinType: CoinType
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = if (transaction.isIncoming) "You received" else "You sent",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(coinColor.copy(alpha = 0.1f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(id = iconRes),
                        contentDescription = null,
                        tint = Color.Unspecified,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Text(
                        text = "$formattedAmount ${coinType.symbol}",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (transaction.isIncoming)
                            MaterialTheme.colorScheme.success
                        else
                            MaterialTheme.colorScheme.onSurface
                    )
                    // USD value
                    Text(
                        text = formattedUsd,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun TransactionHashCard(
    hash: String,
    onCopy: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.Link,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Hash info
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = "Transaction Hash",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = hash,
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Copy button
            IconButton(onClick = onCopy) {
                Icon(
                    Icons.Outlined.ContentCopy,
                    "Copy",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
fun TransactionAddressesCard(
    transaction: TransactionDetail,
    onCopyAddress: (String, String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = "Addresses",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(12.dp))

            // From Address
            AddressRow(
                label = "From",
                address = transaction.fromAddress,
                onCopy = { onCopyAddress(transaction.fromAddress, "From address") }
            )

            Spacer(modifier = Modifier.height(8.dp))

            HorizontalDivider(Modifier, thickness = 1.dp, color = MaterialTheme.colorScheme.outline)

            Spacer(modifier = Modifier.height(8.dp))

            // To Address
            AddressRow(
                label = "To",
                address = transaction.toAddress,
                onCopy = { onCopyAddress(transaction.toAddress, "To address") }
            )
        }
    }
}

@Composable
fun AddressRow(
    label: String,
    address: String,
    onCopy: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Label
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(40.dp)
        )

        Spacer(modifier = Modifier.width(8.dp))

        // Address
        Text(
            text = address,
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )

        // Copy button
        IconButton(onClick = onCopy) {
            Icon(
                Icons.Outlined.ContentCopy,
                "Copy",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
fun NetworkFeeCard(
    transaction: TransactionDetail,
    formattedFee: String,
    coinColor: Color,
    coinType: CoinType
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = "Network & Fee",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Network
            DetailRow(
                label = "Network",
                value = when {
                    transaction.network.isTestnet -> "${transaction.network.displayName} Testnet"
                    else -> transaction.network.displayName
                },
                valueColor = coinColor
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Fee
            DetailRow(
                label = "Fee",
                value = "$formattedFee ${transaction.tokenSymbol ?: coinType.symbol}",
                valueColor = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
fun AdditionalDetailsCard(
    transaction: TransactionDetail
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = "Additional Details",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Solana specific
            transaction.slot?.let {
                DetailRow(
                    label = "Slot",
                    value = it.toString()
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            // EVM specific
            transaction.gasPrice?.let {
                DetailRow(
                    label = "Gas Price",
                    value = "$it Gwei"
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            transaction.gasUsed?.let {
                DetailRow(
                    label = "Gas Used",
                    value = it.toString()
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            transaction.nonce?.let {
                DetailRow(
                    label = "Nonce",
                    value = it.toString()
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            transaction.chainId?.let {
                DetailRow(
                    label = "Chain ID",
                    value = it
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            // Bitcoin specific
            transaction.feePerByte?.let {
                DetailRow(
                    label = "Fee Rate",
                    value = "$it sat/byte"
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            transaction.estimatedSize?.let {
                DetailRow(
                    label = "Size",
                    value = "$it bytes"
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            // Token specific
            transaction.tokenSymbol?.let {
                DetailRow(
                    label = "Token",
                    value = transaction.tokenSymbol
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            transaction.tokenContract?.let {
                DetailRow(
                    label = "Contract",
                    value = it,
                    monospace = true
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            transaction.memo?.let {
                DetailRow(
                    label = "Memo",
                    value = it
                )
            }
        }
    }
}

@Composable
fun DetailRow(
    label: String,
    value: String,
    monospace: Boolean = false,
    valueColor: Color = MaterialTheme.colorScheme.onSurface
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            fontFamily = if (monospace) FontFamily.Monospace else null,
            color = valueColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false)
        )
    }
}

private fun getCoinDetailConfig(coinType: CoinType): Pair<Color, Int> {
    return when (coinType) {
        CoinType.BITCOIN -> Pair(bitcoinLight, R.drawable.bitcoin)
        CoinType.ETHEREUM -> Pair(ethereumLight, R.drawable.ethereum)
        CoinType.SOLANA -> Pair(solanaLight, R.drawable.solana)
        CoinType.USDC -> Pair(usdcLight, R.drawable.usdc)
    }
}