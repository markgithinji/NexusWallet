package com.example.nexuswallet.feature.settings.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Error
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.example.nexuswallet.feature.core.util.Result

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwitchDefaults
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.nexuswallet.ui.theme.success
import com.example.nexuswallet.ui.theme.warning

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SecuritySettingsScreen(
    onNavigateUp: () -> Unit,
    viewModel: SecuritySettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val operationState by viewModel.operationState.collectAsStateWithLifecycle()
    val showPinSetupDialog by viewModel.showPinSetupDialog.collectAsStateWithLifecycle()
    val showPinChangeDialog by viewModel.showPinChangeDialog.collectAsStateWithLifecycle()
    val pinSetupError by viewModel.pinSetupError.collectAsStateWithLifecycle()

    // PIN Setup Dialog
    SecurityPinDialog(
        showDialog = showPinSetupDialog || showPinChangeDialog,
        title = if (showPinSetupDialog) "Setup PIN" else "Change PIN",
        subtitle = "Enter a 6-digit PIN",
        errorMessage = pinSetupError,
        onPinSet = viewModel::setNewPin,
        onDismiss = viewModel::cancelPinSetup
    )

    Scaffold(
        topBar = { SecurityTopBar(onNavigateUp = onNavigateUp) },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize()) {
            when (val state = uiState) {
                is Result.Loading -> SecurityLoadingContent(padding)
                is Result.Error -> SecurityErrorContent(
                    message = state.message,
                    onRetry = viewModel::retry,
                    padding = padding
                )
                is Result.Success -> SecuritySettingsContent(
                    securityState = state.data,
                    viewModel = viewModel,
                    padding = padding
                )
            }

            // Operation overlay
            SecurityOperationOverlay(operationState = operationState)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SecurityTopBar(onNavigateUp: () -> Unit) {
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
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        },
        navigationIcon = {
            IconButton(onClick = onNavigateUp) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    )
}

@Composable
private fun SecurityLoadingContent(padding: PaddingValues) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun SecurityErrorContent(
    message: String,
    onRetry: () -> Unit,
    padding: PaddingValues
) {
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
            tint = MaterialTheme.colorScheme.error
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = onRetry,
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary
            )
        ) {
            Text(
                "Retry",
                color = MaterialTheme.colorScheme.onPrimary,
                style = MaterialTheme.typography.labelLarge
            )
        }
    }
}

@Composable
private fun SecuritySettingsContent(
    securityState: SecurityUiState,
    viewModel: SecuritySettingsViewModel,
    padding: PaddingValues
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .verticalScroll(rememberScrollState())
    ) {
        SecurityStatusCard(
            isBiometricEnabled = securityState.isBiometricEnabled,
            isPinSet = securityState.isPinSet,
            isBackupAvailable = false
        )

        Spacer(modifier = Modifier.height(16.dp))

        PinSection(
            isPinSet = securityState.isPinSet,
            onChangePin = viewModel::changePin,
            onRemovePin = viewModel::removePin,
            onSetupPin = viewModel::setupPin
        )

        Spacer(modifier = Modifier.height(16.dp))

        BiometricSection(
            isBiometricEnabled = securityState.isBiometricEnabled,
            onToggle = viewModel::setBiometricEnabled
        )

        Spacer(modifier = Modifier.height(16.dp))

        BackupSection(
            onCreateBackup = viewModel::createBackup,
            onRestoreBackup = viewModel::restoreBackup,
            onDeleteBackup = viewModel::deleteBackup
        )

        Spacer(modifier = Modifier.height(16.dp))

        AdvancedSecuritySection(
            onClearAllData = viewModel::clearAllData
        )
    }
}

@Composable
private fun SecurityStatusCard(
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

    SecurityCard {
        Text(
            text = "Security Status",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(16.dp))

        SecurityScoreRow(score = securityScore)
        SecurityScoreProgress(score = securityScore)

        Spacer(modifier = Modifier.height(16.dp))

        SecurityFeaturesList(
            isBiometricEnabled = isBiometricEnabled,
            isPinSet = isPinSet,
            isBackupAvailable = isBackupAvailable
        )
    }
}

@Composable
private fun SecurityScoreRow(score: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = "Security Score",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = "$score/100",
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold,
            color = when {
                score >= 80 -> MaterialTheme.colorScheme.success
                score >= 50 -> MaterialTheme.colorScheme.warning
                else -> MaterialTheme.colorScheme.error
            }
        )
    }
}

@Composable
private fun SecurityScoreProgress(score: Int) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(score / 100f)
                .fillMaxHeight()
                .background(
                    color = when {
                        score >= 80 -> MaterialTheme.colorScheme.success
                        score >= 50 -> MaterialTheme.colorScheme.warning
                        else -> MaterialTheme.colorScheme.error
                    }
                )
                .clip(RoundedCornerShape(4.dp))
        )
    }
}

@Composable
private fun SecurityFeaturesList(
    isBiometricEnabled: Boolean,
    isPinSet: Boolean,
    isBackupAvailable: Boolean
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SecurityFeatureItem(
            feature = "Biometric Authentication",
            enabled = isBiometricEnabled
        )
        SecurityFeatureItem(
            feature = "PIN Protection",
            enabled = isPinSet
        )
        SecurityFeatureItem(
            feature = "Encrypted Backup",
            enabled = isBackupAvailable
        )
    }
}

@Composable
private fun SecurityFeatureItem(feature: String, enabled: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (enabled) Icons.Outlined.CheckCircle else Icons.Outlined.Cancel,
            contentDescription = null,
            tint = if (enabled) MaterialTheme.colorScheme.success else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = feature,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = if (enabled) "Active" else "Inactive",
            style = MaterialTheme.typography.labelSmall,
            color = if (enabled) MaterialTheme.colorScheme.success else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun PinSection(
    isPinSet: Boolean,
    onChangePin: () -> Unit,
    onRemovePin: () -> Unit,
    onSetupPin: () -> Unit
) {
    SecuritySection(
        title = "PIN Protection",
        description = "Add an extra layer of security with a PIN"
    ) {
        if (isPinSet) {
            PinManagementButtons(
                onChangePin = onChangePin,
                onRemovePin = onRemovePin
            )
        } else {
            PinSetupButton(onSetupPin = onSetupPin)
        }
    }
}

@Composable
private fun PinManagementButtons(
    onChangePin: () -> Unit,
    onRemovePin: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        PrimaryButton(
            text = "Change PIN",
            onClick = onChangePin
        )
        DangerButton(
            text = "Remove PIN",
            onClick = onRemovePin
        )
    }
}

@Composable
private fun PinSetupButton(onSetupPin: () -> Unit) {
    PrimaryButton(
        text = "Setup PIN",
        onClick = onSetupPin
    )
}

@Composable
private fun BiometricSection(
    isBiometricEnabled: Boolean,
    onToggle: (Boolean) -> Unit
) {
    SecuritySection(
        title = "Biometric Authentication",
        description = "Use fingerprint or face recognition"
    ) {
        BiometricToggle(
            isEnabled = isBiometricEnabled,
            onToggle = onToggle
        )
    }
}

@Composable
private fun BiometricToggle(
    isEnabled: Boolean,
    onToggle: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = if (isEnabled) "Enabled" else "Disabled",
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            color = if (isEnabled) MaterialTheme.colorScheme.success else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Switch(
            checked = isEnabled,
            onCheckedChange = onToggle,
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                checkedTrackColor = MaterialTheme.colorScheme.primary,
                uncheckedThumbColor = MaterialTheme.colorScheme.onSurface,
                uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
            )
        )
    }
}

@Composable
private fun BackupSection(
    onCreateBackup: () -> Unit,
    onRestoreBackup: () -> Unit,
    onDeleteBackup: () -> Unit
) {
    SecuritySection(
        title = "Encrypted Backup",
        description = "Create and restore encrypted backups"
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            PrimaryButton(
                text = "Create Encrypted Backup",
                onClick = onCreateBackup
            )
            PrimaryButton(
                text = "Restore from Backup",
                onClick = onRestoreBackup
            )
            DangerButton(
                text = "Delete Backup",
                onClick = onDeleteBackup
            )
        }
    }
}

@Composable
private fun AdvancedSecuritySection(onClearAllData: () -> Unit) {
    SecuritySection(
        title = "Advanced Security",
        description = "Advanced security options"
    ) {
        DangerButton(
            text = "Clear All Secure Data",
            onClick = onClearAllData
        )
    }
}

@Composable
private fun PrimaryButton(text: String, onClick: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    Button(
        onClick = {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            onClick()
        },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary
        )
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onPrimary
        )
    }
}

@Composable
private fun DangerButton(text: String, onClick: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    Button(
        onClick = {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            onClick()
        },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.error
        )
    }
}

@Composable
private fun SecurityCard(
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            content()
        }
    }
}

@Composable
private fun SecuritySection(
    title: String,
    description: String,
    content: @Composable () -> Unit
) {
    SecurityCard {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(16.dp))
        content()
    }
}

@Composable
private fun SecurityOperationOverlay(operationState: SecurityOperation) {
    if (operationState == SecurityOperation.BACKING_UP ||
        operationState == SecurityOperation.RESTORING ||
        operationState == SecurityOperation.UPDATING
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.5f)),
            contentAlignment = Alignment.Center
        ) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(0.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.primary,
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
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun SecurityPinDialog(
    showDialog: Boolean,
    title: String,
    subtitle: String,
    errorMessage: String?,
    onPinSet: (String) -> Unit,
    onDismiss: () -> Unit
) {
    if (showDialog) {
        PinSetupDialog(
            showDialog = true,
            title = title,
            subtitle = subtitle,
            onPinSet = onPinSet,
            onDismiss = onDismiss,
            errorMessage = errorMessage
        )
    }
}