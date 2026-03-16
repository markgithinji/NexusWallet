package com.example.nexuswallet.feature.wallet.ui.walletcreation

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Token
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.nexuswallet.R
import com.example.nexuswallet.feature.core.domain.model.CoinType
import com.example.nexuswallet.feature.wallet.domain.model.BitcoinNetwork
import com.example.nexuswallet.feature.wallet.domain.model.EthereumNetwork
import com.example.nexuswallet.feature.wallet.domain.model.NativeETH
import com.example.nexuswallet.feature.wallet.domain.model.Network
import com.example.nexuswallet.feature.wallet.domain.model.SolanaNetwork
import com.example.nexuswallet.feature.wallet.domain.model.USDCToken
import com.example.nexuswallet.feature.wallet.domain.model.USDTToken
import com.example.nexuswallet.feature.wallet.domain.model.Wallet
import com.example.nexuswallet.feature.wallet.ui.common.ErrorScreen
import com.example.nexuswallet.feature.wallet.ui.common.FullScreenLoading
import com.example.nexuswallet.ui.theme.bitcoinLight
import com.example.nexuswallet.ui.theme.ethereumLight
import com.example.nexuswallet.ui.theme.solanaLight
import com.example.nexuswallet.ui.theme.usdcLight
import com.example.nexuswallet.ui.theme.usdtLight

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WalletCreationScreen(
    onNavigateUp: () -> Unit,
    onNavigateToMain: () -> Unit,
    viewModel: WalletCreationViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val currentStep by viewModel.currentStep.collectAsStateWithLifecycle()
    val mnemonic by viewModel.mnemonic.collectAsStateWithLifecycle()
    val selectedNetworks by viewModel.selectedNetworks.collectAsStateWithLifecycle()
    val selectedTokens by viewModel.selectedTokens.collectAsStateWithLifecycle()
    val walletName by viewModel.walletName.collectAsStateWithLifecycle()
    val isMnemonicGenerated by viewModel.isMnemonicGenerated.collectAsStateWithLifecycle()
    val enteredWords by viewModel.enteredWords.collectAsStateWithLifecycle()

    // Track if user has seen the mnemonic warning
    var hasSeenSecurityWarning by remember { mutableStateOf(false) }

    // Handle navigation when wallet is created
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
                        "Create Wallet",
                        color = MaterialTheme.colorScheme.onSurface
                    )
                },
                navigationIcon = {
                    if (currentStep > 0) {
                        IconButton(
                            onClick = {
                                if (currentStep == 1 && !hasSeenSecurityWarning) {
                                    onNavigateUp()
                                } else {
                                    viewModel.previousStep()
                                }
                            }
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                "Back",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        if (currentStep < 4 && uiState is WalletCreationUiState.Loading) {
            FullScreenLoading(message = "Creating wallet...")
            return@Scaffold
        }

        if (currentStep < 4 && uiState is WalletCreationUiState.Error) {
            ErrorScreen(
                message = (uiState as WalletCreationUiState.Error).message,
                onRetry = { viewModel.reset() }
            )
            return@Scaffold
        }

        WalletCreationStepper(
            currentStep = currentStep,
            padding = padding,
            content = {
                when (currentStep) {
                    0 -> NetworkSelectionStep(
                        selectedNetworks = selectedNetworks,
                        selectedTokens = selectedTokens,
                        onNetworkToggle = { network, isSelected ->
                            viewModel.toggleNetwork(network, isSelected)
                        },
                        onTokenToggle = { network, coinType, isSelected ->
                            viewModel.toggleToken(network, coinType, isSelected)
                        },
                        onNext = { viewModel.nextStep() },
                        hasSelections = viewModel.hasSelections()
                    )

                    1 -> {
                        if (!hasSeenSecurityWarning) {
                            SecurityWarningDialog(
                                onAccept = {
                                    hasSeenSecurityWarning = true
                                    if (!isMnemonicGenerated) {
                                        viewModel.generateMnemonic()
                                    }
                                },
                                onCancel = { viewModel.previousStep() }
                            )
                        }

                        if (hasSeenSecurityWarning) {
                            if (mnemonic.isNotEmpty()) {
                                MnemonicDisplayStep(
                                    mnemonic = mnemonic,
                                    onNext = { viewModel.nextStep() },
                                    onBack = { viewModel.previousStep() }
                                )
                            } else {
                                FullScreenLoading(message = "Generating secure recovery phrase...")
                            }
                        }
                    }

                    2 -> MnemonicVerificationStep(
                        mnemonic = mnemonic,
                        enteredWords = enteredWords,
                        onAddWord = { word -> viewModel.addWordToVerification(word) },
                        onRemoveWord = { index -> viewModel.removeWordFromVerification(index) },
                        onVerify = {
                            if (viewModel.completeVerificationAndMoveNext()) {
                                viewModel.nextStep()
                            }
                        },
                        onBack = { viewModel.previousStep() }
                    )

                    3 -> WalletNameStep(
                        walletName = walletName,
                        onNameChange = { name -> viewModel.setWalletName(name) },
                        onCreate = { viewModel.createWallet() }
                    )

                    4 -> {
                        when (uiState) {
                            is WalletCreationUiState.WalletCreated -> {
                                val wallet = (uiState as WalletCreationUiState.WalletCreated).wallet
                                WalletSuccessStep(
                                    wallet = wallet,
                                    onFinish = onNavigateToMain
                                )
                            }

                            is WalletCreationUiState.Loading -> {
                                FullScreenLoading(message = "Creating wallet...")
                            }

                            else -> {
                                LaunchedEffect(Unit) {
                                    viewModel.previousStep()
                                }
                                FullScreenLoading(message = "Loading...")
                            }
                        }
                    }
                }
            }
        )
    }
}

@Composable
fun WalletCreationStepper(
    currentStep: Int,
    padding: PaddingValues,
    content: @Composable () -> Unit
) {
    val steps = listOf("Networks", "Backup", "Verify", "Name", "Complete")
    val stepDescriptions = remember(currentStep) {
        when (currentStep) {
            0 -> "Select which networks and tokens to include"
            1 -> "Backup your recovery phrase"
            2 -> "Verify your backup"
            3 -> "Personalize your wallet"
            4 -> "Wallet created successfully"
            else -> ""
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Text(
                text = "Step ${currentStep + 1} of ${steps.size}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Medium
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = stepDescriptions,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            steps.forEachIndexed { index, step ->
                StepIndicator(
                    stepNumber = index + 1,
                    stepName = step,
                    isActive = index == currentStep,
                    isCompleted = index < currentStep,
                    isNext = index == currentStep + 1
                )
            }
        }

        LinearProgressIndicator(
            progress = { (currentStep + 1) / steps.size.toFloat() },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp)
                .height(4.dp),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )

        Spacer(modifier = Modifier.height(24.dp))

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            content()
        }
    }
}

@Composable
fun NetworkSelectionStep(
    selectedNetworks: Set<Network>,
    selectedTokens: Map<EthereumNetwork, Set<CoinType>>,
    onNetworkToggle: (Network, Boolean) -> Unit,
    onTokenToggle: (EthereumNetwork, CoinType, Boolean) -> Unit,
    onNext: () -> Unit,
    hasSelections: Boolean
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        // ============ BITCOIN SECTION ============
        Text(
            text = CoinType.BITCOIN.displayName,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = bitcoinLight,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        // Bitcoin Mainnet
        NetworkToggleCard(
            iconRes = R.drawable.bitcoin,
            color = bitcoinLight,
            network = BitcoinNetwork.Mainnet,
            isSelected = selectedNetworks.contains(BitcoinNetwork.Mainnet),
            onSelectedChange = { isSelected ->
                onNetworkToggle(BitcoinNetwork.Mainnet, isSelected)
            }
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Bitcoin Testnet
        NetworkToggleCard(
            iconRes = R.drawable.bitcoin,
            color = bitcoinLight.copy(alpha = 0.7f),
            network = BitcoinNetwork.Testnet,
            isSelected = selectedNetworks.contains(BitcoinNetwork.Testnet),
            onSelectedChange = { isSelected ->
                onNetworkToggle(BitcoinNetwork.Testnet, isSelected)
            }
        )

        Spacer(modifier = Modifier.height(24.dp))

        // ============ ETHEREUM SECTION ============
        Text(
            text = CoinType.ETHEREUM.displayName,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = ethereumLight,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        // Ethereum Mainnet
        NetworkToggleCard(
            iconRes = R.drawable.ethereum,
            color = ethereumLight,
            network = EthereumNetwork.Mainnet,
            isSelected = selectedNetworks.contains(EthereumNetwork.Mainnet),
            onSelectedChange = { isSelected ->
                onNetworkToggle(EthereumNetwork.Mainnet, isSelected)
            }
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Ethereum Sepolia
        NetworkToggleCard(
            iconRes = R.drawable.ethereum,
            color = ethereumLight.copy(alpha = 0.7f),
            network = EthereumNetwork.Sepolia,
            isSelected = selectedNetworks.contains(EthereumNetwork.Sepolia),
            onSelectedChange = { isSelected ->
                onNetworkToggle(EthereumNetwork.Sepolia, isSelected)
            }
        )

        Spacer(modifier = Modifier.height(24.dp))

        // ============ SOLANA SECTION ============
        Text(
            text = CoinType.SOLANA.displayName,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = solanaLight,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        // Solana Mainnet
        NetworkToggleCard(
            iconRes = R.drawable.solana,
            color = solanaLight,
            network = SolanaNetwork.Mainnet,
            isSelected = selectedNetworks.contains(SolanaNetwork.Mainnet),
            onSelectedChange = { isSelected ->
                onNetworkToggle(SolanaNetwork.Mainnet, isSelected)
            }
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Solana Devnet
        NetworkToggleCard(
            iconRes = R.drawable.solana,
            color = solanaLight.copy(alpha = 0.7f),
            network = SolanaNetwork.Devnet,
            isSelected = selectedNetworks.contains(SolanaNetwork.Devnet),
            onSelectedChange = { isSelected ->
                onNetworkToggle(SolanaNetwork.Devnet, isSelected)
            }
        )

        Spacer(modifier = Modifier.height(24.dp))

        // ============ TOKENS SECTION ============
        Text(
            text = "Tokens",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        // USDC on Ethereum Mainnet
        TokenToggleCard(
            iconRes = R.drawable.usdc,
            color = usdcLight,
            network = EthereumNetwork.Mainnet,
            coinType = CoinType.USDC,
            isSelected = selectedTokens[EthereumNetwork.Mainnet]?.contains(CoinType.USDC) == true,
            onSelectedChange = { isSelected ->
                onTokenToggle(EthereumNetwork.Mainnet, CoinType.USDC, isSelected)
            }
        )

        Spacer(modifier = Modifier.height(8.dp))

        // USDC on Ethereum Sepolia
        TokenToggleCard(
            iconRes = R.drawable.usdc,
            color = usdcLight.copy(alpha = 0.7f),
            network = EthereumNetwork.Sepolia,
            coinType = CoinType.USDC,
            isSelected = selectedTokens[EthereumNetwork.Sepolia]?.contains(CoinType.USDC) == true,
            onSelectedChange = { isSelected ->
                onTokenToggle(EthereumNetwork.Sepolia, CoinType.USDC, isSelected)
            }
        )

        Spacer(modifier = Modifier.height(8.dp))

//        // USDT on Ethereum Mainnet
//        TokenToggleCard(
//            iconRes = R.drawable.tether,
//            color = usdtLight,
//            network = EthereumNetwork.Mainnet,
//            coinType = CoinType.USDT,  // Changed from CoinType.USDC to CoinType.USDT
//            isSelected = selectedTokens[EthereumNetwork.Mainnet]?.contains(CoinType.USDT) == true,  // Check for USDT
//            onSelectedChange = { isSelected ->
//                onTokenToggle(EthereumNetwork.Mainnet, CoinType.USDT, isSelected)  // Pass USDT
//            }
//        )

                    Spacer(modifier = Modifier.weight(1f))
        Spacer(modifier = Modifier.height(16.dp))

        // Summary Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "Selected Assets",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(8.dp))

                if (!hasSelections) {
                    Text(
                        text = "No assets selected",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium
                    )
                } else {
                    // Show selected networks
                    selectedNetworks.forEach { network ->
                        Text(
                            text = "• ${network.coinType.displayName} - ${network.displayName}",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(vertical = 2.dp)
                        )
                    }
                    // Show selected tokens
                    selectedTokens.forEach { (network, tokens) ->
                        tokens.forEach { coinType ->
                            Text(
                                text = "• ${coinType.displayName} on ${network.displayName}",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(vertical = 2.dp)
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = onNext,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            enabled = hasSelections,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
        ) {
            Text("Continue")
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
fun NetworkToggleCard(
    iconRes: Int,
    color: Color,
    network: Network,
    isSelected: Boolean,
    onSelectedChange: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) color.copy(alpha = 0.05f)
            else MaterialTheme.colorScheme.surface
        ),
        border = if (isSelected) BorderStroke(1.dp, color) else BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .clickable { onSelectedChange(!isSelected) },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(48.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(id = iconRes),
                    contentDescription = network.coinType.displayName,
                    tint = Color.Unspecified,
                    modifier = Modifier.size(36.dp)
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = network.coinType.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (isSelected) color else MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "${network.coinType.symbol} • ${network.displayName}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Checkbox(
                checked = isSelected,
                onCheckedChange = onSelectedChange,
                colors = CustomCheckboxDefaults.colors(
                    checkedBackgroundColor = color,
                    checkedBorderColor = color,
                    uncheckedBorderColor = MaterialTheme.colorScheme.outline,
                    checkedIconColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    }
}

@Composable
fun TokenToggleCard(
    iconRes: Int,
    color: Color,
    network: EthereumNetwork,
    coinType: CoinType,
    isSelected: Boolean,
    onSelectedChange: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) color.copy(alpha = 0.05f)
            else MaterialTheme.colorScheme.surface
        ),
        border = if (isSelected) BorderStroke(1.dp, color) else BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .clickable { onSelectedChange(!isSelected) },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(48.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(id = iconRes),
                    contentDescription = coinType.displayName,
                    tint = Color.Unspecified,
                    modifier = Modifier.size(36.dp)
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = coinType.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (isSelected) color else MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "${coinType.symbol} on ${network.displayName}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Checkbox(
                checked = isSelected,
                onCheckedChange = onSelectedChange,
                colors = CustomCheckboxDefaults.colors(
                    checkedBackgroundColor = color,
                    checkedBorderColor = color,
                    uncheckedBorderColor = MaterialTheme.colorScheme.outline,
                    checkedIconColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    }
}

@Composable
fun StepIndicator(
    stepNumber: Int,
    stepName: String,
    isActive: Boolean,
    isCompleted: Boolean,
    isNext: Boolean = false
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(50.dp)
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(
                    color = when {
                        isCompleted -> MaterialTheme.colorScheme.primary
                        isActive -> MaterialTheme.colorScheme.primaryContainer
                        isNext -> MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                        else -> MaterialTheme.colorScheme.surfaceVariant
                    }
                )
                .border(
                    width = if (isActive) 2.dp else 0.dp,
                    color = MaterialTheme.colorScheme.primary,
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            if (isCompleted) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Completed",
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(18.dp)
                )
            } else {
                Text(
                    text = stepNumber.toString(),
                    color = when {
                        isActive -> MaterialTheme.colorScheme.onPrimaryContainer
                        isNext -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = stepName,
            style = MaterialTheme.typography.labelSmall,
            color = when {
                isActive || isCompleted -> MaterialTheme.colorScheme.primary
                isNext -> MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            },
            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun SecurityWarningDialog(
    onAccept: () -> Unit,
    onCancel: () -> Unit
) {
    Dialog(
        onDismissRequest = onCancel,
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp)
            ) {
                // Warning Icon
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.error.copy(alpha = 0.1f))
                        .align(Alignment.CenterHorizontally),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "Security Warning",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(32.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Title
                Text(
                    text = "Critical Security Warning",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Warning Message
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.05f)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = "Your recovery phrase is the ONLY way to restore your wallet. If you lose it, you lose access to your funds FOREVER.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.Medium
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // Key points as chips
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf(
                                "✕ Never share it",
                                "✕ Never store digitally",
                                "✓ Write on paper only"
                            ).forEach { point ->
                                Card(
                                    shape = RoundedCornerShape(8.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.1f)
                                    )
                                ) {
                                    Text(
                                        text = point,
                                        modifier = Modifier.padding(
                                            horizontal = 12.dp,
                                            vertical = 6.dp
                                        ),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Cancel button
                    OutlinedButton(
                        onClick = onCancel,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                    ) {
                        Text(
                            "Cancel",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    // Accept button
                    Button(
                        onClick = onAccept,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError
                        )
                    ) {
                        Text(
                            "I Understand",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun MnemonicDisplayStep(
    mnemonic: List<String>,
    onNext: () -> Unit,
    onBack: () -> Unit
) {
    var hasWrittenDown by remember { mutableStateOf(false) }
    var hasStoredSafely by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 100.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header
        item {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp)
            ) {
                // Critical warning
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = "Warning",
                                tint = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Critical Security Step:",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "Write down these 12 words IN ORDER on paper. " +
                                    "Never store digitally. This is the ONLY way to restore your wallet.",
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }

        // Mnemonic Grid
        item {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .heightIn(min = 300.dp, max = 400.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(mnemonic) { word ->
                    MnemonicDisplayChip(
                        word = word,
                        index = mnemonic.indexOf(word) + 1
                    )
                }
            }
        }

        // Safety Tips
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "Safety Checklist:",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    SafetyChecklistItem(
                        text = "Write on paper (not digital)",
                        checked = true
                    )
                    SafetyChecklistItem(
                        text = "Store in secure location",
                        checked = true
                    )
                    SafetyChecklistItem(
                        text = "Never share with anyone",
                        checked = true
                    )
                    SafetyChecklistItem(
                        text = "Keep away from moisture/fire",
                        checked = true
                    )
                }
            }
        }

        // Confirmation checkboxes
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                // Written down checkbox
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (hasWrittenDown)
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.05f)
                        else
                            MaterialTheme.colorScheme.surface
                    ),
                    border = if (hasWrittenDown)
                        BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                    else
                        BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                            .clickable { hasWrittenDown = !hasWrittenDown },
                        verticalAlignment = Alignment.CenterVertically
                    ) {

                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(
                                    if (hasWrittenDown)
                                        MaterialTheme.colorScheme.primary
                                    else
                                        Color.Transparent
                                )
                                .border(
                                    width = 2.dp,
                                    color = if (hasWrittenDown)
                                        MaterialTheme.colorScheme.primary
                                    else
                                        MaterialTheme.colorScheme.outline,
                                    shape = RoundedCornerShape(6.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            if (hasWrittenDown) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Checked",
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Text(
                            text = "I have written down all 12 words on paper",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (hasWrittenDown)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Stored safely checkbox
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (hasStoredSafely)
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.05f)
                        else
                            MaterialTheme.colorScheme.surface
                    ),
                    border = if (hasStoredSafely)
                        BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                    else
                        BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                            .clickable { hasStoredSafely = !hasStoredSafely },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Custom checkbox
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(
                                    if (hasStoredSafely)
                                        MaterialTheme.colorScheme.primary
                                    else
                                        Color.Transparent
                                )
                                .border(
                                    width = 2.dp,
                                    color = if (hasStoredSafely)
                                        MaterialTheme.colorScheme.primary
                                    else
                                        MaterialTheme.colorScheme.outline,
                                    shape = RoundedCornerShape(6.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            if (hasStoredSafely) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Checked",
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Text(
                            text = "I have stored them in a secure location",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (hasStoredSafely)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(20.dp))
        }
    }

    // Fixed buttons at bottom
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.BottomCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                            MaterialTheme.colorScheme.surface
                        ),
                        startY = 0f,
                        endY = 100f
                    )
                )
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onBack,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.primary
                    ),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
                ) {
                    Text("Back")
                }

                Button(
                    onClick = onNext,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    enabled = hasWrittenDown && hasStoredSafely,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
                ) {
                    Text("I've Backed It Up")
                }
            }
        }
    }
}

@Composable
fun MnemonicDisplayChip(
    word: String,
    index: Int
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1.2f),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "$index.",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = word,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    }
}

@Composable
fun SafetyChecklistItem(text: String, checked: Boolean) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 4.dp)
    ) {
        Icon(
            imageVector = if (checked) Icons.Default.CheckCircle else Icons.Default.Circle,
            contentDescription = null,
            tint = if (checked) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
fun MnemonicVerificationStep(
    mnemonic: List<String>,
    enteredWords: List<String>,
    onAddWord: (String) -> Unit,
    onRemoveWord: (Int) -> Unit,
    onVerify: () -> Unit,
    onBack: () -> Unit
) {
    val shuffledWords = remember { mnemonic.shuffled() }
    var verificationError by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .verticalScroll(rememberScrollState())
            .background(MaterialTheme.colorScheme.background)
    ) {
        Text(
            text = "Tap words in the correct order",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Selected words container
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp)
            ) {
                Text(
                    text = "Selected Words (${enteredWords.size}/${mnemonic.size})",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(8.dp))

                if (enteredWords.isEmpty()) {
                    // Compact empty state
                    Text(
                        text = "No words selected yet",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                } else {
                    // FlowRow for selected words
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        enteredWords.forEachIndexed { index, word ->
                            SimpleSelectedChip(
                                word = word,
                                index = index + 1,
                                onRemove = { onRemoveWord(index) }
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Available words section
        Text(
            text = "Available Words",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Available words container
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp)
            ) {
                // Get remaining words
                val remainingWords = shuffledWords.filter { word -> !enteredWords.contains(word) }

                if (remainingWords.isEmpty()) {
                    // Compact success state
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(vertical = 4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Success",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "All words selected",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                } else {
                    // FlowRow for available words
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        remainingWords.forEach { word ->
                            SimpleWordChip(
                                word = word,
                                onClick = { onAddWord(word) }
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Error message
        if (verificationError) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Error,
                        contentDescription = "Error",
                        tint = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Wrong order. Please try again.",
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        Button(
            onClick = {
                if (enteredWords.size == mnemonic.size && enteredWords == mnemonic) {
                    verificationError = false
                    onVerify()
                } else {
                    verificationError = true
                }
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            enabled = enteredWords.size == mnemonic.size,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
        ) {
            Text("Verify & Continue")
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
fun SimpleSelectedChip(
    word: String,
    index: Int,
    onRemove: () -> Unit
) {
    Card(
        onClick = onRemove,
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "$index.",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = word,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Spacer(modifier = Modifier.width(4.dp))
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Remove",
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

@Composable
fun SimpleWordChip(
    word: String,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Text(
            text = word,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
fun WalletNameStep(
    walletName: String,
    onNameChange: (String) -> Unit,
    onCreate: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        OutlinedTextField(
            value = walletName,
            onValueChange = onNameChange,
            modifier = Modifier.fillMaxWidth(),
            label = {
                Text(
                    "Wallet Name",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            placeholder = {
                Text(
                    "e.g., My Savings Wallet",
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
            },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                cursorColor = MaterialTheme.colorScheme.primary,
                focusedLabelColor = MaterialTheme.colorScheme.primary,
                unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
            )
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Tip: Use a descriptive name like 'Savings' or 'Trading'",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.weight(1f))

        Button(
            onClick = onCreate,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            enabled = walletName.isNotBlank(),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
        ) {
            Text("Create Wallet")
        }
    }
}

@Composable
fun WalletSuccessStep(
    wallet: Wallet,
    onFinish: () -> Unit
) {
    // Calculate total assets correctly
    val totalAssets = wallet.bitcoinCoins.size +
            wallet.solanaCoins.size +
            wallet.evmTokens.size +
            wallet.solanaCoins.flatMap { it.splTokens }.size

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Success icon
        Box(
            modifier = Modifier
                .size(100.dp)
                .clip(RoundedCornerShape(50.dp))
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = "Success",
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = "Wallet Created Successfully!",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(8.dp))

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "Wallet Details",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Name: ",
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        wallet.name,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    "Enabled Assets:",
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Bitcoin Coins with icons
                wallet.bitcoinCoins.forEach { coin ->
                    val networkSuffix =
                        if (coin.network != BitcoinNetwork.Mainnet) " (Testnet)" else ""
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(vertical = 4.dp)
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.bitcoin),
                            contentDescription = "Bitcoin",
                            modifier = Modifier.size(20.dp),
                            tint = Color.Unspecified
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Bitcoin$networkSuffix",
                            color = bitcoinLight,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                // Solana Coins with icons
                wallet.solanaCoins.forEach { coin ->
                    val networkSuffix =
                        if (coin.network != SolanaNetwork.Mainnet) " (Devnet)" else ""
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(vertical = 4.dp)
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.solana),
                            contentDescription = "Solana",
                            modifier = Modifier.size(20.dp),
                            tint = Color.Unspecified
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Solana$networkSuffix",
                            color = solanaLight,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    // Show SPL tokens if any
                    if (coin.splTokens.isNotEmpty()) {
                        coin.splTokens.take(3).forEach { token ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(
                                    start = 28.dp,
                                    top = 2.dp,
                                    bottom = 2.dp
                                )
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(16.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Token,
                                        contentDescription = token.symbol,
                                        modifier = Modifier.size(10.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "${token.symbol} (SPL)",
                                    color = MaterialTheme.colorScheme.primary,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                        if (coin.splTokens.size > 3) {
                            Text(
                                text = "  • +${coin.splTokens.size - 3} more",
                                modifier = Modifier.padding(start = 28.dp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }

                // EVM Tokens with icons
                wallet.evmTokens.forEach { token ->
                    val (color, iconRes) = when (token) {
                        is NativeETH -> Pair(ethereumLight, R.drawable.ethereum)
                        is USDCToken -> Pair(usdcLight, R.drawable.usdc)
                        is USDTToken -> Pair(usdtLight, R.drawable.tether)
                        else -> Pair(MaterialTheme.colorScheme.primary, null)
                    }

                    val networkSuffix =
                        if (token.network != EthereumNetwork.Mainnet) " (Sepolia)" else ""

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(vertical = 4.dp)
                    ) {
                        if (iconRes != null) {
                            Icon(
                                painter = painterResource(id = iconRes),
                                contentDescription = token.symbol,
                                modifier = Modifier.size(20.dp),
                                tint = Color.Unspecified
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(20.dp)
                                    .clip(CircleShape)
                                    .background(color.copy(alpha = 0.1f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Token,
                                    contentDescription = token.symbol,
                                    modifier = Modifier.size(12.dp),
                                    tint = color
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "${token.symbol}$networkSuffix",
                            color = color,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                // Primary Address (first available)
                val primaryAddress = wallet.evmTokens.firstOrNull()?.address
                    ?: wallet.bitcoinCoins.firstOrNull()?.address
                    ?: wallet.solanaCoins.firstOrNull()?.address

                if (primaryAddress != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider(
                        Modifier,
                        DividerDefaults.Thickness,
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.AccountBalanceWallet,
                            contentDescription = "Address",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Primary Address: ",
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = primaryAddress,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(start = 24.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(
                    Modifier,
                    DividerDefaults.Thickness,
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                )
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.AccountBalanceWallet,
                            contentDescription = "Total Assets",
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Total Assets:",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = "$totalAssets",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = onFinish,
            modifier = Modifier
                .fillMaxWidth(0.8f)
                .padding(bottom = 32.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
        ) {
            Icon(
                imageVector = Icons.Outlined.Dashboard,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("Go to Dashboard")
        }
    }
}

@Composable
fun Checkbox(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: CustomCheckboxColors = CustomCheckboxDefaults.colors()
) {
    val interactionSource = remember { MutableInteractionSource() }
    val backgroundColor by animateColorAsState(
        targetValue = when {
            !enabled -> colors.disabledBackgroundColor
            checked -> colors.checkedBackgroundColor
            else -> colors.uncheckedBackgroundColor
        },
        label = "checkbox_bg"
    )

    val borderColor by animateColorAsState(
        targetValue = when {
            !enabled -> colors.disabledBorderColor
            checked -> colors.checkedBorderColor
            else -> colors.uncheckedBorderColor
        },
        label = "checkbox_border"
    )

    val iconTintColor by animateColorAsState(
        targetValue = when {
            !enabled -> colors.disabledIconColor
            else -> colors.checkedIconColor
        },
        label = "checkbox_icon"
    )

    Box(
        modifier = modifier
            .size(24.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(backgroundColor)
            .border(
                width = 2.dp,
                color = borderColor,
                shape = RoundedCornerShape(6.dp)
            )
            .clickable(
                enabled = enabled,
                interactionSource = interactionSource,
                indication = ripple(bounded = true)
            ) { onCheckedChange(!checked) },
        contentAlignment = Alignment.Center
    ) {
        if (checked) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = "Checked",
                tint = iconTintColor,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

data class CustomCheckboxColors(
    val checkedBackgroundColor: Color,
    val uncheckedBackgroundColor: Color,
    val disabledBackgroundColor: Color,
    val checkedBorderColor: Color,
    val uncheckedBorderColor: Color,
    val disabledBorderColor: Color,
    val checkedIconColor: Color,
    val disabledIconColor: Color
)

object CustomCheckboxDefaults {
    @Composable
    fun colors(
        checkedBackgroundColor: Color = MaterialTheme.colorScheme.primary,
        uncheckedBackgroundColor: Color = Color.Transparent,
        disabledBackgroundColor: Color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        checkedBorderColor: Color = MaterialTheme.colorScheme.primary,
        uncheckedBorderColor: Color = MaterialTheme.colorScheme.outline,
        disabledBorderColor: Color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
        checkedIconColor: Color = MaterialTheme.colorScheme.onPrimary,
        disabledIconColor: Color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
    ) = CustomCheckboxColors(
        checkedBackgroundColor = checkedBackgroundColor,
        uncheckedBackgroundColor = uncheckedBackgroundColor,
        disabledBackgroundColor = disabledBackgroundColor,
        checkedBorderColor = checkedBorderColor,
        uncheckedBorderColor = uncheckedBorderColor,
        disabledBorderColor = disabledBorderColor,
        checkedIconColor = checkedIconColor,
        disabledIconColor = disabledIconColor
    )
}