package com.example.nexuswallet.feature.settings.ui


import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.nexuswallet.NexusWalletApplication
import com.example.nexuswallet.feature.authentication.domain.SecurityState
import com.example.nexuswallet.feature.authentication.ui.PinSetupDialog
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SecuritySettingsScreen(
    navController: NavController,
    viewModel: SecuritySettingsViewModel = viewModel()
) {
    val securityState by viewModel.securityState.collectAsState()
    val isBiometricEnabled by viewModel.isBiometricEnabled.collectAsState()
    val isPinSet by viewModel.isPinSet.collectAsState()
    val isBackupAvailable by viewModel.isBackupAvailable.collectAsState()

    val showPinSetupDialog by viewModel.showPinSetupDialog.collectAsState()
    val showPinChangeDialog by viewModel.showPinChangeDialog.collectAsState()
    val pinSetupError by viewModel.pinSetupError.collectAsState()

    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
            val securityManager = NexusWalletApplication.Companion.instance.securityManager
            Log.d("SecurityDebug", "PIN set: ${securityManager.isPinSet()}")
            Log.d("SecurityDebug", "Biometric enabled: ${securityManager.isBiometricEnabled()}")
    }

    // Show PIN Setup Dialog
    if (showPinSetupDialog || showPinChangeDialog) {
        PinSetupDialog(
            showDialog = true,
            title = if (showPinSetupDialog) "Setup PIN" else "Change PIN",
            subtitle = "Enter a 4-6 digit PIN",
            onPinSet = { pin ->
                coroutineScope.launch {
                    val success = viewModel.setNewPin(pin)
                    if (!success) {
                        // Error will be shown in the dialog
                    }
                }
            },
            onDismiss = { viewModel.cancelPinSetup() },
            errorMessage = pinSetupError
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Security Settings") },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
            ) {
                // Security Status Card
                SecurityStatusCard(
                    isBiometricEnabled = isBiometricEnabled,
                    isPinSet = isPinSet,
                    isBackupAvailable = isBackupAvailable
                )

                Spacer(modifier = Modifier.height(16.dp))

                // PIN Section
                SecuritySection(
                    title = "PIN Protection",
                    description = "Add an extra layer of security with a PIN"
                ) {
                    if (isPinSet) {
                        Button(
                            onClick = { viewModel.changePin() },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Change PIN")
                        }

                        Button(
                            onClick = { viewModel.removePin() },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer,
                                contentColor = MaterialTheme.colorScheme.onErrorContainer
                            )
                        ) {
                            Text("Remove PIN")
                        }
                    } else {
                        Button(
                            onClick = { viewModel.setupPin() },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Setup PIN")
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
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isBiometricEnabled) "Enabled" else "Disabled",
                            modifier = Modifier.weight(1f)
                        )

                        Switch(
                            checked = isBiometricEnabled,
                            onCheckedChange = { viewModel.setBiometricEnabled(it) }
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
                            enabled = !isBackupAvailable
                        ) {
                            Text("Create Encrypted Backup")
                        }

                        if (isBackupAvailable) {
                            Button(
                                onClick = { viewModel.restoreBackup() },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Restore from Backup")
                            }

                            Button(
                                onClick = { viewModel.deleteBackup() },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.errorContainer,
                                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                                )
                            ) {
                                Text("Delete Backup")
                            }
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
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer
                        )
                    ) {
                        Text("Clear All Secure Data")
                    }
                }
            }

            // Show loading overlay
            when (securityState) {
                is SecurityState.ENCRYPTING,
                is SecurityState.DECRYPTING,
                is SecurityState.BACKING_UP,
                is SecurityState.RESTORING -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator()
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = when (securityState) {
                                    is SecurityState.ENCRYPTING -> "Encrypting..."
                                    is SecurityState.DECRYPTING -> "Decrypting..."
                                    is SecurityState.BACKING_UP -> "Creating backup..."
                                    is SecurityState.RESTORING -> "Restoring..."
                                    else -> "Processing..."
                                },
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }

                is SecurityState.ERROR -> {
                    val errorMessage = (securityState as SecurityState.ERROR).message
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.Error,
                                contentDescription = "Error",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Security operation failed",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.error
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = errorMessage,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }

                else -> {
                    // Normal state, nothing to show
                }
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
            .padding(16.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = when {
                securityScore >= 80 -> Color.Green.copy(alpha = 0.1f)
                securityScore >= 50 -> Color.Yellow.copy(alpha = 0.1f)
                else -> Color.Red.copy(alpha = 0.1f)
            }
        )
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            Text(
                text = "Security Status",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Security Score
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Security Score: ",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "$securityScore/100",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = when {
                        securityScore >= 80 -> Color.Green
                        securityScore >= 50 -> Color.Magenta
                        else -> Color.Red
                    }
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            LinearProgressIndicator(
                progress = { securityScore / 100f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp),
                color = when {
                    securityScore >= 80 -> Color.Green
                    securityScore >= 50 -> Color.Magenta
                    else -> Color.Red
                }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Security Features Status
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SecurityFeatureStatus(
                    feature = "Biometric",
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
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(16.dp))

            content()
        }
    }
}

@Composable
fun SecurityFeatureStatus(feature: String, enabled: Boolean) {
    Row(
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (enabled) Icons.Default.CheckCircle else Icons.Default.Error,
            contentDescription = null,
            tint = if (enabled) Color.Green else MaterialTheme.colorScheme.error,
            modifier = Modifier.size(20.dp)
        )

        Spacer(modifier = Modifier.width(8.dp))

        Text(
            text = feature,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )

        Text(
            text = if (enabled) "Active" else "Inactive",
            style = MaterialTheme.typography.labelMedium,
            color = if (enabled) Color.Green else MaterialTheme.colorScheme.error
        )
    }
}