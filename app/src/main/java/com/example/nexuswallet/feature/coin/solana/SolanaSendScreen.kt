package com.example.nexuswallet.feature.coin.solana

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.math.BigDecimal


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SolanaSendScreen(
    walletId: String,
    onBack: () -> Unit,
    onSuccess: (hash: String) -> Unit
) {
    val viewModel: SolanaSendViewModel = hiltViewModel()

    LaunchedEffect(Unit) {
        viewModel.init(walletId)
    }

    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Send SOL") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                }
            )
        },
        bottomBar = {
//            if (state.wallet != null) {
//                BottomSendBar(
//                    enabled = state.toAddress.isNotBlank() &&
//                            state.amountValue > BigDecimal.ZERO &&
//                            !state.isLoading,
//                    isLoading = state.isLoading,
//                    onClick = { viewModel.send(onSuccess) }
//                )
//            }
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
            // Wallet Info
//            state.wallet?.let { wallet ->
//                Card {
//                    Column(
//                        modifier = Modifier.padding(16.dp),
//                        verticalArrangement = Arrangement.spacedBy(8.dp)
//                    ) {
//                        Text("From:", style = MaterialTheme.typography.labelMedium)
//                        Text(
//                            wallet.address.take(8) + "..." + wallet.address.takeLast(8),
//                            style = MaterialTheme.typography.bodyMedium
//                        )
//                        Text("Wallet: ${wallet.name}", style = MaterialTheme.typography.bodySmall)
//                    }
//                }
//            }

            Button(
                onClick = {
                    viewModel.requestAirdrop()
                }
            ) {
                Text("Get Test SOL")
            }
            // To Address
//            OutlinedTextField(
//                value = state.toAddress,
//                onValueChange = viewModel::updateAddress,
//                label = { Text("To Address") },
//                placeholder = { Text("Enter Solana address") },
//                modifier = Modifier.fillMaxWidth(),
//                singleLine = true,
//                isError = state.toAddress.isNotBlank() && state.error?.contains("address") == true
//            )

            // Amount
            OutlinedTextField(
                value = state.amount,
                onValueChange = viewModel::updateAmount,
                label = { Text("Amount") },
                placeholder = { Text("0.0") },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                suffix = { Text("SOL") },
                isError = state.amount.isNotBlank() && state.error?.contains("amount") == true
            )

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
                            Icon(Icons.Default.Close, "Close", tint = MaterialTheme.colorScheme.onErrorContainer)
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
fun LoadingDialog() {
    CircularProgressIndicator()
}

@Composable
private fun BottomSendBar(
    enabled: Boolean,
    isLoading: Boolean,
    onClick: () -> Unit
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
                    Text("Send Transaction")
                }
            }
        }
    }
}