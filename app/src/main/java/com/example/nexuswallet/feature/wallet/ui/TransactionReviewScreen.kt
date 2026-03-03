package com.example.nexuswallet.feature.wallet.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import com.example.nexuswallet.R
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.example.nexuswallet.feature.coin.CoinType
import com.example.nexuswallet.feature.coin.NetworkType
import com.example.nexuswallet.feature.coin.bitcoin.BitcoinFeeEstimate
import com.example.nexuswallet.feature.coin.bitcoin.BitcoinReviewEffect
import com.example.nexuswallet.feature.coin.bitcoin.BitcoinReviewViewModel
import com.example.nexuswallet.feature.coin.bitcoin.BitcoinSendEvent
import com.example.nexuswallet.feature.coin.bitcoin.BitcoinSendViewModel
import com.example.nexuswallet.feature.coin.bitcoin.FeeLevel
import com.example.nexuswallet.feature.coin.ethereum.EVMFeeEstimate
import com.example.nexuswallet.feature.coin.ethereum.EthereumSendEvent
import com.example.nexuswallet.feature.coin.ethereum.EthereumSendViewModel
import com.example.nexuswallet.feature.coin.solana.SolanaFeeEstimate
import com.example.nexuswallet.feature.coin.solana.SolanaSendEvent
import com.example.nexuswallet.feature.coin.solana.SolanaSendViewModel
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.BitcoinNetwork
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.EVMToken
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.EthereumNetwork
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.NativeETH
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.SolanaNetwork
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.USDCToken
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.USDTToken
import com.example.nexuswallet.ui.theme.bitcoinLight
import com.example.nexuswallet.ui.theme.ethereumLight
import com.example.nexuswallet.ui.theme.solanaLight
import com.example.nexuswallet.ui.theme.success
import com.example.nexuswallet.ui.theme.successContainer
import com.example.nexuswallet.ui.theme.usdcLight
import com.example.nexuswallet.ui.theme.warning
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withTimeoutOrNull
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionReviewScreen(
    onNavigateUp: () -> Unit,
    onNavigateToWalletDetail: (String) -> Unit,
    walletId: String,
    coinType: CoinType,
    toAddress: String,
    amount: String,
    feeLevel: String? = null,
    network: NetworkType? = null,
    ethereumViewModel: EthereumSendViewModel = hiltViewModel(),
    solanaViewModel: SolanaSendViewModel = hiltViewModel(),
    bitcoinReviewViewModel: BitcoinReviewViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    var isSending by remember { mutableStateOf(false) }
    var sendError by remember { mutableStateOf<String?>(null) }
    var txHash by remember { mutableStateOf<String?>(null) }
    var txStatus by remember { mutableStateOf("") }
    var showSuccessBanner by remember { mutableStateOf(false) }

    val ethereumState = ethereumViewModel.uiState.collectAsState()
    val solanaState = solanaViewModel.state.collectAsState()
    val bitcoinState = bitcoinReviewViewModel.state.collectAsState()

    LaunchedEffect(bitcoinReviewViewModel) {
        bitcoinReviewViewModel.effect.collect { effect ->
            when (effect) {
                is BitcoinReviewEffect.ShowError -> {
                    sendError = effect.message
                    isSending = false
                }
                is BitcoinReviewEffect.TransactionPrepared -> {}
                is BitcoinReviewEffect.TransactionSent -> {
                    txHash = effect.txHash
                    txStatus = "Transaction sent!"
                    isSending = false
                    showSuccessBanner = true
                    delay(5000)
                    showSuccessBanner = false
                }
            }
        }
    }

    // Get coin config with custom icons
    val (coinColor, iconRes, displayName) = when (coinType) {
        CoinType.BITCOIN -> Triple(bitcoinLight, R.drawable.bitcoin, "Bitcoin")
        CoinType.ETHEREUM -> Triple(ethereumLight, R.drawable.ethereum, "Ethereum")
        CoinType.SOLANA -> Triple(solanaLight, R.drawable.solana, "Solana")
        CoinType.USDC -> Triple(usdcLight, R.drawable.usdc, "USDC")
    }

    // Extract network objects from NetworkType enum
    val bitcoinNetwork = when (network) {
        NetworkType.BITCOIN_MAINNET -> BitcoinNetwork.Mainnet
        NetworkType.BITCOIN_TESTNET -> BitcoinNetwork.Testnet
        else -> null
    }

    val ethereumNetwork = when (network) {
        NetworkType.ETHEREUM_MAINNET -> EthereumNetwork.Mainnet
        NetworkType.ETHEREUM_SEPOLIA -> EthereumNetwork.Sepolia
        else -> null
    }

    val solanaNetwork = when (network) {
        NetworkType.SOLANA_MAINNET -> SolanaNetwork.Mainnet
        NetworkType.SOLANA_DEVNET -> SolanaNetwork.Devnet
        else -> null
    }

    LaunchedEffect(Unit) {
        when (coinType) {
            CoinType.ETHEREUM, CoinType.USDC -> {
                // For both ETH and USDC, we use the Ethereum ViewModel
                ethereumViewModel.initialize(walletId, ethereumNetwork)

                // Set the transaction data
                ethereumViewModel.onEvent(EthereumSendEvent.ToAddressChanged(toAddress))
                ethereumViewModel.onEvent(EthereumSendEvent.AmountChanged(amount))
                feeLevel?.let {
                    ethereumViewModel.onEvent(EthereumSendEvent.FeeLevelChanged(FeeLevel.valueOf(it)))
                }

                // If this is USDC, we need to select the USDC token
                if (coinType == CoinType.USDC) {
                    // Wait for initialization
                    snapshotFlow { ethereumState.value.isInitialized }
                        .filter { it }
                        .firstOrNull()

                    // Find and select USDC token
                    val usdcToken = ethereumState.value.availableTokens.firstOrNull { it is USDCToken }
                    usdcToken?.let { ethereumViewModel.selectToken(it) }
                }
            }
            CoinType.SOLANA -> {
                solanaViewModel.init(walletId, solanaNetwork)
                solanaViewModel.updateToAddress(toAddress)
                solanaViewModel.updateAmount(amount)
                feeLevel?.let {
                    solanaViewModel.updateFeeLevel(FeeLevel.valueOf(it))
                }
            }
            CoinType.BITCOIN -> {
                bitcoinReviewViewModel.initialize(
                    walletId = walletId,
                    toAddress = toAddress,
                    amount = amount,
                    feeLevel = FeeLevel.valueOf(feeLevel ?: "NORMAL"),
                    network = bitcoinNetwork ?: BitcoinNetwork.Testnet
                )
                bitcoinReviewViewModel.prepareTransaction()
            }
        }
    }

    // Extract data for display
    val fromAddress = when (coinType) {
        CoinType.ETHEREUM, CoinType.USDC -> ethereumState.value.fromAddress
        CoinType.SOLANA -> solanaState.value.walletAddress
        CoinType.BITCOIN -> bitcoinState.value.fromAddress
    }

    val selectedToken = if (coinType == CoinType.ETHEREUM || coinType == CoinType.USDC) {
        ethereumState.value.selectedToken
    } else null

    val feeEstimate = when (coinType) {
        CoinType.ETHEREUM, CoinType.USDC -> ethereumState.value.feeEstimate
        CoinType.SOLANA -> solanaState.value.feeEstimate
        CoinType.BITCOIN -> bitcoinState.value.feeEstimate
    }

    val isReady = when (coinType) {
        CoinType.BITCOIN -> bitcoinState.value.transactionPrepared
        CoinType.ETHEREUM, CoinType.USDC -> ethereumState.value.validationResult.isValid
        CoinType.SOLANA -> solanaState.value.isValid
        else -> true
    }

    val isPreparing = when (coinType) {
        CoinType.BITCOIN -> bitcoinState.value.isLoading && !bitcoinState.value.transactionPrepared
        else -> false
    }

    // Get network display name
    val networkDisplayName = network?.displayName ?: when (coinType) {
        CoinType.BITCOIN -> "Bitcoin"
        CoinType.ETHEREUM, CoinType.USDC -> "Ethereum"
        CoinType.SOLANA -> "Solana"
    }

    // Get token icon for selected token
    val tokenIconRes = when (selectedToken) {
        is NativeETH -> R.drawable.ethereum
        is USDCToken -> R.drawable.usdc
        is USDTToken -> R.drawable.tether
        else -> iconRes
    }

    Scaffold(
        topBar = {
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
                            text = "Review Transaction",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(
                            Icons.Default.ArrowBack,
                            "Back",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            TransactionBottomBar(
                txHash = txHash,
                isSending = isSending,
                sendError = sendError,
                txStatus = txStatus,
                isValid = isReady,
                isPreparing = isPreparing,
                onSend = {
                    isSending = true
                    sendError = null

                    when (coinType) {
                        CoinType.ETHEREUM, CoinType.USDC -> {
                            ethereumViewModel.send { hash ->
                                txHash = hash
                                txStatus = "Transaction sent!"
                                isSending = false
                            }
                        }
                        CoinType.SOLANA -> {
                            solanaViewModel.send { hash ->
                                txHash = hash
                                txStatus = "Transaction sent!"
                                isSending = false
                            }
                        }
                        CoinType.BITCOIN -> {
                            bitcoinReviewViewModel.sendTransaction { hash ->
                                txHash = hash
                                txStatus = "Transaction sent!"
                                isSending = false
                            }
                        }
                    }
                },
                onDone = { onNavigateToWalletDetail(walletId) }
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Main content
            if (isPreparing) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = coinColor)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Preparing transaction...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                TransactionReviewContent(
                    coinType = coinType,
                    amount = amount,
                    fromAddress = fromAddress,
                    toAddress = toAddress,
                    feeEstimate = feeEstimate,
                    txHash = txHash,
                    coinColor = coinColor,
                    iconRes = iconRes,
                    tokenIconRes = tokenIconRes,
                    selectedToken = selectedToken,
                    network = networkDisplayName,
                    isValid = true,
                    validationErrors = if (sendError != null) listOf(sendError!!) else emptyList(),
                    onCopyAddress = { address ->
                        copyToClipboard(context, address)
                    },
                    onViewOnExplorer = { hash ->
                        val url = getExplorerUrl(coinType, hash, network)
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                        context.startActivity(intent)
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }

            // Floating Success Banner
            AnimatedVisibility(
                visible = showSuccessBanner,
                enter = fadeIn() + slideInVertically { -it },
                exit = fadeOut() + slideOutVertically { -it }
            ) {
                SuccessBanner(
                    txHash = txHash ?: "",
                    coinType = coinType,
                    onViewExplorer = { hash ->
                        val url = getExplorerUrl(coinType, hash, network)
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                        context.startActivity(intent)
                    },
                    onDismiss = { showSuccessBanner = false },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                )
            }
        }
    }
}

@Composable
fun SuccessBanner(
    txHash: String,
    coinType: CoinType,
    onViewExplorer: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.successContainer
        ),
        elevation = CardDefaults.cardElevation(4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Success icon
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.success.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.success,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Message
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = "Transaction Sent!",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Hash: ${txHash.take(6)}...${txHash.takeLast(4)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Actions
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Explorer button
                IconButton(
                    onClick = { onViewExplorer(txHash) },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        Icons.Outlined.OpenInBrowser,
                        contentDescription = "View on Explorer",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Dismiss button
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        Icons.Outlined.Close,
                        contentDescription = "Dismiss",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun TransactionReviewContent(
    coinType: CoinType,
    amount: String,
    fromAddress: String?,
    toAddress: String,
    feeEstimate: Any?,
    txHash: String?,
    coinColor: Color,
    iconRes: Int,
    tokenIconRes: Int? = null,
    selectedToken: EVMToken? = null,
    network: Any? = null,
    isValid: Boolean = true,
    validationErrors: List<String> = emptyList(),
    onCopyAddress: (String) -> Unit,
    onViewOnExplorer: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        // Show validation errors if any
        if (!isValid && validationErrors.isNotEmpty()) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "Transaction cannot be sent:",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )

                    validationErrors.forEach { error ->
                        Text(
                            text = "• $error",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(start = 8.dp, top = 4.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
        }

        // Transaction Summary Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.cardElevation(0.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.Start
            ) {
                Text(
                    text = "You are sending",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Amount with better alignment
                Row(
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.Start
                ) {
                    Text(
                        text = amount,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = getTokenSymbol(coinType, selectedToken),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Medium,
                        color = coinColor,
                        modifier = Modifier.padding(bottom = 2.dp)
                    )
                }

                if (selectedToken != null && selectedToken !is NativeETH) {
                    Text(
                        text = "on ${selectedToken.network.displayName}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                } else if (network != null) {
                    Text(
                        text = "on ${network.toString()}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // From Address
        if (!fromAddress.isNullOrEmpty()) {
            AddressCard(
                label = "From",
                address = fromAddress,
                coinColor = coinColor,
                iconRes = iconRes,
                onCopy = { onCopyAddress(fromAddress) }
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // To Address Card
        AddressCard(
            label = "To",
            address = toAddress,
            coinColor = coinColor,
            iconRes = tokenIconRes ?: iconRes,
            isToAddress = true,
            onCopy = { onCopyAddress(toAddress) }
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Fee Preview
        feeEstimate?.let {
            when (coinType) {
                CoinType.ETHEREUM, CoinType.USDC -> EVMFeePreviewCard(feeEstimate = it as EVMFeeEstimate)
                CoinType.BITCOIN -> BitcoinFeePreviewCard(feeEstimate = it as BitcoinFeeEstimate)
                CoinType.SOLANA -> SolanaFeePreviewCard(feeEstimate = it as SolanaFeeEstimate)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Success Message
        txHash?.let { hash ->
            TransactionSuccessCard(
                hash = hash,
                coinType = coinType,
                coinColor = coinColor,
                network = network,
                onViewOnExplorer = { onViewOnExplorer(hash) }
            )
        }
    }
}

@Composable
fun AddressCard(
    label: String,
    address: String,
    coinColor: Color,
    iconRes: Int,
    isToAddress: Boolean = false,
    onCopy: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
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

            // Address info
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = address,
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Copy button
            IconButton(onClick = onCopy) {
                Icon(
                    Icons.Outlined.ContentCopy,
                    "Copy",
                    tint = coinColor,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
fun TransactionBottomBar(
    txHash: String?,
    isSending: Boolean,
    sendError: String?,
    txStatus: String,
    isValid: Boolean,
    isPreparing: Boolean = false,
    onSend: () -> Unit,
    onDone: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 8.dp
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            if (txHash != null) {
                Button(
                    onClick = onDone,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text(
                        "Done",
                        color = MaterialTheme.colorScheme.onPrimary,
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            } else {
                sendError?.let { error ->
                    Text(
                        text = error,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        textAlign = TextAlign.Center
                    )
                }

                Button(
                    onClick = onSend,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    enabled = !isSending && !isPreparing && isValid,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        disabledContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    if (isSending || isPreparing) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                if (isPreparing) "Preparing..." else txStatus.ifEmpty { "Sending..." },
                                color = MaterialTheme.colorScheme.onPrimary,
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    } else {
                        Text(
                            "Confirm & Send",
                            color = MaterialTheme.colorScheme.onPrimary,
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun EVMFeePreviewCard(feeEstimate: EVMFeeEstimate) {
    FeePreviewCard(
        priority = feeEstimate.priority,
        rows = listOf(
            "Total Fee" to "${feeEstimate.totalFeeEth} ETH",
            "Gas Price" to "${feeEstimate.gasPriceGwei} Gwei",
            "Gas Limit" to feeEstimate.gasLimit.toString()
        ),
        estimatedTime = feeEstimate.estimatedTime
    )
}

@Composable
fun BitcoinFeePreviewCard(feeEstimate: BitcoinFeeEstimate) {
    FeePreviewCard(
        priority = feeEstimate.priority,
        rows = listOf(
            "Total Fee" to "${feeEstimate.totalFeeBtc} BTC",
            "Fee Rate" to "${feeEstimate.feePerByte} sat/byte"
        ),
        estimatedTime = feeEstimate.estimatedTime
    )
}

@Composable
fun SolanaFeePreviewCard(feeEstimate: SolanaFeeEstimate) {
    FeePreviewCard(
        priority = feeEstimate.priority,
        rows = listOf(
            "Total Fee" to "${feeEstimate.feeSol} SOL",
            "Compute Units" to feeEstimate.computeUnits.toString()
        ),
        estimatedTime = feeEstimate.estimatedTime
    )
}

@Composable
fun FeePreviewCard(
    priority: FeeLevel,
    rows: List<Pair<String, String>>,
    estimatedTime: Int?
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            // Header with icon and title
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.LocalGasStation,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Network Fee",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Priority chip
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = getPriorityColor(priority).copy(alpha = 0.1f),
                contentColor = getPriorityColor(priority),
                modifier = Modifier.align(Alignment.Start)
            ) {
                Text(
                    text = priority.name.lowercase().replaceFirstChar { it.uppercase() },
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Fee details in a grid layout
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                rows.forEach { (label, value) ->
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
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                if (estimatedTime != null) {
                    Divider(
                        modifier = Modifier.padding(vertical = 4.dp),
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Schedule,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "Estimated time",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = "~${estimatedTime}s",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun getPriorityColor(priority: FeeLevel): Color {
    return when (priority) {
        FeeLevel.SLOW -> MaterialTheme.colorScheme.success
        FeeLevel.NORMAL -> MaterialTheme.colorScheme.primary
        FeeLevel.FAST -> MaterialTheme.colorScheme.warning
    }
}

@Composable
fun TransactionSuccessCard(
    hash: String,
    coinType: CoinType,
    coinColor: Color,
    network: Any? = null,
    onViewOnExplorer: () -> Unit
) {
    val context = LocalContext.current

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.success.copy(alpha = 0.5f)),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            // Success header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.success.copy(alpha = 0.1f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.CheckCircle,
                        contentDescription = "Success",
                        tint = MaterialTheme.colorScheme.success,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Column {
                    Text(
                        text = "Transaction Sent!",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Your transaction has been broadcast",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Transaction hash with copy option
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Link,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = hash.take(8) + "..." + hash.takeLast(8),
                            style = MaterialTheme.typography.bodyMedium,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    // Copy hash button
                    IconButton(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = ClipData.newPlainText("Transaction Hash", hash)
                            clipboard.setPrimaryClip(clip)
                            Toast.makeText(context, "Hash copied", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.ContentCopy,
                            contentDescription = "Copy hash",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Explorer button
            Button(
                onClick = onViewOnExplorer,
                modifier = Modifier.fillMaxWidth(),
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
                    "View on ${getExplorerName(coinType)}",
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}

// Helper functions
private fun copyToClipboard(context: Context, address: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText("Address", address)
    clipboard.setPrimaryClip(clip)
    Toast.makeText(context, "Address copied", Toast.LENGTH_SHORT).show()
}

private fun getTokenSymbol(coinType: CoinType, token: EVMToken? = null): String {
    return when {
        token != null -> token.symbol
        coinType == CoinType.BITCOIN -> "BTC"
        coinType == CoinType.ETHEREUM -> "ETH"
        coinType == CoinType.SOLANA -> "SOL"
        coinType == CoinType.USDC -> "USDC"
        else -> ""
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

private fun getExplorerUrl(coinType: CoinType, txHash: String, network: NetworkType?): String {
    return when (coinType) {
        CoinType.BITCOIN -> {
            when (network) {
                NetworkType.BITCOIN_TESTNET -> "https://blockstream.info/testnet/tx/$txHash"
                else -> "https://blockstream.info/tx/$txHash"
            }
        }
        CoinType.ETHEREUM, CoinType.USDC -> {
            when (network) {
                NetworkType.ETHEREUM_SEPOLIA -> "https://sepolia.etherscan.io/tx/$txHash"
                else -> "https://etherscan.io/tx/$txHash"
            }
        }
        CoinType.SOLANA -> {
            when (network) {
                NetworkType.SOLANA_DEVNET -> "https://solscan.io/tx/$txHash?cluster=devnet"
                else -> "https://solscan.io/tx/$txHash"
            }
        }
    }
}