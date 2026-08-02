package com.example.nexuswallet.feature.settings.ui.security

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.nexuswallet.R
import com.example.nexuswallet.feature.core.ui.isBiometricUserCancel
import com.example.nexuswallet.feature.core.ui.rememberBiometricPrompt
import com.example.nexuswallet.feature.core.util.Result
import com.example.nexuswallet.feature.ethereum.domain.model.EVMTokenType
import com.example.nexuswallet.feature.settings.domain.model.BackupBundle
import com.example.nexuswallet.feature.settings.domain.model.RestoreSelection
import com.example.nexuswallet.feature.settings.ui.auth.PinEntryDialog
import com.example.nexuswallet.feature.wallet.domain.model.BitcoinNetwork
import com.example.nexuswallet.feature.wallet.domain.model.EthereumNetwork
import com.example.nexuswallet.feature.wallet.domain.model.SolanaNetwork
import com.example.nexuswallet.feature.wallet.ui.common.*
import com.example.nexuswallet.ui.theme.bitcoinLight
import com.example.nexuswallet.ui.theme.ethereumLight
import com.example.nexuswallet.ui.theme.solanaLight
import com.example.nexuswallet.ui.theme.success
import com.example.nexuswallet.ui.theme.usdcLight
import com.example.nexuswallet.ui.theme.usdtLight
import com.example.nexuswallet.ui.theme.warning
import kotlinx.coroutines.launch

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
    val showPinVerifyDialog by viewModel.showPinVerifyDialog.collectAsStateWithLifecycle()
    val showClearAllDataDialog by viewModel.showClearAllDataDialog.collectAsStateWithLifecycle()
    val showRestoreSelectionDialog by viewModel.showRestoreSelectionDialog.collectAsStateWithLifecycle()
    val clearAllConfirmationText by viewModel.clearAllConfirmationText.collectAsStateWithLifecycle()
    val authRequest by viewModel.authRequest.collectAsStateWithLifecycle()
    val decryptedBundle by viewModel.decryptedBundle.collectAsStateWithLifecycle()
    val restoreSelection by viewModel.restoreSelection.collectAsStateWithLifecycle()
    val pinSetupError by viewModel.pinSetupError.collectAsStateWithLifecycle()
    val pinVerifyPurpose by viewModel.pinVerifyPurpose.collectAsStateWithLifecycle()
    
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var pendingBackupData by remember { mutableStateOf<ByteArray?>(null) }

    val saveBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        uri?.let {
            scope.launch {
                try {
                    context.contentResolver.openOutputStream(it)?.use { stream ->
                        pendingBackupData?.let { data -> stream.write(data) }
                    }
                    snackbarHostState.showSnackbar("Backup saved successfully")
                } catch (e: Exception) {
                    snackbarHostState.showSnackbar("Failed to save backup: ${e.message}")
                } finally {
                    pendingBackupData = null
                }
            }
        }
    }

    val selectBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            scope.launch {
                try {
                    val data = context.contentResolver.openInputStream(it)?.use { stream ->
                        stream.readBytes()
                    }
                    if (data != null) {
                        viewModel.onBackupFileSelected(data)
                    }
                } catch (e: Exception) {
                    snackbarHostState.showSnackbar("Failed to read backup: ${e.message}")
                }
            }
        }
    }

    val biometricPrompt = rememberBiometricPrompt(
        onSuccess = { _ -> viewModel.onClearAllAuthSuccess() },
        onError = { errorCode, errString ->
            if (!isBiometricUserCancel(errorCode)) {
                scope.launch {
                    snackbarHostState.showSnackbar(errString.toString())
                }
            }
        }
    )

    val biometricTitle = stringResource(R.string.biometric_authentication)
    val biometricSubtitle = stringResource(R.string.clear_all_data)
    val biometricCancel = stringResource(R.string.cancel)

    LaunchedEffect(authRequest) {
        if (authRequest != null) {
            val promptInfo = BiometricPrompt.PromptInfo.Builder()
                .setTitle(biometricTitle)
                .setSubtitle(biometricSubtitle)
                .setNegativeButtonText(biometricCancel)
                .build()

            biometricPrompt?.authenticate(promptInfo)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.uiEffect.collect { effect ->
            when (effect) {
                is SecurityUiEffect.ShowSnackbar -> {
                    snackbarHostState.showSnackbar(effect.message)
                }
                is SecurityUiEffect.SaveBackupFile -> {
                    pendingBackupData = effect.data
                    saveBackupLauncher.launch(effect.fileName)
                }
                SecurityUiEffect.SelectBackupFile -> {
                    selectBackupLauncher.launch(arrayOf("*/*"))
                }
                SecurityUiEffect.RestoreSuccess -> {
                    // Maybe show an extra success state or navigate
                }
            }
        }
    }

    // PIN Setup Dialog
    SecurityPinDialog(
        showDialog = showPinSetupDialog || showPinChangeDialog,
        title = if (showPinSetupDialog) stringResource(R.string.setup_pin) else stringResource(R.string.change_pin),
        subtitle = stringResource(R.string.pin_digits_hint, 6),
        errorMessage = pinSetupError,
        onPinSet = viewModel::setNewPin,
        onDismiss = viewModel::cancelPinSetup
    )

    // PIN Verification Dialog for Backup/Restore
    PinEntryDialog(
        showDialog = showPinVerifyDialog,
        title = if (pinVerifyPurpose == PinVerifyPurpose.RESTORE) 
            stringResource(R.string.restore_backup) 
            else stringResource(R.string.confirm_pin_title),
        subtitle = if (pinVerifyPurpose == PinVerifyPurpose.RESTORE)
            "Enter the PIN used to encrypt this backup file"
            else stringResource(R.string.confirm_pin_subtitle),
        onPinEntered = viewModel::onPinVerified,
        onDismiss = viewModel::cancelPinSetup
    )

    // Clear All Data Confirmation Dialog
    if (showClearAllDataDialog) {
        AlertDialog(
            onDismissRequest = viewModel::cancelClearAllData,
            title = {
                Text(
                    text = stringResource(R.string.clear_all_data_confirmation_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.error
                )
            },
            text = {
                Column {
                    Text(
                        text = stringResource(R.string.clear_all_data_confirmation_desc),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = clearAllConfirmationText,
                        onValueChange = viewModel::onClearAllConfirmationTextChanged,
                        label = { Text(stringResource(R.string.confirmation_word_placeholder)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.error,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline
                        )
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = viewModel::confirmClearAllData,
                    enabled = clearAllConfirmationText.trim().uppercase() == "DELETE",
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        disabledContainerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.5f)
                    )
                ) {
                    Text(stringResource(R.string.wipe_data_button))
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::cancelClearAllData) {
                    Text(stringResource(R.string.cancel))
                }
            },
            shape = RoundedCornerShape(24.dp)
        )
    }

    // Restore Selection Dialog
    if (showRestoreSelectionDialog && decryptedBundle != null) {
        RestoreSelectionDialog(
            bundle = decryptedBundle!!,
            selection = restoreSelection,
            onWalletToggle = viewModel::toggleWalletSelection,
            onNetworkToggle = viewModel::toggleNetworkSelection,
            onTokenToggle = viewModel::toggleTokenSelection,
            onConfirm = viewModel::confirmRestore,
            onDismiss = viewModel::cancelRestoreSelection
        )
    }

    Scaffold(
        topBar = { SecurityTopBar(onNavigateUp = onNavigateUp) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize()) {
            when (val state = uiState) {
                is Result.Loading -> FullScreenLoading(
                    modifier = Modifier.padding(padding),
                    message = stringResource(R.string.loading)
                )
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
                    text = stringResource(R.string.security_settings),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        },
        navigationIcon = {
            IconButton(onClick = onNavigateUp) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
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
                stringResource(R.string.try_again),
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
        Spacer(modifier = Modifier.height(16.dp))

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

        SecurityToggleSection(
            title = stringResource(R.string.biometric_authentication),
            description = stringResource(R.string.biometric_description),
            checked = securityState.isBiometricEnabled,
            onCheckedChange = viewModel::setBiometricEnabled
        )

        Spacer(modifier = Modifier.height(12.dp))

        SecurityToggleSection(
            title = stringResource(R.string.privacy_mode),
            description = stringResource(R.string.privacy_mode_description),
            checked = securityState.isPrivacyModeEnabled,
            onCheckedChange = viewModel::setPrivacyModeEnabled
        )

        Spacer(modifier = Modifier.height(12.dp))

        SecurityToggleSection(
            title = stringResource(R.string.transaction_security),
            description = stringResource(R.string.transaction_security_description),
            checked = securityState.isRequireAuthForSend,
            onCheckedChange = viewModel::setRequireAuthForSend,
            activeText = stringResource(R.string.always_require),
            inactiveText = stringResource(R.string.standard)
        )

        Spacer(modifier = Modifier.height(16.dp))

        BackupSection(
            onCreateBackup = viewModel::handleCreateBackupClick,
            onRestoreBackup = viewModel::handleRestoreBackupClick,
            onDeleteBackup = viewModel::deleteBackup
        )

        Spacer(modifier = Modifier.height(16.dp))

        AdvancedSecuritySection(
            onClearAllData = viewModel::requestClearAllData
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
            text = stringResource(R.string.security_status),
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
            text = stringResource(R.string.security_score),
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
            feature = stringResource(R.string.biometric_authentication),
            enabled = isBiometricEnabled
        )
        SecurityFeatureItem(
            feature = stringResource(R.string.pin_protection),
            enabled = isPinSet
        )
        SecurityFeatureItem(
            feature = stringResource(R.string.encrypted_backup),
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
            text = if (enabled) stringResource(R.string.active) else stringResource(R.string.inactive),
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
        title = stringResource(R.string.pin_protection),
        description = stringResource(R.string.pin_description)
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
            text = stringResource(R.string.change_pin),
            onClick = onChangePin
        )
        DangerButton(
            text = stringResource(R.string.remove_pin),
            onClick = onRemovePin
        )
    }
}

@Composable
private fun PinSetupButton(onSetupPin: () -> Unit) {
    PrimaryButton(
        text = stringResource(R.string.setup_pin),
        onClick = onSetupPin
    )
}

@Composable
private fun SecurityToggleSection(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    activeText: String = stringResource(R.string.enabled),
    inactiveText: String = stringResource(R.string.disabled)
) {
    SecurityCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (checked) activeText else inactiveText,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Medium,
                    color = if (checked) MaterialTheme.colorScheme.success else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                    checkedTrackColor = MaterialTheme.colorScheme.primary,
                    uncheckedThumbColor = MaterialTheme.colorScheme.onSurface,
                    uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            )
        }
    }
}

@Composable
private fun BackupSection(
    onCreateBackup: () -> Unit,
    onRestoreBackup: () -> Unit,
    onDeleteBackup: () -> Unit
) {
    SecuritySection(
        title = stringResource(R.string.encrypted_backup),
        description = stringResource(R.string.backup_description)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            PrimaryButton(
                text = stringResource(R.string.create_backup),
                onClick = onCreateBackup
            )
            PrimaryButton(
                text = stringResource(R.string.restore_backup),
                onClick = onRestoreBackup
            )
            DangerButton(
                text = stringResource(R.string.delete_backup),
                onClick = onDeleteBackup
            )
        }
    }
}

@Composable
private fun AdvancedSecuritySection(onClearAllData: () -> Unit) {
    SecuritySection(
        title = stringResource(R.string.advanced_security),
        description = stringResource(R.string.advanced_security_description)
    ) {
        DangerButton(
            text = stringResource(R.string.clear_all_data),
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
fun SecurityOperationOverlay(operationState: SecurityOperation) {
    if (operationState == SecurityOperation.BACKING_UP ||
        operationState == SecurityOperation.RESTORING ||
        operationState == SecurityOperation.UPDATING
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.5f)),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 0.dp,
                shadowElevation = 0.dp,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 40.dp, vertical = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.primary,
                        strokeWidth = 3.dp,
                        modifier = Modifier.size(40.dp)
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    Text(
                        text = when (operationState) {
                            SecurityOperation.BACKING_UP -> stringResource(R.string.creating_backup)
                            SecurityOperation.RESTORING -> stringResource(R.string.restoring)
                            SecurityOperation.UPDATING -> stringResource(R.string.updating)
                            else -> ""
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center
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
