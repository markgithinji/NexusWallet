package com.example.nexuswallet.feature.wallet.data.test.kettest

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController

@Composable
fun KeyStorageTestScreen(
    navController: NavController,
    viewModel: KeyTestViewModel = hiltViewModel()
) {
    val testResult by viewModel.testResult.collectAsState()
    val loading by viewModel.loading.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Back button
        Row {
            IconButton(onClick = { navController.navigateUp() }) {
                Icon(Icons.Default.ArrowBack, "Back")
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text("Key Storage Test", style = MaterialTheme.typography.titleLarge)
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Test buttons
        Button(
            onClick = { viewModel.runKeyStorageTest() },
            modifier = Modifier.fillMaxWidth(),
            enabled = !loading
        ) {
            Text("Test Key Storage (Create New)")
        }

        Spacer(modifier = Modifier.height(12.dp))

        Button(
            onClick = { viewModel.runRealWalletTest() },
            modifier = Modifier.fillMaxWidth(),
            enabled = !loading
        ) {
            Text("Test Real Wallet (test3)")
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Results
        if (loading) {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }

        testResult?.let { result ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (result.contains("✅")) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Text(
                    text = result,
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        // Debug info
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            "Test wallet ID: eth_1770129685786",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}