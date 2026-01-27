package com.example.nexuswallet.feature.authentication.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Pin
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.nexuswallet.NexusWalletApplication
import com.example.nexuswallet.feature.authentication.domain.AuthenticationManager
import com.example.nexuswallet.feature.authentication.domain.AuthenticationResult
import com.example.nexuswallet.getFragmentActivity
import kotlinx.coroutines.launch

@Composable
fun AuthenticationRequiredScreen(
    onAuthenticated: () -> Unit,
    onCancel: () -> Unit,
    title: String = "Authentication Required",
    description: String = "Please authenticate to access this feature"
) {
    val context = LocalContext.current
    val authenticationManager = remember { AuthenticationManager(context) }
    val viewModel: AuthenticationViewModel = hiltViewModel()

    val authenticationState by viewModel.authenticationState.collectAsState()
    val showPinDialog by viewModel.showPinDialog.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()

    val coroutineScope = rememberCoroutineScope()
    val activity = getFragmentActivity()

    // Show PIN dialog when required
    PinEntryDialog(
        showDialog = showPinDialog,
        title = "Enter PIN",
        subtitle = "Enter your PIN to continue",
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Icon
        Icon(
            imageVector = Icons.Default.Security,
            contentDescription = "Authentication",
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Title
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Description
        Text(
            text = description,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Error message
        errorMessage?.let { message ->
            Text(
                text = message,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Authentication methods
        AuthenticationMethods(
            onBiometricClick = {
                activity?.let {
                    viewModel.authenticateWithBiometric(it)
                } ?: run {
                    viewModel.setErrorMessage("Unable to start biometric authentication")
                }
            },
            onPinClick = {
                viewModel.showPinDialog()
            }
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Cancel button
        TextButton(onClick = onCancel) {
            Text("Cancel")
        }
    }

    // Handle authentication result
    LaunchedEffect(authenticationState) {
        when (authenticationState) {
            is AuthenticationResult.Success -> {

                onAuthenticated()
            }
            is AuthenticationResult.Error -> {
                viewModel.setErrorMessage((authenticationState as AuthenticationResult.Error).message)
            }
            else -> {
                // Do nothing
            }
        }
    }
}

@Composable
fun AuthenticationMethods(
    onBiometricClick: () -> Unit,
    onPinClick: () -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        // Biometric button
        Button(
            onClick = onBiometricClick,
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium
        ) {
            Icon(
                imageVector = Icons.Default.Fingerprint,
                contentDescription = "Biometric",
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("Use Biometric")
        }

        // OR divider
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Divider(modifier = Modifier.weight(1f))
            Text(
                text = "OR",
                modifier = Modifier.padding(horizontal = 16.dp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Divider(modifier = Modifier.weight(1f))
        }

        // PIN button
        OutlinedButton(
            onClick = onPinClick,
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium
        ) {
            Icon(
                imageVector = Icons.Default.Pin,
                contentDescription = "PIN",
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("Use PIN")
        }
    }
}