package com.example.nexuswallet.feature.settings.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CurrencyExchange
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    navController: NavController
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Security Settings Option
            SettingsItem(
                title = "Security",
                description = "PIN, Biometric, Encryption",
                icon = Icons.Default.Security,
                onClick = { navController.navigate("securitySettings") }
            )

            Divider()

            // Currency Settings
            SettingsItem(
                title = "Currency",
                description = "Display currency preferences",
                icon = Icons.Default.CurrencyExchange,
                onClick = { /* Navigate to currency settings */ }
            )

            Divider()

            // Network Settings
            SettingsItem(
                title = "Networks",
                description = "Configure blockchain networks",
                icon = Icons.Default.NetworkCheck,
                onClick = { /* Navigate to network settings */ }
            )

            Divider()

            // About
            SettingsItem(
                title = "About",
                description = "App version and information",
                icon = Icons.Default.Info,
                onClick = { /* Navigate to about screen */ }
            )
        }
    }
}

@Composable
fun SettingsItem(
    title: String,
    description: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(0.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                modifier = Modifier.size(24.dp)
            )

            Spacer(modifier = Modifier.width(16.dp))

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = "Navigate",
                modifier = Modifier.size(20.dp)
            )
        }
    }
}