package com.example.nexuswallet.feature.wallet.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import com.example.nexuswallet.NavigationViewModel
import com.example.nexuswallet.feature.wallet.domain.BitcoinWallet
import com.example.nexuswallet.feature.wallet.domain.CryptoWallet
import com.example.nexuswallet.feature.wallet.domain.EthereumWallet
import com.example.nexuswallet.feature.wallet.domain.MultiChainWallet
import com.example.nexuswallet.feature.wallet.domain.SolanaWallet
import com.example.nexuswallet.feature.wallet.domain.Transaction
import com.example.nexuswallet.feature.wallet.domain.TransactionStatus
import com.example.nexuswallet.feature.wallet.domain.WalletBalance
import com.example.nexuswallet.formatDate
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApiDebugScreen(
    navController: NavController,
    walletId: String
) {
    val viewModel: BlockchainViewModel = hiltViewModel()
    val walletViewModel: WalletDetailViewModel = hiltViewModel()
    val wallet by walletViewModel.wallet.collectAsState()

//    val apiTestResults by viewModel.apiTestResults.collectAsState()
//    val isLoading by viewModel.isLoading.collectAsState()
//    val rawData by viewModel.rawData.collectAsState()
//    val selectedApi by viewModel.selectedApi.collectAsState()
//    val apiStatus by viewModel.apiStatus.collectAsState()
//    val lastUpdated by viewModel.lastUpdated.collectAsState()
//
//    // Load data when wallet changes
//    LaunchedEffect(wallet) {
//        wallet?.let {
//            viewModel.refreshRawData(it)
//        }
//    }
//
//    // Also load when selected API changes
//    LaunchedEffect(selectedApi) {
//        wallet?.let {
//            viewModel.refreshRawData(it)
//        }
//    }

//    Scaffold(
//        topBar = {
//            TopAppBar(
//                title = { Text("API Debug") },
//                navigationIcon = {
//                    IconButton(onClick = { navController.navigateUp() }) {
//                        Icon(Icons.Default.ArrowBack, "Back")
//                    }
//                }
//            )
//        }
//    ) { padding ->
//        Column(
//            modifier = Modifier
//                .fillMaxSize()
//                .padding(padding)
//                .verticalScroll(rememberScrollState())
//        ) {
//            ApiStatusCard(
//                apiStatus = apiStatus,
//                lastUpdated = lastUpdated
//            )
//
//            // API Selector
//            Card(
//                modifier = Modifier
//                    .fillMaxWidth()
//                    .padding(16.dp)
//            ) {
//                Column(modifier = Modifier.padding(16.dp)) {
//                    Text(
//                        text = "Select API to Test",
//                        style = MaterialTheme.typography.titleMedium,
//                        fontWeight = FontWeight.Bold
//                    )
//
//                    Spacer(modifier = Modifier.height(12.dp))
//
//                    Row(
//                        modifier = Modifier.fillMaxWidth(),
//                        horizontalArrangement = Arrangement.SpaceEvenly
//                    ) {
//                        ApiSelectorButton(
//                            label = "Etherscan",
//                            isSelected = selectedApi == "etherscan",
//                            onClick = { viewModel.selectApi("etherscan") }
//                        )
//                        ApiSelectorButton(
//                            label = "Blockstream",
//                            isSelected = selectedApi == "blockstream",
//                            onClick = { viewModel.selectApi("blockstream") }
//                        )
//                        ApiSelectorButton(
//                            label = "Covalent",
//                            isSelected = selectedApi == "covalent",
//                            onClick = { viewModel.selectApi("covalent") }
//                        )
//                    }
//                }
//            }
//
//            // API Test Results (show if we have results)
//            if (apiTestResults.isNotEmpty()) {
//                Card(
//                    modifier = Modifier
//                        .fillMaxWidth()
//                        .padding(16.dp)
//                ) {
//                    Column(modifier = Modifier.padding(16.dp)) {
//                        Text(
//                            text = "API Test Results",
//                            style = MaterialTheme.typography.titleMedium,
//                            fontWeight = FontWeight.Bold
//                        )
//
//                        Spacer(modifier = Modifier.height(12.dp))
//
//                        apiTestResults.forEach { result ->
//                            ApiTestResultItem(result = result)
//                            if (apiTestResults.indexOf(result) < apiTestResults.size - 1) {
//                                Divider(modifier = Modifier.padding(vertical = 8.dp))
//                            }
//                        }
//                    }
//                }
//            }
//
//            // Raw Data Display
//            Card(
//                modifier = Modifier
//                    .fillMaxWidth()
//                    .padding(16.dp)
//            ) {
//                Column(modifier = Modifier.padding(16.dp)) {
//                    Row(
//                        modifier = Modifier.fillMaxWidth(),
//                        horizontalArrangement = Arrangement.SpaceBetween,
//                        verticalAlignment = Alignment.CenterVertically
//                    ) {
//                        Text(
//                            text = "Raw API Response",
//                            style = MaterialTheme.typography.titleMedium,
//                            fontWeight = FontWeight.Bold
//                        )
//
//                        Button(
//                            onClick = {
//                                wallet?.let {
//                                    viewModel.refreshRawData(it)
//                                }
//                            },
//                            shape = RoundedCornerShape(8.dp),
//                            enabled = !isLoading && wallet != null
//                        ) {
//                            if (isLoading) {
//                                CircularProgressIndicator(
//                                    modifier = Modifier.size(16.dp),
//                                    strokeWidth = 2.dp
//                                )
//                            } else {
//                                Icon(Icons.Default.Refresh, "Refresh")
//                            }
//                        }
//                    }
//
//                    Spacer(modifier = Modifier.height(12.dp))
//
//                    if (rawData != null) {
//                        val scrollState = rememberScrollState()
//                        Box(
//                            modifier = Modifier
//                                .fillMaxWidth()
//                                .height(300.dp)
//                                .background(Color.Black.copy(alpha = 0.05f))
//                                .clip(RoundedCornerShape(8.dp))
//                                .padding(12.dp)
//                        ) {
//                            Text(
//                                text = rawData!!,
//                                style = MaterialTheme.typography.bodySmall,
//                                fontFamily = FontFamily.Monospace,
//                                modifier = Modifier
//                                    .fillMaxSize()
//                                    .verticalScroll(scrollState)
//                            )
//                        }
//
//                        // Show data source
//                        Spacer(modifier = Modifier.height(8.dp))
//                        Text(
//                            text = "Source: $selectedApi API • ${wallet?.getDisplayAddress() ?: ""}",
//                            style = MaterialTheme.typography.labelSmall,
//                            color = MaterialTheme.colorScheme.onSurfaceVariant
//                        )
//                    } else {
//                        Box(
//                            modifier = Modifier
//                                .fillMaxWidth()
//                                .height(300.dp),
//                            contentAlignment = Alignment.Center
//                        ) {
//                            when {
//                                isLoading -> CircularProgressIndicator()
//                                wallet == null -> Column(
//                                    horizontalAlignment = Alignment.CenterHorizontally
//                                ) {
//                                    Icon(
//                                        imageVector = Icons.Default.Warning,
//                                        contentDescription = "No Wallet",
//                                        modifier = Modifier.size(48.dp),
//                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
//                                    )
//                                    Spacer(modifier = Modifier.height(8.dp))
//                                    Text(
//                                        "No wallet loaded",
//                                        color = MaterialTheme.colorScheme.onSurfaceVariant
//                                    )
//                                }
//                                else -> Column(
//                                    horizontalAlignment = Alignment.CenterHorizontally
//                                ) {
//                                    Icon(
//                                        imageVector = Icons.Default.Download,
//                                        contentDescription = "Load Data",
//                                        modifier = Modifier.size(48.dp),
//                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
//                                    )
//                                    Spacer(modifier = Modifier.height(8.dp))
//                                    Text(
//                                        "Select an API to load data",
//                                        color = MaterialTheme.colorScheme.onSurfaceVariant
//                                    )
//                                }
//                            }
//                        }
//                    }
//                }
//            }
//
//            // Test Controls
//            Card(
//                modifier = Modifier
//                    .fillMaxWidth()
//                    .padding(16.dp)
//            ) {
//                Column(modifier = Modifier.padding(16.dp)) {
//                    Text(
//                        text = "API Connectivity Tests",
//                        style = MaterialTheme.typography.titleMedium,
//                        fontWeight = FontWeight.Bold
//                    )
//
//                    Spacer(modifier = Modifier.height(12.dp))
//
//                    Spacer(modifier = Modifier.height(8.dp))
//
//                    Text(
//                        text = "Tests all blockchain APIs with known addresses",
//                        style = MaterialTheme.typography.bodySmall,
//                        color = MaterialTheme.colorScheme.onSurfaceVariant
//                    )
//                }
//            }
//
//            Spacer(modifier = Modifier.height(16.dp))
//        }
//    }
}

@Composable
fun ApiStatusCard(
    apiStatus: ApiStatus,
    lastUpdated: Date?
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = when (apiStatus) {
                ApiStatus.CONNECTED -> Color(0xFF4CAF50).copy(alpha = 0.1f)
                ApiStatus.CONNECTING -> Color(0xFFFF9800).copy(alpha = 0.1f)
                ApiStatus.ERROR -> Color(0xFFF44336).copy(alpha = 0.1f)
                ApiStatus.DISCONNECTED -> Color(0xFF9E9E9E).copy(alpha = 0.1f)
            }
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Status indicator
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(
                        when (apiStatus) {
                            ApiStatus.CONNECTED -> Color.Green
                            ApiStatus.CONNECTING -> Color.Yellow
                            ApiStatus.ERROR -> Color.Red
                            ApiStatus.DISCONNECTED -> Color.Gray
                        }
                    )
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = when (apiStatus) {
                        ApiStatus.CONNECTED -> "Connected to Blockchain APIs"
                        ApiStatus.CONNECTING -> "Connecting..."
                        ApiStatus.ERROR -> "Connection Issues"
                        ApiStatus.DISCONNECTED -> "Offline Mode"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = when (apiStatus) {
                        ApiStatus.CONNECTED -> "All systems operational"
                        ApiStatus.CONNECTING -> "Establishing connections..."
                        ApiStatus.ERROR -> "Some APIs may be unavailable"
                        ApiStatus.DISCONNECTED -> "Using demo data"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (lastUpdated != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Last updated: ${SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(lastUpdated)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // API badges
            Row {
                Chip(
                    label = "Etherscan",
                    isActive = apiStatus == ApiStatus.CONNECTED,
                    modifier = Modifier.padding(end = 4.dp)
                )
                Chip(
                    label = "Blockstream",
                    isActive = apiStatus == ApiStatus.CONNECTED,
                    modifier = Modifier.padding(end = 4.dp)
                )
                Chip(
                    label = "Covalent",
                    isActive = apiStatus == ApiStatus.CONNECTED
                )
            }
        }
    }
}

@Composable
fun Chip(
    label: String,
    isActive: Boolean,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (isActive) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                else MaterialTheme.colorScheme.surfaceVariant
            )
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (isActive) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun ApiTestResultItem(result: ApiTestResult) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (result.isConnected) Icons.Default.CheckCircle else Icons.Default.Error,
            contentDescription = "Status",
            tint = if (result.isConnected) Color.Green else Color.Red,
            modifier = Modifier.size(20.dp)
        )

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = result.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )

            Text(
                text = result.message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }

        Column(
            horizontalAlignment = Alignment.End
        ) {
            Text(
                text = result.responseTime,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Text(
                text = result.lastBlock,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun ApiSelectorButton(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Text(label)
    }
}

fun CryptoWallet.getDisplayAddress(): String {
    val address = when (this) {
        is BitcoinWallet -> this.address
        is EthereumWallet -> this.address
        is MultiChainWallet -> this.ethereumWallet?.address ?: this.bitcoinWallet?.address ?: ""
        is SolanaWallet -> this.address
        else -> ""
    }
    return if (address.length > 10) {
        "${address.take(6)}...${address.takeLast(4)}"
    } else {
        address
    }
}