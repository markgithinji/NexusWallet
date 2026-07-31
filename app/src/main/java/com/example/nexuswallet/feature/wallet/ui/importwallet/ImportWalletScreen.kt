package com.example.nexuswallet.feature.wallet.ui.importwallet

import androidx.activity.compose.LocalActivity
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricPrompt
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.nexuswallet.R
import com.example.nexuswallet.feature.core.ui.NexusTextField
import com.example.nexuswallet.feature.ethereum.domain.model.EVMTokenType
import com.example.nexuswallet.feature.wallet.domain.model.BitcoinNetwork
import com.example.nexuswallet.feature.wallet.domain.model.EthereumNetwork
import com.example.nexuswallet.feature.wallet.domain.model.NativeETH
import com.example.nexuswallet.feature.wallet.domain.model.Network
import com.example.nexuswallet.feature.wallet.domain.model.SolanaNetwork
import com.example.nexuswallet.feature.wallet.domain.model.USDCToken
import com.example.nexuswallet.feature.wallet.domain.model.USDTToken
import com.example.nexuswallet.feature.wallet.domain.model.Wallet
import com.example.nexuswallet.feature.wallet.ui.common.*
import com.example.nexuswallet.feature.wallet.ui.walletcreation.WalletCreationUiState
import com.example.nexuswallet.ui.theme.bitcoinLight
import com.example.nexuswallet.ui.theme.ethereumLight
import com.example.nexuswallet.ui.theme.solanaLight
import com.example.nexuswallet.ui.theme.usdcLight
import com.example.nexuswallet.ui.theme.usdtLight

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportWalletScreen(
    viewModel: ImportWalletViewModel,
    onNavigateUp: () -> Unit,
    onNavigateToMain: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val words by viewModel.mnemonicWords.collectAsStateWithLifecycle()
    val walletName by viewModel.walletName.collectAsStateWithLifecycle()
    val currentStep by viewModel.currentStep.collectAsStateWithLifecycle()
    val selectedNetworks by viewModel.selectedNetworks.collectAsStateWithLifecycle()
    val selectedTokens by viewModel.selectedTokens.collectAsStateWithLifecycle()
    val authRequest by viewModel.authRequest.collectAsStateWithLifecycle()
    val cryptoObject by viewModel.cryptoObject.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val activity = LocalActivity.current as? AppCompatActivity

    val biometricPrompt = remember(activity) {
        if (activity == null) return@remember null
        val executor = ContextCompat.getMainExecutor(context)
        BiometricPrompt(
            activity,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    viewModel.completeImportAfterBiometric(result)
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    // Handle error if needed
                }
            }
        )
    }

    val biometricTitle = stringResource(R.string.biometric_authentication)
    val biometricSubtitle = stringResource(R.string.secure_your_wallet)
    val biometricCancel = stringResource(R.string.cancel)

    val promptInfo = remember(biometricTitle, biometricSubtitle, biometricCancel) {
        BiometricPrompt.PromptInfo.Builder()
            .setTitle(biometricTitle)
            .setSubtitle(biometricSubtitle)
            .setNegativeButtonText(biometricCancel)
            .build()
    }

    LaunchedEffect(authRequest) {
        if (authRequest != null) {
            if (cryptoObject != null) {
                biometricPrompt?.authenticate(promptInfo, cryptoObject!!)
            } else {
                biometricPrompt?.authenticate(promptInfo)
            }
        }
    }

    LaunchedEffect(uiState) {
        if (uiState is WalletCreationUiState.WalletCreated) {
            onNavigateToMain()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.import_wallet_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (currentStep == 0) onNavigateUp() else viewModel.previousStep()
                    }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            stringResource(R.string.back),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        if (currentStep < 2 && (uiState is WalletCreationUiState.Loading || uiState is WalletCreationUiState.WalletCreated)) {
            FullScreenLoading(message = stringResource(R.string.creating_wallet))
            return@Scaffold
        }

        val steps = listOf(
            stringResource(R.string.step_networks),
            stringResource(R.string.step_backup),
            stringResource(R.string.step_complete)
        )

        ImportWalletStepper(
            steps = steps,
            currentStep = if (currentStep == 0) 0 else if (uiState is WalletCreationUiState.WalletCreated) 2 else 1,
            padding = padding,
            content = {
                when {
                    currentStep == 0 -> ImportNetworkSelectionStep(
                        selectedNetworks = selectedNetworks,
                        selectedTokens = selectedTokens,
                        onNetworkToggle = viewModel::toggleNetwork,
                        onTokenToggle = viewModel::toggleToken,
                        onNext = viewModel::nextStep
                    )

                    uiState is WalletCreationUiState.WalletCreated -> {
                        val wallet = (uiState as WalletCreationUiState.WalletCreated).wallet
                        ImportSuccessStep(
                            wallet = wallet,
                            onFinish = onNavigateToMain
                        )
                    }

                    else -> ImportMnemonicStep(
                        words = words,
                        walletName = walletName,
                        uiState = uiState,
                        onWordChange = viewModel::updateWord,
                        onNameChange = viewModel::setWalletName,
                        onImport = viewModel::importWallet
                    )
                }
            }
        )
    }
}

@Composable
fun ImportWalletStepper(
    steps: List<String>,
    currentStep: Int,
    padding: PaddingValues,
    content: @Composable () -> Unit
) {
    val stepDescriptions = when (currentStep) {
        0 -> stringResource(R.string.step_networks_desc)
        1 -> stringResource(R.string.import_wallet_desc)
        2 -> stringResource(R.string.step_complete_desc)
        else -> ""
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .background(MaterialTheme.colorScheme.background)
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp,
            shadowElevation = 2.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = steps[currentStep.coerceIn(steps.indices)],
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = stringResource(R.string.step_x_of_y, currentStep + 1, steps.size),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Box(contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(
                            progress = { (currentStep + 1) / steps.size.toFloat() },
                            modifier = Modifier.size(48.dp),
                            strokeWidth = 4.dp,
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant,
                            strokeCap = androidx.compose.ui.graphics.StrokeCap.Round
                        )
                        Text(
                            text = "${((currentStep + 1) * 100 / steps.size)}%",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = stepDescriptions,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 20.sp
                )

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    steps.indices.forEach { index ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(
                                    when {
                                        index == currentStep -> MaterialTheme.colorScheme.primary
                                        index < currentStep -> MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                                        else -> MaterialTheme.colorScheme.surfaceVariant
                                    }
                                )
                        )
                    }
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
        ) {
            content()
        }
    }
}

@Composable
fun ImportNetworkSelectionStep(
    selectedNetworks: Set<Network>,
    selectedTokens: Map<EthereumNetwork, Set<EVMTokenType>>,
    onNetworkToggle: (Network, Boolean) -> Unit,
    onTokenToggle: (EthereumNetwork, EVMTokenType, Boolean) -> Unit,
    onNext: () -> Unit
) {
    val haptic = LocalHapticFeedback.current

    Column(
        modifier = Modifier
            .fillMaxSize()
    ) {
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                SectionHeader(
                    title = stringResource(R.string.bitcoin),
                    color = bitcoinLight
                )
            }

            item {
                NetworkToggleCard(
                    iconRes = R.drawable.bitcoin,
                    color = bitcoinLight,
                    network = BitcoinNetwork.Mainnet,
                    coinName = stringResource(R.string.bitcoin_name),
                    coinSymbol = stringResource(R.string.bitcoin_symbol),
                    isSelected = selectedNetworks.contains(BitcoinNetwork.Mainnet),
                    onSelectedChange = { onNetworkToggle(BitcoinNetwork.Mainnet, it) }
                )
            }

            item {
                NetworkToggleCard(
                    iconRes = R.drawable.bitcoin,
                    color = bitcoinLight.copy(alpha = 0.7f),
                    network = BitcoinNetwork.Testnet,
                    coinName = stringResource(R.string.bitcoin_name),
                    coinSymbol = stringResource(R.string.bitcoin_symbol),
                    isSelected = selectedNetworks.contains(BitcoinNetwork.Testnet),
                    onSelectedChange = { onNetworkToggle(BitcoinNetwork.Testnet, it) }
                )
            }

            item {
                Spacer(modifier = Modifier.height(12.dp))
                SectionHeader(
                    title = stringResource(R.string.ethereum),
                    color = ethereumLight
                )
            }

            item {
                NetworkToggleCard(
                    iconRes = R.drawable.ethereum,
                    color = ethereumLight,
                    network = EthereumNetwork.Mainnet,
                    coinName = stringResource(R.string.ethereum_name),
                    coinSymbol = stringResource(R.string.ethereum_symbol),
                    isSelected = selectedNetworks.contains(EthereumNetwork.Mainnet),
                    onSelectedChange = { onNetworkToggle(EthereumNetwork.Mainnet, it) }
                )
            }

            item {
                NetworkToggleCard(
                    iconRes = R.drawable.ethereum,
                    color = ethereumLight.copy(alpha = 0.7f),
                    network = EthereumNetwork.Sepolia,
                    coinName = stringResource(R.string.ethereum_name),
                    coinSymbol = stringResource(R.string.ethereum_symbol),
                    isSelected = selectedNetworks.contains(EthereumNetwork.Sepolia),
                    onSelectedChange = { onNetworkToggle(EthereumNetwork.Sepolia, it) }
                )
            }

            item {
                Spacer(modifier = Modifier.height(12.dp))
                SectionHeader(
                    title = stringResource(R.string.solana),
                    color = solanaLight
                )
            }

            item {
                NetworkToggleCard(
                    iconRes = R.drawable.solana,
                    color = solanaLight,
                    network = SolanaNetwork.Mainnet,
                    coinName = stringResource(R.string.solana_name),
                    coinSymbol = stringResource(R.string.solana_symbol),
                    isSelected = selectedNetworks.contains(SolanaNetwork.Mainnet),
                    onSelectedChange = { onNetworkToggle(SolanaNetwork.Mainnet, it) }
                )
            }

            item {
                NetworkToggleCard(
                    iconRes = R.drawable.solana,
                    color = solanaLight.copy(alpha = 0.7f),
                    network = SolanaNetwork.Devnet,
                    coinName = stringResource(R.string.solana_name),
                    coinSymbol = stringResource(R.string.solana_symbol),
                    isSelected = selectedNetworks.contains(SolanaNetwork.Devnet),
                    onSelectedChange = { onNetworkToggle(SolanaNetwork.Devnet, it) }
                )
            }

            if (selectedNetworks.any { it is EthereumNetwork }) {
                item {
                    Spacer(modifier = Modifier.height(12.dp))
                    SectionHeader(
                        title = stringResource(R.string.tokens),
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                item {
                    TokenToggleCard(
                        iconRes = R.drawable.usdc,
                        color = usdcLight,
                        network = EthereumNetwork.Mainnet,
                        evmTokenType = EVMTokenType.USDC,
                        tokenName = stringResource(R.string.usdc_name),
                        tokenSymbol = stringResource(R.string.usdc_symbol),
                        isSelected = selectedTokens[EthereumNetwork.Mainnet]?.contains(EVMTokenType.USDC) == true,
                        networkEnabled = selectedNetworks.contains(EthereumNetwork.Mainnet),
                        onSelectedChange = { onTokenToggle(EthereumNetwork.Mainnet, EVMTokenType.USDC, it) }
                    )
                }

                item {
                    TokenToggleCard(
                        iconRes = R.drawable.usdc,
                        color = usdcLight.copy(alpha = 0.7f),
                        network = EthereumNetwork.Sepolia,
                        evmTokenType = EVMTokenType.USDC,
                        tokenName = stringResource(R.string.usdc_name),
                        tokenSymbol = stringResource(R.string.usdc_symbol),
                        isSelected = selectedTokens[EthereumNetwork.Sepolia]?.contains(EVMTokenType.USDC) == true,
                        networkEnabled = selectedNetworks.contains(EthereumNetwork.Sepolia),
                        onSelectedChange = { onTokenToggle(EthereumNetwork.Sepolia, EVMTokenType.USDC, it) }
                    )
                }

                item {
                    TokenToggleCard(
                        iconRes = R.drawable.tether,
                        color = usdtLight,
                        network = EthereumNetwork.Mainnet,
                        evmTokenType = EVMTokenType.USDT,
                        tokenName = stringResource(R.string.usdt_name),
                        tokenSymbol = stringResource(R.string.usdt_symbol),
                        isSelected = selectedTokens[EthereumNetwork.Mainnet]?.contains(EVMTokenType.USDT) == true,
                        networkEnabled = selectedNetworks.contains(EthereumNetwork.Mainnet),
                        onSelectedChange = { onTokenToggle(EthereumNetwork.Mainnet, EVMTokenType.USDT, it) }
                    )
                }

                item {
                    TokenToggleCard(
                        iconRes = R.drawable.tether,
                        color = usdtLight.copy(alpha = 0.7f),
                        network = EthereumNetwork.Sepolia,
                        evmTokenType = EVMTokenType.USDT,
                        tokenName = stringResource(R.string.usdt_name),
                        tokenSymbol = stringResource(R.string.usdt_symbol),
                        isSelected = selectedTokens[EthereumNetwork.Sepolia]?.contains(EVMTokenType.USDT) == true,
                        networkEnabled = selectedNetworks.contains(EthereumNetwork.Sepolia),
                        onSelectedChange = { onTokenToggle(EthereumNetwork.Sepolia, EVMTokenType.USDT, it) }
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))
                AssetSummaryCard(
                    hasSelections = selectedNetworks.isNotEmpty(),
                    selectedNetworks = selectedNetworks,
                    selectedTokens = selectedTokens
                )
            }
        }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            tonalElevation = 8.dp,
            shadowElevation = 8.dp
        ) {
            Button(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onNext()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp),
                enabled = selectedNetworks.isNotEmpty(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Text(
                    text = stringResource(R.string.continue_button),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun ImportMnemonicStep(
    words: List<String>,
    walletName: String,
    uiState: WalletCreationUiState,
    onWordChange: (Int, String) -> Unit,
    onNameChange: (String) -> Unit,
    onImport: () -> Unit
) {
    val haptic = LocalHapticFeedback.current

    Column(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            item {
                NexusTextField(
                    value = walletName,
                    onValueChange = onNameChange,
                    label = stringResource(R.string.wallet_name_label),
                    placeholder = stringResource(R.string.wallet_name_placeholder)
                )
            }

            item {
                Text(
                    text = stringResource(R.string.recovery_phrase_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    shape = RoundedCornerShape(24.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(
                        modifier = Modifier
                            .padding(16.dp)
                            .animateContentSize()
                    ) {
                        for (row in 0 until 6) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                for (col in 0 until 2) {
                                    val index = row * 2 + col
                                    NexusTextField(
                                        value = words[index],
                                        onValueChange = { onWordChange(index, it) },
                                        label = "${index + 1}",
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                            if (row < 5) Spacer(modifier = Modifier.height(12.dp))
                        }
                    }
                }
            }

            if (uiState is WalletCreationUiState.Error) {
                item {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Error,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = uiState.message,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }
            }
        }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            tonalElevation = 8.dp,
            shadowElevation = 8.dp
        ) {
            Button(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onImport()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp),
                enabled = words.all { it.isNotBlank() }
            ) {
                Text(
                    text = stringResource(R.string.import_button),
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium
                )
            }
        }
    }
}

@Composable
fun ImportSuccessStep(
    wallet: Wallet,
    onFinish: () -> Unit
) {
    val totalAssets = wallet.bitcoinCoins.size +
            wallet.solanaCoins.size +
            wallet.evmTokens.size +
            wallet.solanaCoins.flatMap { it.splTokens }.size

    Column(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .weight(1f)
                .background(MaterialTheme.colorScheme.background)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(48.dp))

            Box(
                modifier = Modifier
                    .size(120.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "Success",
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            Text(
                text = stringResource(R.string.wallet_created_success),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(24.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp)
                ) {
                    Text(
                        text = stringResource(R.string.wallet_details),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    DetailRow(label = stringResource(R.string.name_label), value = wallet.name)
                    
                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        stringResource(R.string.enabled_assets),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.SemiBold
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    wallet.bitcoinCoins.forEach { coin ->
                        AssetDetailItem(
                            iconRes = R.drawable.bitcoin,
                            name = coin.name,
                            color = bitcoinLight,
                            isTestnet = coin.network.isTestnet
                        )
                    }

                    wallet.solanaCoins.forEach { coin ->
                        AssetDetailItem(
                            iconRes = R.drawable.solana,
                            name = coin.name,
                            color = solanaLight,
                            isTestnet = coin.network.isTestnet
                        )
                    }

                    wallet.evmTokens.forEach { token ->
                        val (color, iconRes) = when (token) {
                            is NativeETH -> Pair(ethereumLight, R.drawable.ethereum)
                            is USDCToken -> Pair(usdcLight, R.drawable.usdc)
                            is USDTToken -> Pair(usdtLight, R.drawable.tether)
                        }
                        AssetDetailItem(
                            iconRes = iconRes,
                            name = token.name,
                            color = color,
                            isTestnet = token.network.isTestnet
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.total_assets),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = totalAssets.toString(),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(32.dp))
        }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            tonalElevation = 8.dp,
            shadowElevation = 8.dp
        ) {
            Button(
                onClick = onFinish,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Outlined.Dashboard, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.go_to_dashboard),
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium
                )
            }
        }
    }
}
