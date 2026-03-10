package com.example.nexuswallet.feature.wallet.ui


import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.Error
import androidx.compose.material.icons.outlined.FlashOn
import androidx.compose.material.icons.outlined.Lan
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Token
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.nexuswallet.R
import com.example.nexuswallet.feature.coin.CoinType
import com.example.nexuswallet.feature.coin.NetworkType
import com.example.nexuswallet.feature.coin.SendValidationResult
import com.example.nexuswallet.feature.coin.bitcoin.BitcoinFeeEstimate
import com.example.nexuswallet.feature.coin.bitcoin.FeeLevel
import com.example.nexuswallet.feature.coin.ethereum.EVMFeeEstimate
import com.example.nexuswallet.feature.coin.solana.SolanaFeeEstimate
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.EVMToken
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.EthereumNetwork
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.NativeETH
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.USDCToken
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.USDTToken
import com.example.nexuswallet.ui.theme.bitcoinLight
import com.example.nexuswallet.ui.theme.ethereumLight
import com.example.nexuswallet.ui.theme.solanaLight
import com.example.nexuswallet.ui.theme.success
import com.example.nexuswallet.ui.theme.usdcLight
import com.example.nexuswallet.ui.theme.warning
import java.math.BigDecimal
import java.math.RoundingMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SendTopBar(
    title: String,
    iconRes: Int,
    coinColor: Color,
    isLoading: Boolean,
    onNavigateUp: () -> Unit
) {
    TopAppBar(
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painter = painterResource(id = iconRes),
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = Color.Unspecified
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        },
        navigationIcon = {
            IconButton(onClick = onNavigateUp) {
                Icon(
                    Icons.Default.ArrowBack,
                    "Back",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        actions = {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary
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
fun NetworkSelectorCard(
    currentNetwork: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Outlined.Lan,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Network",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = currentNetwork,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Icon(
                imageVector = Icons.Outlined.ChevronRight,
                contentDescription = "Change network",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun NetworkSelectorDialog(
    availableNetworks: List<NetworkType>,
    currentNetwork: String,
    onNetworkSelected: (NetworkType) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(20.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        title = {
            Text(
                text = "Select Network",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        },
        text = {
            LazyColumn(
                modifier = Modifier.heightIn(max = 300.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(availableNetworks) { network ->
                    val networkName = network.displayName
                    val isSelected = networkName == currentNetwork

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onNetworkSelected(network) },
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected)
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                            else
                                MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = networkName,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected)
                                    MaterialTheme.colorScheme.primary
                                else
                                    MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Outlined.CheckCircle,
                                    contentDescription = "Selected",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDismiss,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun SendBalanceCard(
    balance: BigDecimal,
    balanceFormatted: String,
    coinColor: Color,
    iconRes: Int,
    address: String,
    network: String? = null
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Available Balance",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    val usdValue = String.format("%.2f", balance.toDouble() * getUsdRate(coinColor))

                    Text(
                        text = "$$usdValue",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Text(
                        text = balanceFormatted,
                        style = MaterialTheme.typography.bodyMedium,
                        color = coinColor
                    )

                    if (network != null && network != "MAINNET" && network != "Mainnet") {
                        Text(
                            text = "Network: $network",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(coinColor.copy(alpha = 0.1f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(id = iconRes),
                        contentDescription = null,
                        tint = Color.Unspecified,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.AccountBalanceWallet,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "From: ${address.take(6)}...${address.takeLast(4)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
fun SendAddressInput(
    toAddress: String,
    onAddressChange: (String) -> Unit,
    onFocusChange: (Boolean) -> Unit = {},
    placeholder: String,
    isValid: Boolean = true,
    errorMessage: String? = null,
    onPaste: (String) -> Unit,
    focusRequester: FocusRequester = FocusRequester()
) {
    val focusManager = LocalFocusManager.current
    val context = LocalContext.current
    var focused by remember { mutableStateOf(false) }

    LaunchedEffect(focused) {
        onFocusChange(focused)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Recipient Address",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = toAddress,
                onValueChange = onAddressChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
                    .onFocusChanged { focusState ->
                        focused = focusState.isFocused
                    },
                placeholder = {
                    Text(
                        text = placeholder,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                shape = RoundedCornerShape(12.dp),
                singleLine = true,
                isError = toAddress.isNotEmpty() && !isValid,
                supportingText = if (errorMessage != null) {
                    {
                        Text(
                            errorMessage,
                            color = MaterialTheme.colorScheme.error,
                            maxLines = 1,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                } else null,
                trailingIcon = {
                    if (toAddress.isNotEmpty()) {
                        IconButton(
                            onClick = { onAddressChange("") },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Close,
                                contentDescription = "Clear",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = if (isValid) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                    cursorColor = MaterialTheme.colorScheme.primary
                ),
                keyboardOptions = KeyboardOptions.Default.copy(
                    imeAction = ImeAction.Next
                ),
                keyboardActions = KeyboardActions(
                    onNext = {
                        focusManager.moveFocus(FocusDirection.Down)
                    }
                )
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(
                    onClick = { /* Scan QR code */ },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(
                        imageVector = Icons.Outlined.QrCodeScanner,
                        contentDescription = "Scan",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        "Scan",
                        maxLines = 1,
                        style = MaterialTheme.typography.labelLarge
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                TextButton(
                    onClick = {
                        val clipboard =
                            context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = clipboard.primaryClip
                        val pastedText = clip?.getItemAt(0)?.text?.toString()
                        if (!pastedText.isNullOrBlank()) {
                            onPaste(pastedText)
                        }
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(
                        imageVector = Icons.Outlined.ContentPaste,
                        contentDescription = "Paste",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        "Paste",
                        maxLines = 1,
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }
    }
}

@Composable
fun SendAmountInput(
    amount: String,
    onAmountChange: (String) -> Unit,
    onFocusChange: (Boolean) -> Unit = {},
    balance: BigDecimal,
    symbol: String,
    coinColor: Color,
    coinType: CoinType,
    onMaxClick: () -> Unit,
    errorMessage: String? = null,
    focusRequester: FocusRequester = FocusRequester()
) {
    val focusManager = LocalFocusManager.current
    var focused by remember { mutableStateOf(false) }

    LaunchedEffect(focused) {
        onFocusChange(focused)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Amount",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.weight(1f))

                Text(
                    text = "Max: ${
                        balance.setScale(8, RoundingMode.HALF_UP).stripTrailingZeros()
                            .toPlainString()
                    } $symbol",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    OutlinedTextField(
                        value = amount,
                        onValueChange = { newValue ->
                            if (newValue.matches(Regex("^\\d*\\.?\\d*\$"))) {
                                onAmountChange(newValue)
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester)
                            .onFocusChanged { focusState ->
                                focused = focusState.isFocused
                            },
                        placeholder = {
                            Text(
                                "0.00000000",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                maxLines = 1
                            )
                        },
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true,
                        isError = errorMessage != null,
                        supportingText = if (errorMessage != null) {
                            {
                                Text(
                                    errorMessage,
                                    color = MaterialTheme.colorScheme.error,
                                    maxLines = 1,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        } else null,
                        trailingIcon = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (amount.isNotEmpty()) {
                                    IconButton(
                                        onClick = { onAmountChange("") },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.Close,
                                            contentDescription = "Clear",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                                Text(
                                    text = symbol,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    color = coinColor,
                                    modifier = Modifier.padding(end = 12.dp)
                                )
                            }
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = if (errorMessage == null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                            cursorColor = MaterialTheme.colorScheme.primary,
                            focusedTrailingIconColor = coinColor,
                            unfocusedTrailingIconColor = coinColor
                        ),
                        keyboardOptions = KeyboardOptions.Default.copy(
                            keyboardType = KeyboardType.Decimal,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                focusManager.clearFocus()
                            }
                        )
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Button(
                    onClick = onMaxClick,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    ),
                    modifier = Modifier.height(56.dp)
                ) {
                    Text(
                        "MAX",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }

            if (amount.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))

                val amountValue = try {
                    BigDecimal(amount)
                } catch (e: Exception) {
                    BigDecimal.ZERO
                }

                val usdPrice = when (coinType) {
                    CoinType.BITCOIN -> 45000.0
                    CoinType.ETHEREUM -> 3000.0
                    CoinType.SOLANA -> 30.0
                    CoinType.USDC -> 1.0
                }

                val usdAmount = amountValue.toDouble() * usdPrice

                Text(
                    text = "≈ $${String.format("%.2f", usdAmount)} USD",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.End,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
fun SendFeeSelection(
    feeLevel: FeeLevel,
    onFeeLevelChange: (FeeLevel) -> Unit,
    feeEstimate: Any?,
    coinType: CoinType
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Transaction Fee",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(12.dp))

            FeeLevelButtons(
                selectedLevel = feeLevel,
                onLevelSelected = onFeeLevelChange
            )

            Spacer(modifier = Modifier.height(12.dp))

            when (coinType) {
                CoinType.BITCOIN -> {
                    (feeEstimate as? BitcoinFeeEstimate)?.let { fee ->
                        FeeDetailsRow(
                            label = "Network Fee",
                            value = "${fee.totalFeeBtc} BTC"
                        )
                        FeeDetailsRow(
                            label = "Fee Rate",
                            value = "${fee.feePerByte} sat/byte"
                        )
                    }
                }

                CoinType.ETHEREUM, CoinType.USDC -> {
                    (feeEstimate as? EVMFeeEstimate)?.let { fee ->
                        FeeDetailsRow(
                            label = "Network Fee",
                            value = "${fee.totalFeeEth} ETH"
                        )
                        FeeDetailsRow(
                            label = "Gas Price",
                            value = "${fee.gasPriceGwei} Gwei"
                        )
                        FeeDetailsRow(
                            label = "Gas Limit",
                            value = fee.gasLimit.toString()
                        )
                    }
                }

                CoinType.SOLANA -> {
                    (feeEstimate as? SolanaFeeEstimate)?.let { fee ->
                        FeeDetailsRow(
                            label = "Network Fee",
                            value = "${fee.feeSol} SOL"
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun FeeLevelButtons(
    selectedLevel: FeeLevel,
    onLevelSelected: (FeeLevel) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FeeLevelButton(
            level = FeeLevel.SLOW,
            selected = selectedLevel == FeeLevel.SLOW,
            onClick = { onLevelSelected(FeeLevel.SLOW) },
            color = MaterialTheme.colorScheme.success,
            modifier = Modifier.weight(1f)
        )

        FeeLevelButton(
            level = FeeLevel.NORMAL,
            selected = selectedLevel == FeeLevel.NORMAL,
            onClick = { onLevelSelected(FeeLevel.NORMAL) },
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f)
        )

        FeeLevelButton(
            level = FeeLevel.FAST,
            selected = selectedLevel == FeeLevel.FAST,
            onClick = { onLevelSelected(FeeLevel.FAST) },
            color = MaterialTheme.colorScheme.warning,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
fun FeeLevelButton(
    level: FeeLevel,
    selected: Boolean,
    onClick: () -> Unit,
    color: Color,
    modifier: Modifier = Modifier
) {
    val (text, icon) = when (level) {
        FeeLevel.SLOW -> Pair("Slow", Icons.Outlined.Schedule)
        FeeLevel.NORMAL -> Pair("Normal", Icons.Outlined.Speed)
        FeeLevel.FAST -> Pair("Fast", Icons.Outlined.FlashOn)
    }

    Card(
        modifier = modifier
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) color.copy(alpha = 0.1f) else MaterialTheme.colorScheme.surfaceVariant
        ),
        border = if (selected) BorderStroke(1.dp, color) else null
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = text,
                tint = if (selected) color else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                color = if (selected) color else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun FeeDetailsRow(
    label: String,
    value: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
fun SendBottomBar(
    isValid: Boolean,
    isLoading: Boolean,
    error: String? = null,
    onSend: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 8.dp
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            error?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    textAlign = TextAlign.Center
                )
            }

            Button(
                onClick = onSend,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                enabled = isValid && !isLoading,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    disabledContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "Processing...",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text(
                        text = "Continue",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
        }
    }
}

@Composable
fun MaxAmountDialog(
    balance: BigDecimal,
    feeEstimate: Any?,
    tokenSymbol: String,
    coinType: CoinType,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    val fee = when (feeEstimate) {
        is BitcoinFeeEstimate -> feeEstimate.totalFeeBtc.toBigDecimalOrNull()
            ?: BigDecimal("0.00001")

        is EVMFeeEstimate -> feeEstimate.totalFeeEth.toBigDecimalOrNull() ?: BigDecimal("0.001")
        is SolanaFeeEstimate -> feeEstimate.feeSol.toBigDecimalOrNull() ?: BigDecimal("0.000005")
        else -> when (coinType) {
            CoinType.BITCOIN -> BigDecimal("0.00001")
            CoinType.ETHEREUM, CoinType.USDC -> BigDecimal("0.001")
            CoinType.SOLANA -> BigDecimal("0.000005")
        }
    }

    val maxAmount = balance - fee

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(20.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        title = {
            Text(
                text = "Send Maximum",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        },
        text = {
            Column {
                if (maxAmount > BigDecimal.ZERO) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Available:",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "${
                                balance.setScale(8, RoundingMode.HALF_UP).stripTrailingZeros()
                                    .toPlainString()
                            } $tokenSymbol",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Network Fee:",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "- ${
                                fee.setScale(8, RoundingMode.HALF_UP).stripTrailingZeros()
                                    .toPlainString()
                            } $tokenSymbol",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.error
                        )
                    }

                    Divider(
                        modifier = Modifier.padding(vertical = 8.dp),
                        color = MaterialTheme.colorScheme.outline,
                        thickness = 1.dp
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Maximum Send:",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "${
                                maxAmount.setScale(8, RoundingMode.HALF_UP).stripTrailingZeros()
                                    .toPlainString()
                            } $tokenSymbol",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "This will send all available funds minus the network fee.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Text(
                        text = "Insufficient balance to cover network fee.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            if (maxAmount > BigDecimal.ZERO) {
                Button(
                    onClick = { onConfirm(maxAmount.toPlainString()) },
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text(
                        "Use Maximum",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            ) {
                Text("Cancel", style = MaterialTheme.typography.labelLarge)
            }
        }
    )
}

@Composable
fun ErrorMessage(
    error: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        ),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Error,
                    contentDescription = "Error",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = error,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            IconButton(
                onClick = onDismiss,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Close,
                    contentDescription = "Dismiss",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

// Helper to get USD rate based on coin color TODO: need proper price fetching
private fun getUsdRate(coinColor: Color): Double {
    return when (coinColor) {
        bitcoinLight -> 45000.0
        ethereumLight -> 3000.0
        solanaLight -> 30.0
        usdcLight -> 1.0
        else -> 1.0
    }
}

@Composable
fun MaxAmountDialog(
    balance: BigDecimal,
    feeEstimate: Any?,
    tokenSymbol: String,
    coinType: CoinType,
    token: EVMToken? = null,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    val fee = when (feeEstimate) {
        is BitcoinFeeEstimate -> feeEstimate.totalFeeBtc.toBigDecimalOrNull() ?: BigDecimal("0.00001")
        is EVMFeeEstimate -> feeEstimate.totalFeeEth.toBigDecimalOrNull() ?: BigDecimal("0.001")
        is SolanaFeeEstimate -> feeEstimate.feeSol.toBigDecimalOrNull() ?: BigDecimal("0.000005")
        else -> when (coinType) {
            CoinType.BITCOIN -> BigDecimal("0.00001")
            CoinType.ETHEREUM, CoinType.USDC -> BigDecimal("0.001")
            CoinType.SOLANA -> BigDecimal("0.000005")
        }
    }

    val decimals = when {
        token != null && token.decimals == 6 -> 2
        coinType == CoinType.BITCOIN -> 8
        coinType == CoinType.SOLANA -> 9
        else -> 6
    }

    val maxAmount = balance - fee

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(20.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        title = {
            Text(
                text = "Send Maximum",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        },
        text = {
            Column {
                if (maxAmount > BigDecimal.ZERO) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Available:",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "${
                                balance.setScale(decimals, RoundingMode.HALF_UP).stripTrailingZeros()
                                    .toPlainString()
                            } $tokenSymbol",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Network Fee:",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "- ${
                                fee.setScale(decimals, RoundingMode.HALF_UP).stripTrailingZeros()
                                    .toPlainString()
                            } $tokenSymbol",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.error
                        )
                    }

                    Divider(
                        modifier = Modifier.padding(vertical = 8.dp),
                        color = MaterialTheme.colorScheme.outline,
                        thickness = 1.dp
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Maximum Send:",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "${
                                maxAmount.setScale(decimals, RoundingMode.HALF_UP).stripTrailingZeros()
                                    .toPlainString()
                            } $tokenSymbol",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "This will send all available funds minus the network fee.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Text(
                        text = "Insufficient balance to cover network fee.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            if (maxAmount > BigDecimal.ZERO) {
                Button(
                    onClick = { onConfirm(maxAmount.toPlainString()) },
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text(
                        "Use Maximum",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            ) {
                Text("Cancel", style = MaterialTheme.typography.labelLarge)
            }
        }
    )
}

@Composable
fun SendFeeSelection(
    feeLevel: FeeLevel,
    onFeeLevelChange: (FeeLevel) -> Unit,
    feeEstimate: Any?,
    coinType: CoinType,
    token: EVMToken? = null
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Transaction Fee",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(12.dp))

            FeeLevelButtons(
                selectedLevel = feeLevel,
                onLevelSelected = onFeeLevelChange
            )

            Spacer(modifier = Modifier.height(12.dp))

            when (coinType) {
                CoinType.BITCOIN -> {
                    (feeEstimate as? BitcoinFeeEstimate)?.let { fee ->
                        FeeDetailsRow(
                            label = "Network Fee",
                            value = "${fee.totalFeeBtc} BTC"
                        )
                        FeeDetailsRow(
                            label = "Fee Rate",
                            value = "${fee.feePerByte} sat/byte"
                        )
                    }
                }
                CoinType.ETHEREUM, CoinType.USDC -> {
                    (feeEstimate as? EVMFeeEstimate)?.let { fee ->
                        FeeDetailsRow(
                            label = "Network Fee",
                            value = "${fee.totalFeeEth} ETH"
                        )
                        FeeDetailsRow(
                            label = "Gas Price",
                            value = "${fee.gasPriceGwei} Gwei"
                        )
                        FeeDetailsRow(
                            label = "Gas Limit",
                            value = fee.gasLimit.toString()
                        )
                    }
                }
                CoinType.SOLANA -> {
                    (feeEstimate as? SolanaFeeEstimate)?.let { fee ->
                        FeeDetailsRow(
                            label = "Network Fee",
                            value = "${fee.feeSol} SOL"
                        )
                        if (fee.computeUnits > 0) {
                            FeeDetailsRow(
                                label = "Compute Units",
                                value = fee.computeUnits.toString()
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun rememberSendErrorState(
    validationResult: SendValidationResult,
    addressTouched: Boolean,
    amountTouched: Boolean,
    addressFocused: Boolean,
    amountFocused: Boolean
): SendErrorState {

    val showAddressError = !addressFocused && addressTouched && validationResult.addressError != null
    val showSelfSendError = !addressFocused && addressTouched && validationResult.selfSendError != null

    // Only show amount errors if amount field has been touched
    val showAmountError = !amountFocused && amountTouched && validationResult.amountError != null
    val showBalanceError = !amountFocused && amountTouched && validationResult.balanceError != null
    val showGasError = !amountFocused && amountTouched && validationResult.gasError != null

    val activeError = when {
        !addressFocused && !amountFocused -> {
            when {
                showSelfSendError -> validationResult.selfSendError
                showAddressError -> validationResult.addressError
                showGasError -> validationResult.gasError
                showAmountError -> validationResult.amountError
                showBalanceError -> validationResult.balanceError
                else -> null
            }
        }
        else -> null
    }

    return SendErrorState(
        showAddressError = showAddressError,
        showAmountError = showAmountError,
        showBalanceError = showBalanceError,
        showSelfSendError = showSelfSendError,
        showGasError = showGasError,
        activeError = activeError,
        addressErrorMessage = when {
            showSelfSendError -> validationResult.selfSendError
            showAddressError -> validationResult.addressError
            else -> null
        },
        amountErrorMessage = when {
            showGasError -> validationResult.gasError
            showAmountError -> validationResult.amountError
            showBalanceError -> validationResult.balanceError
            else -> null
        }
    )
}

data class SendErrorState(
    val showAddressError: Boolean = false,
    val showAmountError: Boolean = false,
    val showBalanceError: Boolean = false,
    val showSelfSendError: Boolean = false,
    val showGasError: Boolean = false,
    val activeError: String? = null,
    val addressErrorMessage: String? = null,
    val amountErrorMessage: String? = null
)
@Composable
fun TokenSelectorCard(
    selectedToken: EVMToken?,
    onClick: () -> Unit
) {
    val (iconRes, color, displayName) = when (selectedToken) {
        is NativeETH -> Triple(R.drawable.ethereum, ethereumLight, "Ethereum")
        is USDCToken -> Triple(R.drawable.usdc, usdcLight, "USD Coin")
        is USDTToken -> Triple(R.drawable.usdc, usdtLight, "Tether USD")
        else -> Triple(null, MaterialTheme.colorScheme.primary, "Select token")
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                if (iconRes != null) {
                    Icon(
                        painter = painterResource(id = iconRes),
                        contentDescription = null,
                        tint = Color.Unspecified,
                        modifier = Modifier.size(20.dp)
                    )
                } else {
                    Icon(
                        imageVector = Icons.Outlined.Token,
                        contentDescription = null,
                        tint = color,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Token",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = displayName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Icon(
                imageVector = Icons.Outlined.ChevronRight,
                contentDescription = "Change token",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun TokenSelectorDialog(
    availableTokens: List<EVMToken>,
    selectedToken: EVMToken?,
    onTokenSelected: (EVMToken) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(20.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        title = {
            Text(
                text = "Select Token",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        },
        text = {
            LazyColumn(
                modifier = Modifier.heightIn(max = 300.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(availableTokens) { token ->
                    val isSelected = token == selectedToken
                    val (iconRes, color, displayName) = when (token) {
                        is NativeETH -> Triple(R.drawable.ethereum, ethereumLight, "Ethereum")
                        is USDCToken -> Triple(R.drawable.usdc, usdcLight, "USD Coin")
                        is USDTToken -> Triple(R.drawable.usdc, usdtLight, "Tether USD")
                        else -> Triple(null, MaterialTheme.colorScheme.primary, token.name)
                    }

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onTokenSelected(token) },
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected)
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                            else
                                MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(color.copy(alpha = 0.1f)),
                                contentAlignment = Alignment.Center
                            ) {
                                if (iconRes != null) {
                                    Icon(
                                        painter = painterResource(id = iconRes),
                                        contentDescription = null,
                                        tint = Color.Unspecified,
                                        modifier = Modifier.size(20.dp)
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Outlined.Token,
                                        contentDescription = null,
                                        tint = color,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = token.symbol,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected)
                                        MaterialTheme.colorScheme.primary
                                    else
                                        MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = displayName,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (token.network != EthereumNetwork.Mainnet) {
                                    Text(
                                        text = token.network.displayName,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                    )
                                }
                            }
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Outlined.CheckCircle,
                                    contentDescription = "Selected",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDismiss,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Text("Cancel")
            }
        }
    )
}
