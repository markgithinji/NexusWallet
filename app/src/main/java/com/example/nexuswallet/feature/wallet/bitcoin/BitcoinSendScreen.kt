package com.example.nexuswallet.feature.wallet.bitcoin

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Button
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeveloperBoard
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.nexuswallet.feature.wallet.solana.LoadingDialog
import com.example.nexuswallet.feature.wallet.domain.BitcoinNetwork
import java.math.BigDecimal


@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterialApi::class)
@Composable
fun BitcoinSendScreen(
    walletId: String,
    onBack: () -> Unit,
    onSuccess: (hash: String) -> Unit
) {
    val viewModel: BitcoinSendViewModel = hiltViewModel()

    LaunchedEffect(Unit) {
        viewModel.init(walletId)
    }

    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Send BTC${if (state.network == BitcoinNetwork.TESTNET) " (Testnet)" else ""}"
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
                            !state.isLoading,
                    isLoading = state.isLoading,
                    onClick = { viewModel.send(onSuccess) },
                    label = "Send BTC"
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

            Button(
                onClick = { viewModel.debug() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.DeveloperBoard, "Faucet")
                Spacer(modifier = Modifier.width(8.dp))
                Text("Debug")
            }

            // Network Warning for Mainnet
            if (state.network == BitcoinNetwork.MAINNET) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Warning,
                                contentDescription = "Warning",
                                tint = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "⚠️ REAL BITCOIN",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            "You are sending REAL Bitcoin with monetary value!",
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
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
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                "Wallet: ${wallet.name}",
                                style = MaterialTheme.typography.bodySmall
                            )
                            SuggestionChip(
                                onClick = {},
                                label = {
                                    Text(
                                        when (wallet.network) {
                                            BitcoinNetwork.MAINNET -> "MAINNET"
                                            BitcoinNetwork.TESTNET -> "TESTNET"
                                            BitcoinNetwork.REGTEST -> "REGTEST"
                                        }
                                    )
                                },
                                colors = SuggestionChipDefaults.suggestionChipColors(
                                    containerColor = when (wallet.network) {
                                        BitcoinNetwork.MAINNET -> MaterialTheme.colorScheme.errorContainer
                                        BitcoinNetwork.TESTNET -> MaterialTheme.colorScheme.tertiaryContainer
                                        BitcoinNetwork.REGTEST -> MaterialTheme.colorScheme.secondaryContainer
                                    }
                                )
                            )
                        }
                    }
                }
            }

            // Testnet Faucet Button
            if (state.network == BitcoinNetwork.TESTNET) {
                Button(
                    onClick = {
                        viewModel.getTestnetCoins()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.WaterDrop, "Faucet")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Get Testnet BTC")
                }
            }

            // To Address
            OutlinedTextField(
                value = state.toAddress,
                onValueChange = viewModel::updateAddress,
                label = { Text("To Address") },
                placeholder = { Text("Enter Bitcoin address") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                isError = state.toAddress.isNotBlank() && state.error?.contains("address") == true
            )

            // Amount
            OutlinedTextField(
                value = state.amount,
                onValueChange = viewModel::updateAmount,
                label = { Text("Amount") },
                placeholder = { Text("0.0") },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                suffix = { Text("BTC") },
                isError = state.amount.isNotBlank() && state.error?.contains("amount") == true
            )

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