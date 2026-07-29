package com.example.nexuswallet.feature.wallet.ui.importwallet

import androidx.activity.compose.LocalActivity
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.nexuswallet.R
import com.example.nexuswallet.feature.core.ui.NexusTextField
import com.example.nexuswallet.feature.ethereum.domain.model.EVMTokenType
import com.example.nexuswallet.feature.wallet.domain.model.BitcoinNetwork
import com.example.nexuswallet.feature.wallet.domain.model.EthereumNetwork
import com.example.nexuswallet.feature.wallet.domain.model.Network
import com.example.nexuswallet.feature.wallet.domain.model.SolanaNetwork
import com.example.nexuswallet.feature.wallet.ui.common.FullScreenLoading
import com.example.nexuswallet.feature.wallet.ui.walletcreation.NetworkToggleCard
import com.example.nexuswallet.feature.wallet.ui.walletcreation.TokenToggleCard
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
                    viewModel.completeImportAfterBiometric()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    // Handle error if needed
                }
            }
        )
    }

    val promptInfo = remember {
        BiometricPrompt.PromptInfo.Builder()
            .setTitle(context.getString(R.string.biometric_authentication))
            .setSubtitle(context.getString(R.string.secure_your_wallet))
            .setNegativeButtonText(context.getString(R.string.cancel))
            .build()
    }

    LaunchedEffect(authRequest) {
        if (authRequest != null) {
            biometricPrompt?.authenticate(promptInfo)
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
                        fontWeight = FontWeight.SemiBold
                    ) 
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (currentStep == 0) onNavigateUp() else viewModel.previousStep()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                    }
                }
            )
        }
    ) { padding ->
        if (uiState is WalletCreationUiState.Loading || uiState is WalletCreationUiState.WalletCreated) {
            FullScreenLoading(message = stringResource(R.string.creating_wallet))
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (currentStep == 0) {
                    ImportNetworkSelectionStep(
                        selectedNetworks = selectedNetworks,
                        selectedTokens = selectedTokens,
                        onNetworkToggle = viewModel::toggleNetwork,
                        onTokenToggle = viewModel::toggleToken,
                        onNext = viewModel::nextStep
                    )
                } else {
                    ImportMnemonicStep(
                        words = words,
                        walletName = walletName,
                        uiState = uiState,
                        onWordChange = viewModel::updateWord,
                        onNameChange = viewModel::setWalletName,
                        onImport = viewModel::importWallet
                    )
                }
            }
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
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.select_networks_to_import),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        // Bitcoin
        NetworkToggleCard(
            iconRes = R.drawable.bitcoin,
            color = bitcoinLight,
            network = BitcoinNetwork.Mainnet,
            coinName = stringResource(R.string.bitcoin_name),
            coinSymbol = stringResource(R.string.bitcoin_symbol),
            isSelected = selectedNetworks.contains(BitcoinNetwork.Mainnet),
            onSelectedChange = { onNetworkToggle(BitcoinNetwork.Mainnet, it) }
        )
        Spacer(Modifier.height(8.dp))
        NetworkToggleCard(
            iconRes = R.drawable.bitcoin,
            color = bitcoinLight.copy(alpha = 0.7f),
            network = BitcoinNetwork.Testnet,
            coinName = stringResource(R.string.bitcoin_name),
            coinSymbol = stringResource(R.string.bitcoin_symbol),
            isSelected = selectedNetworks.contains(BitcoinNetwork.Testnet),
            onSelectedChange = { onNetworkToggle(BitcoinNetwork.Testnet, it) }
        )
        Spacer(Modifier.height(8.dp))
        
        // Ethereum
        NetworkToggleCard(
            iconRes = R.drawable.ethereum,
            color = ethereumLight,
            network = EthereumNetwork.Mainnet,
            coinName = stringResource(R.string.ethereum_name),
            coinSymbol = stringResource(R.string.ethereum_symbol),
            isSelected = selectedNetworks.contains(EthereumNetwork.Mainnet),
            onSelectedChange = { onNetworkToggle(EthereumNetwork.Mainnet, it) }
        )
        Spacer(Modifier.height(8.dp))
        NetworkToggleCard(
            iconRes = R.drawable.ethereum,
            color = ethereumLight.copy(alpha = 0.7f),
            network = EthereumNetwork.Sepolia,
            coinName = stringResource(R.string.ethereum_name),
            coinSymbol = stringResource(R.string.ethereum_symbol),
            isSelected = selectedNetworks.contains(EthereumNetwork.Sepolia),
            onSelectedChange = { onNetworkToggle(EthereumNetwork.Sepolia, it) }
        )
        Spacer(Modifier.height(8.dp))

        // Solana
        NetworkToggleCard(
            iconRes = R.drawable.solana,
            color = solanaLight,
            network = SolanaNetwork.Mainnet,
            coinName = stringResource(R.string.solana_name),
            coinSymbol = stringResource(R.string.solana_symbol),
            isSelected = selectedNetworks.contains(SolanaNetwork.Mainnet),
            onSelectedChange = { onNetworkToggle(SolanaNetwork.Mainnet, it) }
        )
        Spacer(Modifier.height(8.dp))
        NetworkToggleCard(
            iconRes = R.drawable.solana,
            color = solanaLight.copy(alpha = 0.7f),
            network = SolanaNetwork.Devnet,
            coinName = stringResource(R.string.solana_name),
            coinSymbol = stringResource(R.string.solana_symbol),
            isSelected = selectedNetworks.contains(SolanaNetwork.Devnet),
            onSelectedChange = { onNetworkToggle(SolanaNetwork.Devnet, it) }
        )

        if (selectedNetworks.any { it is EthereumNetwork }) {
            Spacer(Modifier.height(24.dp))
            Text(
                text = stringResource(R.string.tokens),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            
            // USDC
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
            Spacer(Modifier.height(8.dp))
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
            Spacer(Modifier.height(8.dp))
            
            // USDT
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
            Spacer(Modifier.height(8.dp))
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

        Spacer(Modifier.height(32.dp))
        Button(
            onClick = onNext,
            modifier = Modifier.fillMaxWidth(),
            enabled = selectedNetworks.isNotEmpty()
        ) {
            Text(stringResource(R.string.continue_button))
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
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.import_wallet_desc),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        NexusTextField(
            value = walletName,
            onValueChange = onNameChange,
            label = stringResource(R.string.wallet_name_label),
            placeholder = stringResource(R.string.wallet_name_placeholder),
            modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)
        )

        Text(
            text = stringResource(R.string.recovery_phrase_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        // Grid for 12 words
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            for (row in 0 until 6) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
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
            }
        }

        if (uiState is WalletCreationUiState.Error) {
            Text(
                text = uiState.message,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 16.dp)
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = onImport,
            modifier = Modifier.fillMaxWidth(),
            enabled = words.all { it.isNotBlank() }
        ) {
            Text(stringResource(R.string.import_button))
        }
    }
}
