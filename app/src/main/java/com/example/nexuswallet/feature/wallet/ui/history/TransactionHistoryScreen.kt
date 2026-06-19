package com.example.nexuswallet.feature.wallet.ui.history

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.nexuswallet.R
import com.example.nexuswallet.feature.wallet.domain.model.Coin
import com.example.nexuswallet.feature.wallet.ui.common.TransactionItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionHistoryScreen(
    walletId: String,
    coin: Coin? = null,
    onNavigateUp: () -> Unit,
    onTransactionClick: (String, Coin) -> Unit,
    viewModel: TransactionHistoryViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(walletId, coin) {
        viewModel.loadTransactions(walletId, coin)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (coin != null) {
                            stringResource(R.string.transaction_history_for, coin.name)
                        } else {
                            stringResource(R.string.all_transactions)
                        }
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                }
            )
        }
    ) { padding ->
        if (uiState.isLoading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (uiState.transactions.isEmpty()) {
             Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.no_transactions))
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(uiState.transactions) { transaction ->
                    TransactionItem(
                        transaction = transaction,
                        modifier = Modifier.clickable {
                            onTransactionClick(transaction.id, transaction.coin)
                        }
                    )
                }
            }
        }
    }
}
