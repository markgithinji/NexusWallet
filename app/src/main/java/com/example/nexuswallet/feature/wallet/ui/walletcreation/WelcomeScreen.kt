package com.example.nexuswallet.feature.wallet.ui.walletcreation

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.FileOpen
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.Password
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.VpnKey
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.nexuswallet.R
import com.example.nexuswallet.feature.core.ui.BiometricAuthHandler
import com.example.nexuswallet.feature.core.ui.isBiometricUserCancel
import com.example.nexuswallet.feature.settings.ui.auth.PinEntryDialog
import com.example.nexuswallet.feature.settings.ui.security.PinVerifyPurpose
import com.example.nexuswallet.feature.settings.ui.security.RestoreSelectionDialog
import com.example.nexuswallet.feature.settings.ui.security.SecurityOperationOverlay
import com.example.nexuswallet.feature.settings.ui.security.SecuritySettingsViewModel
import com.example.nexuswallet.feature.settings.ui.security.SecurityUiEffect
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WelcomeScreen(
    onCreateWallet: () -> Unit,
    onImportWallet: () -> Unit,
    onSkip: () -> Unit,
    onRestoreSuccess: () -> Unit,
    viewModel: SecuritySettingsViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showImportOptions by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState()

    val operationState by viewModel.operationState.collectAsStateWithLifecycle()
    val showPinVerifyDialog by viewModel.showPinVerifyDialog.collectAsStateWithLifecycle()
    val showRestoreSelectionDialog by viewModel.showRestoreSelectionDialog.collectAsStateWithLifecycle()
    val decryptedBundle by viewModel.decryptedBundle.collectAsStateWithLifecycle()
    val restoreSelection by viewModel.restoreSelection.collectAsStateWithLifecycle()
    val pinVerifyPurpose by viewModel.pinVerifyPurpose.collectAsStateWithLifecycle()
    val pinSetupError by viewModel.pinSetupError.collectAsStateWithLifecycle()
    val authRequest by viewModel.authRequest.collectAsStateWithLifecycle()
    val cryptoObject by viewModel.cryptoObject.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }

    BiometricAuthHandler(
        authRequest = authRequest,
        cryptoObject = cryptoObject,
        subtitle = stringResource(R.string.authentication_required),
        onSuccess = { result -> viewModel.onAuthSuccess(result.cryptoObject?.cipher) },
        onError = { errorCode, errString ->
            if (!isBiometricUserCancel(errorCode)) {
                scope.launch {
                    snackbarHostState.showSnackbar(errString)
                }
            }
        },
        onDismiss = { viewModel.clearAuthRequest() }
    )

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

    LaunchedEffect(Unit) {
        viewModel.uiEffect.collect { effect ->
            when (effect) {
                is SecurityUiEffect.ShowSnackbar -> snackbarHostState.showSnackbar(effect.message)
                SecurityUiEffect.SelectBackupFile -> selectBackupLauncher.launch(arrayOf("*/*"))
                SecurityUiEffect.RestoreSuccess -> onRestoreSuccess()
                else -> {}
            }
        }
    }

    val gradient = Brush.verticalGradient(
        colors = listOf(
            MaterialTheme.colorScheme.surface,
            MaterialTheme.colorScheme.background
        )
    )

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = Color.Transparent
    ) { padding ->
        Box(modifier = Modifier
            .fillMaxSize()
            .padding(padding)) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(gradient)
                    .systemBarsPadding()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(modifier = Modifier.height(32.dp))

                // Hero Section
                Box(
                    modifier = Modifier
                        .padding(top = 16.dp)
                        .size(100.dp)
                        .clip(RoundedCornerShape(28.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.nexus_icon),
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        contentScale = ContentScale.Fit
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = stringResource(R.string.welcome_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )

                Spacer(modifier = Modifier.height(64.dp))

                // Features Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(0.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp, horizontal = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Row(modifier = Modifier.fillMaxWidth()) {
                            CompactFeatureItem(
                                Modifier.weight(1f),
                                Icons.Outlined.Security,
                                stringResource(R.string.feature_bip39)
                            )
                            CompactFeatureItem(
                                Modifier.weight(1f),
                                Icons.Outlined.Fingerprint,
                                stringResource(R.string.feature_biometric)
                            )
                        }
                        Row(modifier = Modifier.fillMaxWidth()) {
                            CompactFeatureItem(
                                Modifier.weight(1f),
                                Icons.Outlined.VpnKey,
                                stringResource(R.string.feature_keystore)
                            )
                            CompactFeatureItem(
                                Modifier.weight(1f),
                                Icons.Outlined.AccountBalanceWallet,
                                stringResource(R.string.feature_multichain)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(64.dp))

                // Action Buttons
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Button(
                        onClick = onCreateWallet,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text(
                            stringResource(R.string.create_new_wallet),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    OutlinedButton(
                        onClick = { showImportOptions = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(
                            1.dp,
                            MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                        )
                    ) {
                        Text(
                            stringResource(R.string.import_existing_wallet),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    TextButton(onClick = onSkip) {
                        Text(
                            stringResource(R.string.skip_for_now),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Global Overlays
            SecurityOperationOverlay(operationState = operationState)
        }
    }

    // Import Options Sheet
    if (showImportOptions) {
        ModalBottomSheet(
            onDismissRequest = { showImportOptions = false },
            sheetState = sheetState,
            shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
            containerColor = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp,
            dragHandle = {
                Box(
                    modifier = Modifier
                        .padding(vertical = 12.dp)
                        .size(width = 40.dp, height = 4.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                )
            }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 48.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Import Wallet",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Choose how you want to restore your assets",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                ImportOptionItem(
                    title = "Recovery Phrase",
                    description = "Import using 12-word seed phrase",
                    icon = Icons.Outlined.Password,
                    onClick = {
                        showImportOptions = false
                        onImportWallet()
                    }
                )

                ImportOptionItem(
                    title = "Nexus Backup File",
                    description = "Restore from an encrypted .bin file",
                    icon = Icons.Outlined.FileOpen,
                    onClick = {
                        showImportOptions = false
                        viewModel.handleRestoreBackupClick()
                    }
                )
            }
        }
    }

    // PIN Verification for Backup
    PinEntryDialog(
        showDialog = showPinVerifyDialog,
        title = if (pinVerifyPurpose == PinVerifyPurpose.RESTORE)
            "Enter Backup PIN"
        else stringResource(R.string.confirm_pin_title),
        subtitle = if (pinVerifyPurpose == PinVerifyPurpose.RESTORE)
            "Enter the PIN used to encrypt this backup file"
        else stringResource(R.string.confirm_pin_subtitle),
        errorMessage = pinSetupError,
        onPinEntered = viewModel::onPinVerified,
        onTyping = viewModel::clearPinError,
        onDismiss = viewModel::cancelPinSetup
    )

    // Selective Restore Dialog
    if (showRestoreSelectionDialog && decryptedBundle != null) {
        RestoreSelectionDialog(
            bundle = decryptedBundle!!,
            selection = restoreSelection,
            onWalletToggle = viewModel::toggleWalletSelection,
            onNetworkToggle = viewModel::toggleNetworkSelection,
            onTokenToggle = viewModel::toggleTokenSelection,
            onConfirm = {
                viewModel.confirmRestore()
            },
            onDismiss = viewModel::cancelRestoreSelection
        )
    }
}

@Composable
fun ImportOptionItem(
    title: String,
    description: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
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
            }
            Icon(
                imageVector = Icons.Outlined.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
        }
    }
}

@Composable
fun CompactFeatureItem(modifier: Modifier = Modifier, icon: ImageVector, text: String) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )
    }
}
