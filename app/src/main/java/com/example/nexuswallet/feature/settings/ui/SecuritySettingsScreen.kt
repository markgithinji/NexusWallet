package com.example.nexuswallet.feature.settings.ui

import android.util.Log
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Error
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import com.example.nexuswallet.feature.authentication.ui.PinSetupDialog
import kotlinx.coroutines.launch
import com.example.nexuswallet.feature.coin.Result

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SecuritySettingsScreen(
    navController: NavController,
    viewModel: SecuritySettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val operationState by viewModel.operationState.collectAsState()
    val showPinSetupDialog by viewModel.showPinSetupDialog.collectAsState()
    val showPinChangeDialog by viewModel.showPinChangeDialog.collectAsState()
    val pinSetupError by viewModel.pinSetupError.collectAsState()

    val coroutineScope = rememberCoroutineScope()

    // Show PIN Setup Dialog
    if (showPinSetupDialog || showPinChangeDialog) {
        PinSetupDialog(
            showDialog = true,
            title = if (showPinSetupDialog) "Setup PIN" else "Change PIN",
            subtitle = "Enter a 4-6 digit PIN",
            onPinSet = { pin ->
                coroutineScope.launch {
                    viewModel.setNewPin(pin)
                }
            },
            onDismiss = { viewModel.cancelPinSetup() },
            errorMessage = pinSetupError
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Security,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Security Settings",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.Black
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.White,
                    scrolledContainerColor = Color.White
                )
            )
        },
        containerColor = Color(0xFFF5F5F7)
    ) { padding ->
        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            when (val state = uiState) {
                is Result.Loading -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = Color(0xFF3B82F6))
                    }
                }

                is Result.Error -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding)
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Error,
                            contentDescription = "Error",
                            modifier = Modifier.size(48.dp),
                            tint = Color(0xFFEF4444)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = state.message,
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = { viewModel.retry() },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF3B82F6)
                            )
                        ) {
                            Text("Retry", color = Color.White)
                        }
                    }
                }

                is Result.Success -> {
                    val securityState = state.data

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding)
                            .verticalScroll(rememberScrollState())
                    ) {
                        // Security Status Card
                        SecurityStatusCard(
                            isBiometricEnabled = securityState.isBiometricEnabled,
                            isPinSet = securityState.isPinSet,
                            isBackupAvailable = false // TODO: Add backup status to UI state
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // PIN Section
                        SecuritySection(
                            title = "PIN Protection",
                            description = "Add an extra layer of security with a PIN"
                        ) {
                            if (securityState.isPinSet) {
                                Button(
                                    onClick = { viewModel.changePin() },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFF3B82F6)
                                    )
                                ) {
                                    Text(
                                        text = "Change PIN",
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Medium,
                                        color = Color.White
                                    )
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                Button(
                                    onClick = { viewModel.removePin() },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFFFEF2F2)
                                    ),
                                    border = BorderStroke(1.dp, Color(0xFFFEE2E2))
                                ) {
                                    Text(
                                        text = "Remove PIN",
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Medium,
                                        color = Color(0xFFEF4444)
                                    )
                                }
                            } else {
                                Button(
                                    onClick = { viewModel.setupPin() },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFF3B82F6)
                                    )
                                ) {
                                    Text(
                                        text = "Setup PIN",
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Medium,
                                        color = Color.White
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Biometric Section
                        SecuritySection(
                            title = "Biometric Authentication",
                            description = "Use fingerprint or face recognition"
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(
                                        text = if (securityState.isBiometricEnabled) "Enabled" else "Disabled",
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Medium,
                                        color = if (securityState.isBiometricEnabled) Color(0xFF10B981) else Color(0xFF6B7280)
                                    )
                                }

                                Switch(
                                    checked = securityState.isBiometricEnabled,
                                    onCheckedChange = { viewModel.setBiometricEnabled(it) },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = Color.White,
                                        checkedTrackColor = Color(0xFF3B82F6),
                                        uncheckedThumbColor = Color.White,
                                        uncheckedTrackColor = Color(0xFFE5E7EB)
                                    )
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Backup Section
                        SecuritySection(
                            title = "Encrypted Backup",
                            description = "Create and restore encrypted backups"
                        ) {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = { viewModel.createBackup() },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFF3B82F6)
                                    )
                                ) {
                                    Text(
                                        text = "Create Encrypted Backup",
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Medium,
                                        color = Color.White
                                    )
                                }

                                Button(
                                    onClick = { viewModel.restoreBackup() },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFF3B82F6)
                                    )
                                ) {
                                    Text(
                                        text = "Restore from Backup",
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Medium,
                                        color = Color.White
                                    )
                                }

                                Button(
                                    onClick = { viewModel.deleteBackup() },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFFFEF2F2)
                                    ),
                                    border = BorderStroke(1.dp, Color(0xFFFEE2E2))
                                ) {
                                    Text(
                                        text = "Delete Backup",
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Medium,
                                        color = Color(0xFFEF4444)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Advanced Section
                        SecuritySection(
                            title = "Advanced Security",
                            description = "Advanced security options"
                        ) {
                            Button(
                                onClick = { viewModel.clearAllData() },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFFFEF2F2)
                                ),
                                border = BorderStroke(1.dp, Color(0xFFFEE2E2))
                            ) {
                                Text(
                                    text = "Clear All Secure Data",
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium,
                                    color = Color(0xFFEF4444)
                                )
                            }
                        }
                    }
                }
            }

            // Show operation overlay (for background operations)
            when (operationState) {
                SecurityOperation.BACKING_UP,
                SecurityOperation.RESTORING,
                SecurityOperation.UPDATING -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.5f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            elevation = CardDefaults.cardElevation(0.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                CircularProgressIndicator(
                                    color = Color(0xFF3B82F6),
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = when (operationState) {
                                        SecurityOperation.BACKING_UP -> "Creating backup..."
                                        SecurityOperation.RESTORING -> "Restoring..."
                                        SecurityOperation.UPDATING -> "Updating..."
                                        else -> "Processing..."
                                    },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color(0xFF6B7280)
                                )
                            }
                        }
                    }
                }
                else -> { /* No overlay */ }
            }
        }
    }
}

@Composable
fun SecurityStatusCard(
    isBiometricEnabled: Boolean,
    isPinSet: Boolean,
    isBackupAvailable: Boolean
) {
    val securityScore = remember(isBiometricEnabled, isPinSet, isBackupAvailable) {
        var score = 0
        if (isBiometricEnabled) score += 40
        if (isPinSet) score += 30
        if (isBackupAvailable) score += 30
        score
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Shield,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Text(
                    text = "Security Status",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Security Score
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Security Score",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF6B7280)
                )
                Text(
                    text = "$securityScore/100",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = when {
                        securityScore >= 80 -> Color(0xFF10B981)
                        securityScore >= 50 -> Color(0xFFF59E0B)
                        else -> Color(0xFFEF4444)
                    }
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Progress bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color(0xFFE5E7EB))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(securityScore / 100f)
                        .fillMaxHeight()
                        .background(
                            color = when {
                                securityScore >= 80 -> Color(0xFF10B981)
                                securityScore >= 50 -> Color(0xFFF59E0B)
                                else -> Color(0xFFEF4444)
                            }
                        )
                        .clip(RoundedCornerShape(4.dp))
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Security Features Status
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                SecurityFeatureStatus(
                    feature = "Biometric Authentication",
                    enabled = isBiometricEnabled
                )
                SecurityFeatureStatus(
                    feature = "PIN Protection",
                    enabled = isPinSet
                )
                SecurityFeatureStatus(
                    feature = "Encrypted Backup",
                    enabled = isBackupAvailable
                )
            }
        }
    }
}

@Composable
fun SecuritySection(
    title: String,
    description: String,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color.Black
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFF6B7280)
            )

            Spacer(modifier = Modifier.height(16.dp))

            content()
        }
    }
}

@Composable
fun SecurityFeatureStatus(feature: String, enabled: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (enabled) Icons.Outlined.CheckCircle else Icons.Outlined.Cancel,
            contentDescription = null,
            tint = if (enabled) Color(0xFF10B981) else Color(0xFF9CA3AF),
            modifier = Modifier.size(20.dp)
        )

        Spacer(modifier = Modifier.width(12.dp))

        Text(
            text = feature,
            style = MaterialTheme.typography.bodyMedium,
            color = Color.Black,
            modifier = Modifier.weight(1f)
        )

        Text(
            text = if (enabled) "Active" else "Inactive",
            style = MaterialTheme.typography.labelSmall,
            color = if (enabled) Color(0xFF10B981) else Color(0xFF9CA3AF)
        )
    }
}