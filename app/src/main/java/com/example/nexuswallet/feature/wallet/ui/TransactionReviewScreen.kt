package com.example.nexuswallet.feature.wallet.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.LocalGasStation
import androidx.compose.material.icons.outlined.OpenInBrowser
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DividerDefaults
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.nexuswallet.R
import com.example.nexuswallet.feature.bitcoin.domain.model.BitcoinFeeEstimate
import com.example.nexuswallet.feature.bitcoin.ui.review.BitcoinReviewEffect
import com.example.nexuswallet.feature.bitcoin.ui.review.BitcoinReviewViewModel
import com.example.nexuswallet.feature.core.domain.model.FeeLevel
import com.example.nexuswallet.feature.ethereum.domain.model.EVMFeeEstimate
import com.example.nexuswallet.feature.ethereum.domain.model.EVMTokenType
import com.example.nexuswallet.feature.ethereum.ui.EVMSendEffect
import com.example.nexuswallet.feature.ethereum.ui.EVMSendEvent
import com.example.nexuswallet.feature.ethereum.ui.EVMSendViewModel
import com.example.nexuswallet.feature.solana.domain.model.SolanaFeeEstimate
import com.example.nexuswallet.feature.solana.ui.SolanaSendEffect
import com.example.nexuswallet.feature.solana.ui.SolanaSendViewModel
import com.example.nexuswallet.feature.wallet.domain.model.BitcoinCoin
import com.example.nexuswallet.feature.wallet.domain.model.Coin
import com.example.nexuswallet.feature.wallet.domain.model.EVMToken
import com.example.nexuswallet.feature.wallet.domain.model.NativeETH
import com.example.nexuswallet.feature.wallet.domain.model.SolanaCoin
import com.example.nexuswallet.feature.wallet.domain.model.USDCToken
import com.example.nexuswallet.feature.wallet.domain.model.USDTToken
import com.example.nexuswallet.feature.wallet.ui.walletdetail.shimmer
import com.example.nexuswallet.ui.theme.bitcoinLight
import com.example.nexuswallet.ui.theme.ethereumLight
import com.example.nexuswallet.ui.theme.onSuccessContainer
import com.example.nexuswallet.ui.theme.solanaLight
import com.example.nexuswallet.ui.theme.success
import com.example.nexuswallet.ui.theme.successContainer
import com.example.nexuswallet.ui.theme.usdcLight
import com.example.nexuswallet.ui.theme.warning
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.firstOrNull

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionReviewScreen(
    onNavigateUp: () -> Unit,
    onDone: (String, Coin) -> Unit,
    walletId: String,
    toAddress: String,
    amount: String,
    feeLevel: String? = null,
    coin: Coin,
    ethereumViewModel: EVMSendViewModel = hiltViewModel(),
    solanaViewModel: SolanaSendViewModel = hiltViewModel(),
    bitcoinReviewViewModel: BitcoinReviewViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    var isSending by remember { mutableStateOf(false) }
    var sendError by remember { mutableStateOf<String?>(null) }
    var txHash by remember { mutableStateOf<String?>(null) }
    var explorerUrl by remember { mutableStateOf<String?>(null) }
    var txStatus by remember { mutableStateOf("") }
    var showSuccessBanner by remember { mutableStateOf(false) }

    val ethereumState = ethereumViewModel.uiState.collectAsStateWithLifecycle()
    val solanaState = solanaViewModel.state.collectAsStateWithLifecycle()
    val bitcoinState = bitcoinReviewViewModel.state.collectAsStateWithLifecycle()

    // Get coin config
    val (coinColor, iconRes) = getCoinDetailConfig(coin)

    // Handle Bitcoin effects
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
                    explorerUrl = effect.explorerUrl
                    txStatus = "Transaction sent!"
                    isSending = false
                    showSuccessBanner = true
                    delay(5000)
                    showSuccessBanner = false
                }
            }
        }
    }

    // Handle Ethereum effects
    LaunchedEffect(ethereumViewModel) {
        ethereumViewModel.effect.collect { effect ->
            when (effect) {
                is EVMSendEffect.ShowError -> {
                    sendError = effect.message
                    isSending = false
                }

                is EVMSendEffect.TransactionSent -> {
                    txHash = effect.txHash
                    explorerUrl = effect.explorerUrl
                    txStatus = "Transaction sent!"
                    isSending = false
                    showSuccessBanner = true
                    delay(5000)
                    showSuccessBanner = false
                }
            }
        }
    }

    // Handle Solana effects
    LaunchedEffect(solanaViewModel) {
        solanaViewModel.effect.collect { effect ->
            when (effect) {
                is SolanaSendEffect.ShowError -> {
                    sendError = effect.message
                    isSending = false
                }

                is SolanaSendEffect.TransactionSent -> {
                    txHash = effect.txHash
                    explorerUrl = effect.explorerUrl
                    txStatus = "Transaction sent!"
                    isSending = false
                    showSuccessBanner = true
                    delay(5000)
                    showSuccessBanner = false
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        when (coin) {
            is BitcoinCoin -> {
                bitcoinReviewViewModel.initialize(
                    walletId = walletId,
                    toAddress = toAddress,
                    amount = amount,
                    feeLevel = FeeLevel.valueOf(feeLevel ?: "NORMAL"),
                    network = coin.network
                )
                bitcoinReviewViewModel.prepareTransaction()
            }

            is EVMToken -> {
                ethereumViewModel.initialize(walletId, coin)
                ethereumViewModel.onEvent(EVMSendEvent.ToAddressChanged(toAddress))
                ethereumViewModel.onEvent(EVMSendEvent.AmountChanged(amount))
                feeLevel?.let {
                    ethereumViewModel.onEvent(EVMSendEvent.FeeLevelChanged(FeeLevel.valueOf(it)))
                }

                // If this is USDC or USDT (non-native), select the appropriate token
                if (coin.evmTokenType != EVMTokenType.NATIVE) {
                    // Wait for initialization
                    snapshotFlow { ethereumState.value.isInitialized }
                        .filter { it }
                        .firstOrNull()

                    // Find and select the token
                    val token = ethereumState.value.availableTokens.firstOrNull {
                        it.evmTokenType == coin.evmTokenType
                    }
                    token?.let { ethereumViewModel.selectToken(it) }
                }
            }

            is SolanaCoin -> {
                solanaViewModel.init(walletId, coin)
                solanaViewModel.updateToAddress(toAddress)
                solanaViewModel.updateAmount(amount)
                feeLevel?.let {
                    solanaViewModel.updateFeeLevel(FeeLevel.valueOf(it))
                }
            }
        }
    }

    // Extract data for display
    val fromAddress = when (coin) {
        is BitcoinCoin -> bitcoinState.value.fromAddress
        is SolanaCoin -> solanaState.value.walletAddress
        is EVMToken -> ethereumState.value.fromAddress
    }

    val selectedToken = if (coin is EVMToken) ethereumState.value.selectedToken else null

    val feeEstimate = when (coin) {
        is BitcoinCoin -> bitcoinState.value.feeEstimate
        is EVMToken -> ethereumState.value.feeEstimate
        is SolanaCoin -> solanaState.value.feeEstimate
    }

    val isFeeLoading = when (coin) {
        is BitcoinCoin -> bitcoinState.value.isFeeLoading
        is EVMToken -> ethereumState.value.isFeeLoading
        is SolanaCoin -> solanaState.value.isFeeLoading
    }

    val isReady = when (coin) {
        is BitcoinCoin -> bitcoinState.value.transactionPrepared
        is EVMToken -> ethereumState.value.validationResult.isValid
        is SolanaCoin -> solanaState.value.isValid
    }

    val isPreparing = when (coin) {
        is BitcoinCoin -> bitcoinState.value.isLoading && !bitcoinState.value.transactionPrepared
        else -> false
    }

    val isTestnet = coin.network.isTestnet
    val fullNetworkName = if (isTestnet) "${coin.network.name} Testnet" else coin.network.name

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
                            contentDescription = "Coin icon",
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
                            Icons.AutoMirrored.Filled.ArrowBack,
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
                isFeeLoading = isFeeLoading,
                onSend = {
                    isSending = true
                    sendError = null

                    when (coin) {
                        is EVMToken -> {
                            ethereumViewModel.send { hash ->
                                txHash = hash
                                txStatus = "Transaction sent!"
                                isSending = false
                            }
                        }

                        is SolanaCoin -> {
                            solanaViewModel.send { hash ->
                                txHash = hash
                                txStatus = "Transaction sent!"
                                isSending = false
                            }
                        }

                        is BitcoinCoin -> {
                            bitcoinReviewViewModel.sendTransaction { hash ->
                                txHash = hash
                                txStatus = "Transaction sent!"
                                isSending = false
                            }
                        }
                    }
                },
                onDone = { onDone(walletId, coin) }
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            TransactionReviewContent(
                coin = coin,
                amount = amount,
                fromAddress = fromAddress,
                toAddress = toAddress,
                feeEstimate = feeEstimate,
                isFeeLoading = isFeeLoading,
                txHash = txHash,
                explorerUrl = explorerUrl,
                coinColor = coinColor,
                iconRes = iconRes,
                tokenIconRes = tokenIconRes,
                selectedToken = selectedToken,
                networkName = fullNetworkName,
                isValid = true,
                validationErrors = if (sendError != null) listOf(sendError!!) else emptyList(),
                onCopyAddress = { address ->
                    copyToClipboard(context, address)
                },
                onViewOnExplorer = { _, url ->
                    val intent = Intent(Intent.ACTION_VIEW, url.toUri())
                    context.startActivity(intent)
                },
                modifier = Modifier.fillMaxSize()
            )

            // Floating Success Banner
            AnimatedVisibility(
                visible = showSuccessBanner,
                enter = fadeIn() + slideInVertically { -it },
                exit = fadeOut() + slideOutVertically { -it }
            ) {
                SuccessBanner(
                    txHash = txHash ?: "",
                    explorerUrl = explorerUrl,
                    onViewExplorer = { hash, url ->
                        val intent = Intent(Intent.ACTION_VIEW, url.toUri())
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
fun TransactionReviewContent(
    modifier: Modifier = Modifier,
    coin: Coin,
    amount: String,
    fromAddress: String?,
    toAddress: String,
    feeEstimate: Any?,
    isFeeLoading: Boolean,
    txHash: String?,
    explorerUrl: String?,
    coinColor: Color,
    iconRes: Int,
    tokenIconRes: Int? = null,
    selectedToken: EVMToken? = null,
    networkName: String? = null,
    isValid: Boolean = true,
    validationErrors: List<String> = emptyList(),
    onCopyAddress: (String) -> Unit,
    onViewOnExplorer: (String, String) -> Unit
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
                        text = selectedToken?.symbol ?: coin.symbol,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Medium,
                        color = coinColor,
                        modifier = Modifier.padding(bottom = 2.dp)
                    )
                }

                if (selectedToken != null && selectedToken !is NativeETH) {
                    Text(
                        text = "on ${selectedToken.network.name}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                } else if (networkName != null) {
                    Text(
                        text = "on $networkName",
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
            onCopy = { onCopyAddress(toAddress) }
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Fee Preview with shimmer loading
        if (isFeeLoading) {
            FeeLoadingShimmer()
        } else {
            feeEstimate?.let {
                when (it) {
                    is EVMFeeEstimate -> EVMFeePreviewCard(feeEstimate = it)
                    is BitcoinFeeEstimate -> BitcoinFeePreviewCard(feeEstimate = it)
                    is SolanaFeeEstimate -> SolanaFeePreviewCard(feeEstimate = it)
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Success Message
        txHash?.let { hash ->
            explorerUrl?.let { url ->
                TransactionSuccessCard(
                    hash = hash,
                    coin = coin,
                    coinColor = coinColor,
                    onViewOnExplorer = { onViewOnExplorer(hash, url) }
                )
            }
        }
    }
}

@Composable
fun AddressCard(
    label: String,
    address: String,
    coinColor: Color,
    iconRes: Int,
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
                    contentDescription = "Coin icon",
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
fun TransactionSuccessCard(
    hash: String,
    coin: Coin,
    coinColor: Color,
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
                            contentDescription = "Link icon",
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
                            val clipboard =
                                context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
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
                    "View on ${coin.network.name} Explorer",
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}

@Composable
fun SuccessBanner(
    txHash: String,
    explorerUrl: String?,
    onViewExplorer: (String, String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.successContainer,
            contentColor = MaterialTheme.colorScheme.onSuccessContainer
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
                    color = MaterialTheme.colorScheme.onSuccessContainer
                )
                Text(
                    text = "Hash: ${txHash.take(6)}...${txHash.takeLast(4)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSuccessContainer.copy(alpha = 0.8f),
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
                if (explorerUrl != null) {
                    IconButton(
                        onClick = { onViewExplorer(txHash, explorerUrl) },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            Icons.Outlined.OpenInBrowser,
                            contentDescription = "View on Explorer",
                            tint = MaterialTheme.colorScheme.onSuccessContainer,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                // Dismiss button
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        Icons.Outlined.Close,
                        contentDescription = "Dismiss",
                        tint = MaterialTheme.colorScheme.onSuccessContainer.copy(alpha = 0.7f),
                        modifier = Modifier.size(18.dp)
                    )
                }
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
    isFeeLoading: Boolean = false,
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
                    enabled = !isSending && !isPreparing && isValid && !isFeeLoading,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        disabledContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Text(
                        if (isSending || isPreparing) {
                            if (isPreparing) "Preparing..." else txStatus.ifEmpty { "Sending..." }
                        } else {
                            "Confirm & Send"
                        },
                        color = MaterialTheme.colorScheme.onPrimary,
                        style = MaterialTheme.typography.labelLarge
                    )
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
                    contentDescription = "Gas station icon",
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
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 4.dp),
                        thickness = DividerDefaults.Thickness,
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
                                contentDescription = "Schedule icon",
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
fun FeeLoadingShimmer() {
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
                // Icon shimmer
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .shimmer()
                )
                // Title shimmer
                Box(
                    modifier = Modifier
                        .width(100.dp)
                        .height(20.dp)
                        .shimmer()
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Priority chip shimmer
            Box(
                modifier = Modifier
                    .width(80.dp)
                    .height(24.dp)
                    .shimmer()
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Fee details shimmer
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                repeat(3) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .width(70.dp)
                                .height(16.dp)
                                .shimmer()
                        )
                        Box(
                            modifier = Modifier
                                .width(100.dp)
                                .height(16.dp)
                                .shimmer()
                        )
                    }
                }
            }

            // Estimated time section
            Spacer(modifier = Modifier.height(4.dp))
            HorizontalDivider(
                modifier = Modifier.padding(vertical = 4.dp),
                thickness = DividerDefaults.Thickness,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
            )
            Spacer(modifier = Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Icon shimmer
                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .shimmer()
                    )
                    // "Estimated time" text shimmer
                    Box(
                        modifier = Modifier
                            .width(90.dp)
                            .height(16.dp)
                            .shimmer()
                    )
                }
                // Time value shimmer
                Box(
                    modifier = Modifier
                        .width(50.dp)
                        .height(16.dp)
                        .shimmer()
                )
            }
        }
    }
}

private fun getCoinDetailConfig(coin: Coin): Pair<Color, Int> {
    return when (coin) {
        is BitcoinCoin -> Pair(bitcoinLight, R.drawable.bitcoin)
        is NativeETH -> Pair(ethereumLight, R.drawable.ethereum)
        is USDCToken -> Pair(usdcLight, R.drawable.usdc)
        is USDTToken -> Pair(Color(0xFF26A17B), R.drawable.tether)
        is SolanaCoin -> Pair(solanaLight, R.drawable.solana)
    }
}

private fun copyToClipboard(context: Context, address: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText("Address", address)
    clipboard.setPrimaryClip(clip)
    Toast.makeText(context, "Address copied", Toast.LENGTH_SHORT).show()
}

