package com.example.nexuswallet.feature.coin.usdc


import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeveloperBoard
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.nexuswallet.feature.wallet.domain.EthereumNetwork
import java.math.BigDecimal

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterialApi::class)
@Composable
fun USDCSendScreen(
    walletId: String,
    onBack: () -> Unit,
    onSuccess: (hash: String) -> Unit
) {
    val viewModel: USDCSendViewModel = hiltViewModel()

    LaunchedEffect(Unit) {
        viewModel.init(walletId)
    }

    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Send USDC${if (state.network == EthereumNetwork.SEPOLIA) " (Testnet)" else ""}"
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                }
            )
        },
        bottomBar = {
            if (state.wallet != null) {
                BottomSendBar(
                    enabled = state.toAddress.isNotBlank() &&
                            state.amountValue > BigDecimal.ZERO &&
                            !state.isLoading &&
                            state.hasSufficientBalance &&
                            state.hasSufficientGas,
                    isLoading = state.isLoading,
                    onClick = { viewModel.send(onSuccess) },
                    label = "Send USDC"
                )
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            // Debug/Faucet Button
            Button(
                onClick = { viewModel.debug() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.DeveloperBoard, "Debug")
                Spacer(modifier = Modifier.width(8.dp))
                Text("Debug USDC")
            }

            // Testnet Faucet Button
            if (state.network == EthereumNetwork.SEPOLIA) {
                Button(
                    onClick = {
                        viewModel.getTestnetUSDC()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.WaterDrop, "Faucet")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Get Testnet USDC")
                }
            }

            // Network Info
            Card {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            "Network:",
                            style = MaterialTheme.typography.labelMedium
                        )
                        SuggestionChip(
                            onClick = {},
                            label = {
                                Text(
                                    when (state.network) {
                                        EthereumNetwork.SEPOLIA -> "SEPOLIA TESTNET"
                                        EthereumNetwork.MAINNET -> "MAINNET"
                                        else -> state.network.name
                                    }
                                )
                            },
                            colors = SuggestionChipDefaults.suggestionChipColors(
                                containerColor = when (state.network) {
                                    EthereumNetwork.SEPOLIA -> MaterialTheme.colorScheme.tertiaryContainer
                                    EthereumNetwork.MAINNET -> MaterialTheme.colorScheme.errorContainer
                                    else -> MaterialTheme.colorScheme.secondaryContainer
                                }
                            )
                        )
                    }

                    // Contract Address
                    Text(
                        "USDC Contract:",
                        style = MaterialTheme.typography.labelMedium
                    )
                    Text(
                        state.contractAddress?.take(10) + "..." + state.contractAddress?.takeLast(8),
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            // Wallet Info
            state.wallet?.let { wallet ->
                Card {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("From:", style = MaterialTheme.typography.labelMedium)
                        Text(
                            wallet.address.take(8) + "..." + wallet.address.takeLast(8),
                            style = MaterialTheme.typography.bodyMedium,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            "Wallet: ${wallet.name}",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            // To Address
            OutlinedTextField(
                value = state.toAddress,
                onValueChange = viewModel::updateAddress,
                label = { Text("To Address") },
                placeholder = { Text("Enter Ethereum address (0x...)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                isError = state.toAddress.isNotBlank() && !state.isValidAddress
            )

            // Amount
            OutlinedTextField(
                value = state.amount,
                onValueChange = viewModel::updateAmount,
                label = { Text("Amount") },
                placeholder = { Text("0.0") },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                suffix = { Text("USDC") },
                isError = state.amount.isNotBlank() && !state.hasSufficientBalance
            )

            // Balance and Gas Info
            Card {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // USDC Balance
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("USDC Balance:", style = MaterialTheme.typography.labelMedium)
                        Text(
                            "${state.usdcBalance} USDC",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = if (state.hasSufficientBalance) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.error
                        )
                    }

                    // ETH Balance for Gas
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("ETH for Gas:", style = MaterialTheme.typography.labelMedium)
                        Text(
                            "${state.ethBalance} ETH",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = if (state.hasSufficientGas) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.error
                        )
                    }

                    // Estimated Gas
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Estimated Gas:", style = MaterialTheme.typography.labelMedium)
                        Text(
                            "${state.estimatedGas} ETH",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Error messages
                    if (!state.hasSufficientBalance) {
                        Text(
                            "⚠️ Insufficient USDC balance. You have ${state.usdcBalance} USDC",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    if (!state.hasSufficientGas) {
                        Text(
                            "⚠️ Insufficient ETH for gas fees. You need ${state.estimatedGas} ETH but have ${state.ethBalance} ETH",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }

                    Text(
                        "Note: Gas fees are paid in ETH, not USDC",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontStyle = FontStyle.Italic
                    )
                }
            }

            // Info Message
            state.info?.let { info ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            info,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = { viewModel.clearInfo() },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                Icons.Default.Close,
                                "Close",
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
            }

            // Error Message
            state.error?.let { error ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            error,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = { viewModel.clearError() },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                Icons.Default.Close,
                                "Close",
                                tint = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }
            }
        }
    }

    if (state.isLoading) {
        LoadingDialog()
    }
}

@Composable
private fun LoadingDialog() {
    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                CircularProgressIndicator()
                Text("Sending USDC...", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Please wait while we process your transaction",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun BottomSendBar(
    enabled: Boolean,
    isLoading: Boolean,
    onClick: () -> Unit,
    label: String = "Send"
) {
    Surface(
        tonalElevation = 8.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            Button(
                onClick = onClick,
                enabled = enabled,
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(vertical = 16.dp)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(
                        Icons.Default.Send,
                        "Send",
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(label)
                }
            }
        }
    }
}