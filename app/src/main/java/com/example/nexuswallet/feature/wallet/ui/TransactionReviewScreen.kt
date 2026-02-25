package com.example.nexuswallet.feature.wallet.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.CurrencyBitcoin
import androidx.compose.material.icons.filled.CurrencyExchange
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.AttachMoney
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.CurrencyBitcoin
import androidx.compose.material.icons.outlined.Diamond
import androidx.compose.material.icons.outlined.Error
import androidx.compose.material.icons.outlined.FlashOn
import androidx.compose.material.icons.outlined.OpenInBrowser
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import com.example.nexuswallet.feature.coin.CoinType
import com.example.nexuswallet.feature.coin.bitcoin.BitcoinFeeEstimate
import com.example.nexuswallet.feature.coin.bitcoin.BitcoinSendViewModel
import com.example.nexuswallet.feature.coin.bitcoin.FeeLevel
import com.example.nexuswallet.feature.coin.ethereum.EthereumFeeEstimate
import com.example.nexuswallet.feature.coin.ethereum.EthereumSendViewModel
import com.example.nexuswallet.feature.coin.ethereum.EthereumSendEvent
import com.example.nexuswallet.feature.coin.solana.SolanaFeeEstimate
import com.example.nexuswallet.feature.coin.solana.SolanaSendEvent
import com.example.nexuswallet.feature.coin.solana.SolanaSendViewModel
import com.example.nexuswallet.feature.coin.usdc.USDCSendEvent
import com.example.nexuswallet.feature.coin.usdc.USDCSendViewModel
import com.example.nexuswallet.feature.coin.usdc.domain.USDCFeeEstimate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionReviewScreen(
    navController: NavController,
    walletId: String,
    coinType: CoinType,
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

    // Get coin config
    val (coinColor, icon, displayName) = getCoinConfig(coinType)

    // Initialize if needed
    LaunchedEffect(Unit) {
        when (coinType) {
            CoinType.ETHEREUM -> {
                ethereumViewModel.initialize(walletId)
            }
            CoinType.USDC -> {
                usdcViewModel.init(walletId)
            }
            CoinType.SOLANA -> {
                solanaViewModel.init(walletId)
            }
            CoinType.BITCOIN -> {
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
                            imageVector = icon,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                            tint = coinColor
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Review Transaction",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.Black
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.Default.ArrowBack, "Back", tint = Color.Black)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.White,
                    scrolledContainerColor = Color.White
                )
            )
        },
        bottomBar = {
            if (txHash != null) {
                // Transaction sent - show done button
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = Color.White,
                    shadowElevation = 8.dp
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
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF3B82F6)
                            )
                        ) {
                            Text("Done", color = Color.White)
                        }
                    }
                }
            } else {
                // Send button
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = Color.White,
                    shadowElevation = 8.dp
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        sendError?.let { error ->
                            Text(
                                text = error,
                                color = Color(0xFFEF4444),
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                        }

                        Button(
                            onClick = {
                                isSending = true
                                sendError = null

                                when (coinType) {
                                    CoinType.ETHEREUM -> {
                                        ethereumViewModel.onEvent(EthereumSendEvent.ToAddressChanged(toAddress))
                                        ethereumViewModel.onEvent(EthereumSendEvent.AmountChanged(amount))
                                        feeLevel?.let {
                                            ethereumViewModel.onEvent(EthereumSendEvent.FeeLevelChanged(FeeLevel.valueOf(it)))
                                        }
                                        ethereumViewModel.send { hash ->
                                            txHash = hash
                                            txStatus = "Transaction sent!"
                                            isSending = false
                                        }
                                    }
                                    CoinType.USDC -> {
                                        usdcViewModel.onEvent(USDCSendEvent.ToAddressChanged(toAddress))
                                        usdcViewModel.onEvent(USDCSendEvent.AmountChanged(amount))
                                        feeLevel?.let {
                                            usdcViewModel.onEvent(USDCSendEvent.FeeLevelChanged(FeeLevel.valueOf(it)))
                                        }
                                        usdcViewModel.send { hash ->
                                            txHash = hash
                                            txStatus = "Transaction sent!"
                                            isSending = false
                                        }
                                    }
                                    CoinType.SOLANA -> {
                                        solanaViewModel.onEvent(SolanaSendEvent.ToAddressChanged(toAddress))
                                        solanaViewModel.onEvent(SolanaSendEvent.AmountChanged(amount))
                                        feeLevel?.let {
                                            solanaViewModel.onEvent(SolanaSendEvent.FeeLevelChanged(FeeLevel.valueOf(it)))
                                        }
                                        solanaViewModel.send { hash ->
                                            txHash = hash
                                            txStatus = "Transaction sent!"
                                            isSending = false
                                        }
                                    }
                                    CoinType.BITCOIN -> {
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
                            enabled = !isSending,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF3B82F6),
                                disabledContainerColor = Color(0xFF3B82F6).copy(alpha = 0.5f)
                            )
                        ) {
                            if (isSending) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    color = Color.White,
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(txStatus.ifEmpty { "Sending..." }, color = Color.White)
                            } else {
                                Text("Confirm & Send", color = Color.White)
                            }
                        }
                    }
                }
            }
        },
        containerColor = Color(0xFFF5F5F7)
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
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color.White
                ),
                elevation = CardDefaults.cardElevation(0.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "You are sending",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color(0xFF6B7280)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "$amount ${getTokenSymbol(coinType)}",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // From Address (show wallet address from state)
            when (coinType) {
                CoinType.ETHEREUM -> {
                    ethereumState.value.fromAddress.takeIf { it.isNotEmpty() }?.let { fromAddress ->
                        AddressCard(
                            label = "From",
                            address = fromAddress,
                            coinType = coinType
                        )
                    }
                }
                CoinType.USDC -> {
                    usdcState.value.fromAddress.takeIf { it.isNotEmpty() }?.let { fromAddress ->
                        AddressCard(
                            label = "From",
                            address = fromAddress,
                            coinType = coinType
                        )
                    }
                }
                CoinType.SOLANA -> {
                    solanaState.value.walletAddress.takeIf { it.isNotEmpty() }?.let { fromAddress ->
                        AddressCard(
                            label = "From",
                            address = fromAddress,
                            coinType = coinType
                        )
                    }
                }
                CoinType.BITCOIN -> {
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

            // Fee Preview (now using coin-specific fee displays)
            when (coinType) {
                CoinType.ETHEREUM -> {
                    ethereumState.value.feeEstimate?.let { fee ->
                        EthereumFeePreviewCard(feeEstimate = fee)
                    }
                }
                CoinType.BITCOIN -> {
                    bitcoinState.value.feeEstimate?.let { fee ->
                        BitcoinFeePreviewCard(feeEstimate = fee)
                    }
                }
                CoinType.USDC -> {
                    usdcState.value.feeEstimate?.let { fee ->
                        USDCFeePreviewCard(feeEstimate = fee)
                    }
                }
                CoinType.SOLANA -> {
                    solanaState.value.feeEstimate?.let { fee ->
                        SolanaFeePreviewCard(feeEstimate = fee)
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
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFF10B981).copy(alpha = 0.1f)
                    ),
                    elevation = CardDefaults.cardElevation(0.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.CheckCircle,
                                contentDescription = "Success",
                                tint = Color(0xFF10B981)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Transaction Sent!",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF10B981)
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "Hash: ${hash.take(8)}...${hash.takeLast(8)}",
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            color = Color(0xFF6B7280)
                        )

                        Button(
                            onClick = {
                                val url = getExplorerUrl(coinType, hash)
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                context.startActivity(intent)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF3B82F6)
                            )
                        ) {
                            Icon(Icons.Outlined.OpenInBrowser, "View", modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("View on ${getExplorerName(coinType)}", color = Color.White)
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
    coinType: CoinType
) {
    val context = LocalContext.current
    val (coinColor, _, _) = getCoinConfig(coinType)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White
        ),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF6B7280)
            )

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = address,
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = FontFamily.Monospace,
                    color = Color.Black,
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
                    Icon(Icons.Outlined.ContentCopy, "Copy", tint = coinColor, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

@Composable
fun EthereumFeePreviewCard(feeEstimate: EthereumFeeEstimate) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White
        ),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Network Fee",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = Color.Black
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Priority:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF6B7280)
                )

                Text(
                    text = feeEstimate.priority.name.lowercase().replaceFirstChar { it.uppercase() },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = Color.Black
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Total Fee:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF6B7280)
                )

                Text(
                    text = "${feeEstimate.totalFeeEth} ETH",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = Color.Black
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Gas Price:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF6B7280)
                )

                Text(
                    text = "${feeEstimate.gasPriceGwei} Gwei",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = Color.Black
                )
            }

            if (feeEstimate.estimatedTime != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Est. Time:",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFF6B7280)
                    )

                    Text(
                        text = "~${feeEstimate.estimatedTime}s",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = Color.Black
                    )
                }
            }
        }
    }
}

@Composable
fun BitcoinFeePreviewCard(feeEstimate: BitcoinFeeEstimate) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White
        ),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Network Fee",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = Color.Black
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Priority:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF6B7280)
                )

                Text(
                    text = feeEstimate.priority.name.lowercase().replaceFirstChar { it.uppercase() },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = Color.Black
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Total Fee:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF6B7280)
                )

                Text(
                    text = "${feeEstimate.totalFeeBtc} BTC",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = Color.Black
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Fee Rate:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF6B7280)
                )

                Text(
                    text = "${feeEstimate.feePerByte} sat/byte",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = Color.Black
                )
            }

            if (feeEstimate.estimatedTime != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Est. Time:",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFF6B7280)
                    )

                    Text(
                        text = "~${feeEstimate.estimatedTime}s",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = Color.Black
                    )
                }
            }
        }
    }
}

@Composable
fun USDCFeePreviewCard(feeEstimate: USDCFeeEstimate) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White
        ),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Network Fee",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = Color.Black
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Priority:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF6B7280)
                )

                Text(
                    text = feeEstimate.priority.name.lowercase().replaceFirstChar { it.uppercase() },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = Color.Black
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Total Fee:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF6B7280)
                )

                Text(
                    text = "${feeEstimate.totalFeeEth} ETH",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = Color.Black
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Gas Price:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF6B7280)
                )

                Text(
                    text = "${feeEstimate.gasPriceGwei} Gwei",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = Color.Black
                )
            }

            if (feeEstimate.estimatedTime != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Est. Time:",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFF6B7280)
                    )

                    Text(
                        text = "~${feeEstimate.estimatedTime}s",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = Color.Black
                    )
                }
            }
        }
    }
}

@Composable
fun SolanaFeePreviewCard(feeEstimate: SolanaFeeEstimate) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White
        ),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Network Fee",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = Color.Black
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Priority:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF6B7280)
                )

                Text(
                    text = feeEstimate.priority.name.lowercase().replaceFirstChar { it.uppercase() },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = Color.Black
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Total Fee:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF6B7280)
                )

                Text(
                    text = "${feeEstimate.feeSol} SOL",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = Color.Black
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Compute Units:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF6B7280)
                )

                Text(
                    text = feeEstimate.computeUnits.toString(),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = Color.Black
                )
            }

            if (feeEstimate.estimatedTime != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Est. Time:",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFF6B7280)
                    )

                    Text(
                        text = "~${feeEstimate.estimatedTime}s",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = Color.Black
                    )
                }
            }
        }
    }
}

// Helper functions
private fun getTokenSymbol(coinType: CoinType): String {
    return when (coinType) {
        CoinType.BITCOIN -> "BTC"
        CoinType.ETHEREUM -> "ETH"
        CoinType.SOLANA -> "SOL"
        CoinType.USDC -> "USDC"
    }
}

private fun getExplorerName(coinType: CoinType): String {
    return when (coinType) {
        CoinType.BITCOIN -> "Blockstream"
        CoinType.ETHEREUM -> "Etherscan"
        CoinType.SOLANA -> "Solscan"
        CoinType.USDC -> "Etherscan"
    }
}

private fun getExplorerUrl(coinType: CoinType, txHash: String): String {
    return when (coinType) {
        CoinType.BITCOIN -> "https://blockstream.info/tx/$txHash"
        CoinType.ETHEREUM -> "https://etherscan.io/tx/$txHash"
        CoinType.SOLANA -> "https://solscan.io/tx/$txHash"
        CoinType.USDC -> "https://etherscan.io/tx/$txHash"
    }
}

private fun getCoinConfig(coinType: CoinType): Triple<Color, ImageVector, String> {
    return when (coinType) {
        CoinType.BITCOIN -> Triple(Color(0xFFF7931A), Icons.Outlined.CurrencyBitcoin, "Bitcoin")
        CoinType.ETHEREUM -> Triple(Color(0xFF627EEA), Icons.Outlined.Diamond, "Ethereum")
        CoinType.SOLANA -> Triple(Color(0xFF00FFA3), Icons.Outlined.FlashOn, "Solana")
        CoinType.USDC -> Triple(Color(0xFF2775CA), Icons.Outlined.AttachMoney, "USDC")
    }
}