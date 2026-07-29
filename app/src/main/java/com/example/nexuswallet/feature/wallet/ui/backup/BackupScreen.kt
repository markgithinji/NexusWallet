package com.example.nexuswallet.feature.wallet.ui.backup

import androidx.activity.compose.LocalActivity
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.nexuswallet.R
import com.example.nexuswallet.feature.wallet.ui.common.FullScreenLoading

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupScreen(
    walletId: String,
    onNavigateUp: () -> Unit,
    viewModel: BackupViewModel
) {
    val mnemonic by viewModel.mnemonic.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()
    val cryptoObject by viewModel.cryptoObject.collectAsStateWithLifecycle()
    val authRequest by viewModel.authRequest.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val activity = LocalActivity.current as? AppCompatActivity

    val biometricPrompt = remember(activity) {
        if (activity == null) return@remember null
        val executor = ContextCompat.getMainExecutor(context)
        BiometricPrompt(
            activity,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    viewModel.onBiometricSuccess(walletId, result)
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    // Handle error if needed
                }
            }
        )
    }

    val biometricTitle = stringResource(R.string.biometric_authentication)
    val biometricSubtitle = stringResource(R.string.reveal_recovery_phrase_subtitle)
    val biometricCancel = stringResource(R.string.cancel)

    val promptInfo = remember(biometricTitle, biometricSubtitle, biometricCancel) {
        BiometricPrompt.PromptInfo.Builder()
            .setTitle(biometricTitle)
            .setSubtitle(biometricSubtitle)
            .setNegativeButtonText(biometricCancel)
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
            .build()
    }

    LaunchedEffect(walletId) {
        viewModel.loadMnemonic(walletId)
    }

    LaunchedEffect(authRequest) {
        if (authRequest != null) {
            if (cryptoObject != null) {
                biometricPrompt?.authenticate(promptInfo, cryptoObject!!)
            } else {
                biometricPrompt?.authenticate(promptInfo)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.step_backup)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                    }
                }
            )
        }
    ) { padding ->
        if (isLoading) {
            FullScreenLoading(message = stringResource(R.string.decrypting_recovery_phrase))
        } else if (mnemonic != null) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Security Warning Card
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 24.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = stringResource(R.string.recovery_phrase_warning),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }

                Text(
                    text = stringResource(R.string.your_recovery_phrase),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    itemsIndexed(mnemonic!!) { index, word ->
                        MnemonicWordItem(index + 1, word)
                    }
                }

                Button(
                    onClick = onNavigateUp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp)
                ) {
                    Text(stringResource(R.string.saved_safely_button))
                }
            }
        } else if (errorMessage != null) {
            Box(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
                Text(
                    text = errorMessage!!,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            // Initial state or auth pending
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.authentication_required_to_view))
            }
        }
    }
}

@Composable
fun MnemonicWordItem(index: Int, word: String) {
    Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "$index.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.width(24.dp)
            )
            Text(
                text = word,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Start
            )
        }
    }
}
