package com.example.nexuswallet.feature.wallet.data.test

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import com.example.nexuswallet.feature.wallet.ui.TransactionItem
import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode
import java.text.SimpleDateFormat
import java.util.*

// Updated SepoliaTestScreen.kt
@Composable
fun SepoliaTestScreen(
    navController: NavController,
    viewModel: SepoliaTestViewModel = hiltViewModel()
) {
    // Use YOUR sending wallet address
    val fromAddress = "0xf35d0111a5a55d65b21b9a22f242095584c0c058"
    val balance by viewModel.balance.collectAsState()
    val loading by viewModel.loading.collectAsState()
    val error by viewModel.error.collectAsState()
    val sendStatus by viewModel.sendStatus.collectAsState()
    val txHash by viewModel.txHash.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.loadWalletData(fromAddress)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Back button
        IconButton(onClick = { navController.navigateUp() }) {
            Icon(Icons.Default.ArrowBack, "Back")
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Balance card
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Sepolia Testnet", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(16.dp))

                if (loading) {
                    CircularProgressIndicator()
                } else if (error != null) {
                    Text("Error: $error", color = MaterialTheme.colorScheme.error)
                } else {
                    Text(
                        "$balance ETH",
                        style = MaterialTheme.typography.displayMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "From: ${fromAddress.take(10)}...",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "To: 0xb4b0d6410aa23d3bb9c47672210cd70c0e04cb7d",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Status display
        sendStatus?.let { status ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (status.contains("✅")) MaterialTheme.colorScheme.primaryContainer
                    else if (status.contains("❌")) MaterialTheme.colorScheme.errorContainer
                    else MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Text(
                    text = status,
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        // Transaction Hash
        txHash?.let { hash ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Transaction Hash:", style = MaterialTheme.typography.labelMedium)
                    Text(hash, style = MaterialTheme.typography.bodySmall)
                    Text(
                        "View on Etherscan",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable {
                            // Open browser with: https://sepolia.etherscan.io/tx/$hash
                        }
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        // Send Button
        Button(
            onClick = { viewModel.sendTestTransaction() },
            modifier = Modifier.fillMaxWidth(),
            enabled = !loading && (balance ?: BigDecimal.ZERO) > BigDecimal("0.01")
        ) {
            Text("Send 0.01 ETH")
        }

        Spacer(modifier = Modifier.height(8.dp))

        Spacer(modifier = Modifier.height(8.dp))

        // Refresh Button
        Button(
            onClick = { viewModel.loadWalletData(fromAddress) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Refresh Balance")
        }
    }
}