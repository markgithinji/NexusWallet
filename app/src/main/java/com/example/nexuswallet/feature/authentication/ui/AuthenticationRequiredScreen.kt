package com.example.nexuswallet.feature.authentication.ui

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Pin
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.example.nexuswallet.feature.authentication.domain.AuthType
import com.example.nexuswallet.feature.coin.Result
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthenticationRequiredScreen(
    onAuthenticated: () -> Unit,
    onCancel: () -> Unit,
    title: String = "Authentication Required",
    description: String = "Please authenticate to access this feature",
    viewModel: AuthenticationViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val activity = context as? FragmentActivity

    val authenticationResult by viewModel.authenticationResult.collectAsState()
    val showPinDialog by viewModel.showPinDialog.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()

    val coroutineScope = rememberCoroutineScope()

    // Create biometric authenticator
    val authenticateWithBiometric = rememberBiometricAuthenticator(
        title = "Biometric Authentication",
        subtitle = "Use your fingerprint or face to authenticate",
        description = description,
        onSuccess = {
            viewModel.onBiometricSuccess()
        },
        onError = { errorMsg ->
            viewModel.setErrorMessage(errorMsg)
        },
        onFailed = {
            viewModel.setErrorMessage("Authentication failed. Please try again.")
        }
    )

    // Check biometric availability
    LaunchedEffect(Unit) {
        if (activity != null) {
            checkBiometricAvailability(activity, viewModel)
        }
    }

    // Show PIN dialog when required
    if (showPinDialog) {
        AuthenticationPinDialog(
            showDialog = showPinDialog,
            onPinEntered = { pin ->
                coroutineScope.launch {
                    viewModel.verifyPin(pin)
                }
            },
            onDismiss = {
                viewModel.cancelPinEntry()
                onCancel()
            }
        )
    }

    Scaffold(
        topBar = {
            AuthenticationTopBar(
                title = title,
                onCancel = onCancel
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        AuthenticationContent(
            title = title,
            description = description,
            errorMessage = errorMessage,
            onBiometricClick = {
                if (activity != null) {
                    authenticateWithBiometric()
                } else {
                    viewModel.setErrorMessage("Biometric authentication not available")
                }
            },
            onPinClick = { viewModel.showPinDialog() },
            onCancel = onCancel,
            modifier = Modifier.padding(paddingValues)
        )
    }

    // Handle authentication result
    LaunchedEffect(authenticationResult) {
        val result = authenticationResult
        when (result) {
            is Result.Success<AuthType> -> {
                onAuthenticated()
                viewModel.clearState()
            }
            is Result.Error -> {
                viewModel.setErrorMessage(result.message)
            }
            else -> {
                // Do nothing for Loading or null
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AuthenticationTopBar(
    title: String,
    onCancel: () -> Unit
) {
    TopAppBar(
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface
            )
        },
        navigationIcon = {
            IconButton(onClick = onCancel) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
            scrolledContainerColor = MaterialTheme.colorScheme.surface
        )
    )
}

@Composable
private fun AuthenticationContent(
    title: String,
    description: String,
    errorMessage: String?,
    onBiometricClick: () -> Unit,
    onPinClick: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Header Card
        AuthenticationHeaderCard(
            title = title,
            description = description
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Error Card
        errorMessage?.let { message ->
            AuthenticationErrorCard(message = message)
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Authentication Methods Card
        AuthenticationMethodsCard(
            onBiometricClick = onBiometricClick,
            onPinClick = onPinClick
        )

        Spacer(modifier = Modifier.weight(1f))

        // Cancel button
        TextButton(
            onClick = onCancel,
            modifier = Modifier.padding(vertical = 8.dp)
        ) {
            Text(
                text = "Cancel",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun AuthenticationHeaderCard(
    title: String,
    description: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Icon with background
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Security,
                    contentDescription = "Authentication",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Title
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Description
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun AuthenticationErrorCard(
    message: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        ),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Error,
                contentDescription = "Error",
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = message,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun AuthenticationMethodsCard(
    onBiometricClick: () -> Unit,
    onPinClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            Text(
                text = "Choose Authentication Method",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            // Biometric button
            Button(
                onClick = onBiometricClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(
                    imageVector = Icons.Default.Fingerprint,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onPrimary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Use Biometric",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // OR Divider
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Divider(
                    modifier = Modifier.weight(1f),
                    color = MaterialTheme.colorScheme.outline,
                    thickness = 1.dp
                )
                Text(
                    text = "OR",
                    modifier = Modifier.padding(horizontal = 16.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Divider(
                    modifier = Modifier.weight(1f),
                    color = MaterialTheme.colorScheme.outline,
                    thickness = 1.dp
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // PIN button
            OutlinedButton(
                onClick = onPinClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.primary
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Icon(
                    imageVector = Icons.Default.Pin,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Use PIN",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun AuthenticationPinDialog(
    showDialog: Boolean,
    onPinEntered: (String) -> Unit,
    onDismiss: () -> Unit
) {
    PinEntryDialog(
        showDialog = showDialog,
        title = "Enter PIN",
        subtitle = "Enter your PIN to continue",
        onPinEntered = onPinEntered,
        onDismiss = onDismiss
    )
}

@Composable
fun rememberBiometricAuthenticator(
    title: String,
    subtitle: String? = null,
    description: String? = null,
    onSuccess: () -> Unit,
    onError: (String) -> Unit,
    onFailed: () -> Unit
): () -> Unit {
    val context = LocalContext.current
    val activity = context as? FragmentActivity

    return remember {
        {
            if (activity == null) {
                onError("Activity context is required for biometric prompt.")
                return@remember
            }

            val executor = ContextCompat.getMainExecutor(context)
            val biometricPrompt = BiometricPrompt(
                activity,
                executor,
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        super.onAuthenticationSucceeded(result)
                        onSuccess()
                    }

                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                        super.onAuthenticationError(errorCode, errString)
                        val message = when (errorCode) {
                            BiometricPrompt.ERROR_CANCELED,
                            BiometricPrompt.ERROR_USER_CANCELED -> "Authentication cancelled"
                            BiometricPrompt.ERROR_LOCKOUT -> "Too many failed attempts. Try again later."
                            BiometricPrompt.ERROR_LOCKOUT_PERMANENT -> "Biometric authentication permanently locked."
                            else -> errString.toString()
                        }
                        onError(message)
                    }

                    override fun onAuthenticationFailed() {
                        super.onAuthenticationFailed()
                        onFailed()
                    }
                })

            val promptInfo = BiometricPrompt.PromptInfo.Builder()
                .setTitle(title)
                .apply {
                    subtitle?.let { setSubtitle(it) }
                    description?.let { setDescription(it) }
                }
                .setAllowedAuthenticators(BIOMETRIC_STRONG or DEVICE_CREDENTIAL)
                .setConfirmationRequired(false)
                .setNegativeButtonText("Use PIN")
                .build()

            biometricPrompt.authenticate(promptInfo)
        }
    }
}

private fun checkBiometricAvailability(
    activity: FragmentActivity,
    viewModel: AuthenticationViewModel
) {
    val biometricManager = BiometricManager.from(activity)
    when (biometricManager.canAuthenticate(BIOMETRIC_STRONG or DEVICE_CREDENTIAL)) {
        BiometricManager.BIOMETRIC_SUCCESS -> {
            // Biometric is available, no action needed
        }
        BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> {
            viewModel.setErrorMessage("Biometric hardware is not available on this device")
        }
        BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> {
            viewModel.setErrorMessage("Biometric hardware is currently unavailable")
        }
        BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> {
            viewModel.setErrorMessage("No biometric credentials enrolled. Please set up fingerprint or face unlock in settings.")
        }
        BiometricManager.BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED -> {
            viewModel.setErrorMessage("A security update is required for biometric authentication")
        }
        BiometricManager.BIOMETRIC_ERROR_UNSUPPORTED -> {
            viewModel.setErrorMessage("Biometric authentication is not supported on this device")
        }
        BiometricManager.BIOMETRIC_STATUS_UNKNOWN -> {
            viewModel.setErrorMessage("Biometric status unknown")
        }
    }
}