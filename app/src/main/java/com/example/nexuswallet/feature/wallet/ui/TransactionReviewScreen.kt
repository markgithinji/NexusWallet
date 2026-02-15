package com.example.nexuswallet.feature.wallet.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.CurrencyBitcoin
import androidx.compose.material.icons.filled.CurrencyExchange
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Note
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import com.example.nexuswallet.feature.coin.bitcoin.BitcoinSendViewModel
import com.example.nexuswallet.feature.coin.bitcoin.FeeLevel
import com.example.nexuswallet.feature.coin.ethereum.EthereumSendViewModel
import com.example.nexuswallet.feature.coin.ethereum.TransactionState
import com.example.nexuswallet.feature.coin.solana.SolanaSendViewModel
import com.example.nexuswallet.feature.coin.usdc.USDCSendViewModel
import com.example.nexuswallet.feature.wallet.data.model.BroadcastResult
import com.example.nexuswallet.feature.wallet.data.model.FeeEstimate
import com.example.nexuswallet.feature.wallet.data.model.SendTransaction
import com.example.nexuswallet.feature.wallet.data.model.SignedTransaction
import com.example.nexuswallet.feature.wallet.domain.ChainType
import com.example.nexuswallet.feature.wallet.domain.TransactionStatus
import com.example.nexuswallet.feature.wallet.domain.WalletType
import kotlinx.coroutines.delay
import java.math.BigDecimal
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionReviewScreen(
    navController: NavController,
    walletId: String,
    coinType: String, // "ETH", "BTC", "SOL", "USDC"
    toAddress: String,
    amount: String,
    feeLevel: String? = null,
    ethereumViewModel: EthereumSendViewModel = hiltViewModel(),
    usdcViewModel: USDCSendViewModel = hiltViewModel(),
    solanaViewModel: SolanaSendViewModel = hiltViewModel(),
    bitcoinViewModel: BitcoinSendViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    var isSending by remember { mutableStateOf(false) }
    var sendError by remember { mutableStateOf<String?>(null) }
    var txHash by remember { mutableStateOf<String?>(null) }
    var txStatus by remember { mutableStateOf("") }

    // Get wallet info for display
    val ethereumState = ethereumViewModel.uiState.collectAsState()
    val usdcState = usdcViewModel.state.collectAsState()
    val solanaState = solanaViewModel.state.collectAsState()
    val bitcoinState = bitcoinViewModel.state.collectAsState()

    // Initialize if needed
    LaunchedEffect(Unit) {
        when (coinType) {
            "ETH" -> {
                ethereumViewModel.initialize(walletId)
            }
            "USDC" -> {
                usdcViewModel.init(walletId)
            }
            "SOL" -> {
                solanaViewModel.init(walletId)
            }
            "BTC" -> {
                bitcoinViewModel.init(walletId)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = when (coinType) {
                                "BTC" -> Icons.Default.CurrencyBitcoin
                                "ETH" -> Icons.Default.CurrencyExchange
                                "SOL" -> Icons.Default.Star
                                "USDC" -> Icons.Default.AttachMoney
                                else -> Icons.Default.AccountBalanceWallet
                            },
                            contentDescription = null,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Review Transaction")
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                }
            )
        },
        bottomBar = {
            if (txHash != null) {
                // Transaction sent - show done button
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    tonalElevation = 8.dp
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Button(
                            onClick = {
                                navController.navigate("walletDetail/$walletId") {
                                    popUpTo("walletDetail/$walletId") { inclusive = true }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Done")
                        }
                    }
                }
            } else {
                // Send button
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    tonalElevation = 8.dp
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        sendError?.let { error ->
                            Text(
                                text = error,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                        }

                        Button(
                            onClick = {
                                isSending = true
                                sendError = null

                                when (coinType) {
                                    "ETH" -> {
                                        ethereumViewModel.onEvent(EthereumSendViewModel.SendEvent.ToAddressChanged(toAddress))
                                        ethereumViewModel.onEvent(EthereumSendViewModel.SendEvent.AmountChanged(amount))
                                        feeLevel?.let {
                                            ethereumViewModel.onEvent(EthereumSendViewModel.SendEvent.FeeLevelChanged(FeeLevel.valueOf(it)))
                                        }

                                        // Then call send
                                        ethereumViewModel.send { hash ->
                                            txHash = hash
                                            txStatus = "Transaction sent!"
                                            isSending = false
                                        }
                                    }
                                    "USDC" -> {
                                        usdcViewModel.updateAddress(toAddress)
                                        usdcViewModel.updateAmount(amount)
                                        usdcViewModel.send { hash ->
                                            txHash = hash
                                            txStatus = "Transaction sent!"
                                            isSending = false
                                        }
                                    }
                                    "SOL" -> {
                                        solanaViewModel.updateAddress(toAddress)
                                        solanaViewModel.updateAmount(amount)
                                        solanaViewModel.send { hash ->
                                            txHash = hash
                                            txStatus = "Transaction sent!"
                                            isSending = false
                                        }
                                    }
                                    "BTC" -> {
                                        bitcoinViewModel.updateAddress(toAddress)
                                        bitcoinViewModel.updateAmount(amount)
                                        feeLevel?.let {
                                            bitcoinViewModel.updateFeeLevel(FeeLevel.valueOf(it))
                                        }
                                        bitcoinViewModel.send { hash ->
                                            txHash = hash
                                            txStatus = "Transaction sent!"
                                            isSending = false
                                        }
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            enabled = !isSending
                        ) {
                            if (isSending) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(txStatus.ifEmpty { "Sending..." })
                            } else {
                                Text("Confirm & Send")
                            }
                        }
                    }
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            // Transaction Summary Card
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
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "You are sending",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "$amount ${getTokenSymbol(coinType)}",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // From Address (show wallet address from state)
            when (coinType) {
                "ETH" -> {
                    ethereumState.value.fromAddress.takeIf { it.isNotEmpty() }?.let { fromAddress ->
                        AddressCard(
                            label = "From",
                            address = fromAddress,
                            coinType = coinType
                        )
                    }
                }
                "USDC" -> {
                    usdcState.value.fromAddress.takeIf { it.isNotEmpty() }?.let { fromAddress ->
                        AddressCard(
                            label = "From",
                            address = fromAddress,
                            coinType = coinType
                        )
                    }
                }
                "SOL" -> {
                    solanaState.value.walletAddress.takeIf { it.isNotEmpty() }?.let { fromAddress ->
                        AddressCard(
                            label = "From",
                            address = fromAddress,
                            coinType = coinType
                        )
                    }
                }
                "BTC" -> {
                    bitcoinState.value.walletAddress.takeIf { it.isNotEmpty() }?.let { fromAddress ->
                        AddressCard(
                            label = "From",
                            address = fromAddress,
                            coinType = coinType
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // To Address Card
            AddressCard(
                label = "To",
                address = toAddress,
                coinType = coinType
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Fee Preview (if available)
            when (coinType) {
                "ETH" -> {
                    ethereumState.value.feeEstimate?.let { fee ->
                        FeePreviewCard(
                            fee = fee,
                            coinType = coinType
                        )
                    }
                }
                "BTC" -> {
                    bitcoinState.value.feeEstimate?.let { fee ->
                        FeePreviewCard(
                            fee = fee,
                            coinType = coinType
                        )
                    }
                }
                else -> {
                    // Show default fee for USDC and SOL
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Text(
                                text = "Network Fee",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "≈ 0.0005 ${getTokenSymbol(coinType)}",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Success Message
            txHash?.let { hash ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color.Green.copy(alpha = 0.1f)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "Success",
                                tint = Color.Green
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Transaction Sent!",
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.Green
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "Hash: ${hash.take(8)}...${hash.takeLast(8)}",
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace
                        )

                        Button(
                            onClick = {
                                val url = getExplorerUrl(coinType, hash)
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                context.startActivity(intent)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.OpenInBrowser, "View", modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("View on ${getExplorerName(coinType)}")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AddressCard(
    label: String,
    address: String,
    coinType: String
) {
    val context = LocalContext.current

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = address,
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.weight(1f)
                )

                IconButton(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText("Address", address)
                        clipboard.setPrimaryClip(clip)
                        Toast.makeText(context, "Address copied", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Icon(Icons.Default.ContentCopy, "Copy", modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

@Composable
fun FeePreviewCard(
    fee: FeeEstimate,
    coinType: String
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Network Fee",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = fee.priority.name.lowercase().replaceFirstChar { it.uppercase() },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Text(
                    text = "${fee.totalFeeDecimal} ${getTokenSymbol(coinType)}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
            }

            if (fee.estimatedTime != null) {
                Text(
                    text = "≈ ${fee.estimatedTime} seconds",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// Helper functions
private fun getTokenSymbol(coinType: String): String {
    return when (coinType) {
        "BTC" -> "BTC"
        "SOL" -> "SOL"
        "USDC" -> "USDC"
        "ETH" -> "ETH"
        else -> "TOKEN"
    }
}

private fun getExplorerName(coinType: String): String {
    return when (coinType) {
        "BTC" -> "Blockstream"
        "ETH" -> "Etherscan"
        "SOL" -> "Solscan"
        "USDC" -> "Etherscan"
        else -> "Explorer"
    }
}

private fun getExplorerUrl(coinType: String, txHash: String): String {
    return when (coinType) {
        "BTC" -> "https://blockstream.info/tx/$txHash"
        "ETH" -> "https://etherscan.io/tx/$txHash"
        "SOL" -> "https://solscan.io/tx/$txHash"
        "USDC" -> "https://etherscan.io/tx/$txHash"
        else -> "https://etherscan.io/tx/$txHash"
    }
}

// Helper functions
private fun getTokenSymbol(walletType: WalletType): String {
    return when (walletType) {
        WalletType.BITCOIN -> "BTC"
        WalletType.SOLANA -> "SOL"
        WalletType.USDC -> "USDC"
        WalletType.ETHEREUM, WalletType.ETHEREUM_SEPOLIA -> "ETH"
        else -> "TOKEN"
    }
}

private fun getExplorerName(walletType: WalletType): String {
    return when (walletType) {
        WalletType.BITCOIN -> "Blockstream"
        WalletType.ETHEREUM -> "Etherscan"
        WalletType.ETHEREUM_SEPOLIA -> "Etherscan"
        WalletType.SOLANA -> "Solscan"
        WalletType.USDC -> "Etherscan"
        else -> "Explorer"
    }
}

private fun getExplorerUrl(walletType: WalletType, txHash: String): String {
    return when (walletType) {
        WalletType.BITCOIN -> "https://blockstream.info/tx/$txHash"
        WalletType.ETHEREUM -> "https://etherscan.io/tx/$txHash"
        WalletType.ETHEREUM_SEPOLIA -> "https://sepolia.etherscan.io/tx/$txHash"
        WalletType.SOLANA -> "https://solscan.io/tx/$txHash"
        WalletType.USDC -> "https://etherscan.io/tx/$txHash"
        else -> "https://etherscan.io/tx/$txHash"
    }
}

@Composable
fun ErrorMessage(
    error: String,
    onDismiss: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Error,
                contentDescription = "Error",
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(20.dp)
            )

            Spacer(modifier = Modifier.width(8.dp))

            Text(
                text = error,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.weight(1f)
            )

            IconButton(
                onClick = onDismiss,
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Dismiss",
                    tint = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }
    }
}