package com.example.nexuswallet.feature.wallet.ui.common

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.nexuswallet.R
import com.example.nexuswallet.feature.ethereum.domain.model.EVMTokenType
import com.example.nexuswallet.feature.wallet.domain.model.BitcoinNetwork
import com.example.nexuswallet.feature.wallet.domain.model.EthereumNetwork
import com.example.nexuswallet.feature.wallet.domain.model.Network
import com.example.nexuswallet.feature.wallet.domain.model.SolanaNetwork
import com.example.nexuswallet.ui.theme.bitcoinLight
import com.example.nexuswallet.ui.theme.ethereumLight
import com.example.nexuswallet.ui.theme.solanaLight
import com.example.nexuswallet.ui.theme.usdcLight
import com.example.nexuswallet.ui.theme.usdtLight

@Composable
fun SectionHeader(title: String, color: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(4.dp, 16.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(color)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.ExtraBold,
            color = color,
            letterSpacing = 1.sp
        )
    }
}

@Composable
fun NetworkToggleCard(
    iconRes: Int,
    color: Color,
    network: Network,
    coinName: String,
    coinSymbol: String,
    isSelected: Boolean,
    onSelectedChange: (Boolean) -> Unit
) {
    val backgroundColor by animateColorAsState(
        targetValue = if (isSelected) color.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surface,
        label = "card_bg"
    )
    val borderColor by animateColorAsState(
        targetValue = if (isSelected) color else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
        label = "card_border"
    )

    Surface(
        onClick = { onSelectedChange(!isSelected) },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = backgroundColor,
        border = BorderStroke(if (isSelected) 2.dp else 1.dp, borderColor),
        tonalElevation = if (isSelected) 4.dp else 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(if (isSelected) color.copy(alpha = 0.2f) else color.copy(alpha = 0.05f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(id = iconRes),
                    contentDescription = coinName,
                    tint = Color.Unspecified,
                    modifier = Modifier.size(32.dp)
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = coinName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (isSelected) color else MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "$coinSymbol • ${network.name}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Checkbox(
                checked = isSelected,
                onCheckedChange = null,
                colors = CustomCheckboxDefaults.colors(
                    checkedBackgroundColor = color,
                    checkedBorderColor = color
                )
            )
        }
    }
}

@Composable
fun TokenToggleCard(
    iconRes: Int,
    color: Color,
    network: EthereumNetwork,
    evmTokenType: EVMTokenType,
    tokenName: String,
    tokenSymbol: String,
    isSelected: Boolean,
    networkEnabled: Boolean,
    onSelectedChange: (Boolean) -> Unit
) {
    val backgroundColor by animateColorAsState(
        targetValue = if (isSelected && networkEnabled) color.copy(alpha = 0.12f) 
                     else if (!networkEnabled) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                     else MaterialTheme.colorScheme.surface,
        label = "token_card_bg"
    )
    val borderColor by animateColorAsState(
        targetValue = if (isSelected && networkEnabled) color 
                     else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
        label = "token_card_border"
    )

    Surface(
        onClick = { if (networkEnabled) onSelectedChange(!isSelected) },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = backgroundColor,
        border = BorderStroke(if (isSelected && networkEnabled) 2.dp else 1.dp, borderColor),
        enabled = networkEnabled,
        tonalElevation = if (isSelected && networkEnabled) 4.dp else 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(
                        if (isSelected && networkEnabled) color.copy(alpha = 0.2f) 
                        else if (!networkEnabled) Color.Transparent
                        else color.copy(alpha = 0.05f)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(id = iconRes),
                    contentDescription = tokenName,
                    tint = if (networkEnabled) Color.Unspecified else Color.Gray.copy(alpha = 0.5f),
                    modifier = Modifier.size(32.dp)
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = tokenName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (isSelected && networkEnabled) color
                    else if (!networkEnabled) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    else MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "$tokenSymbol on ${network.name}",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (!networkEnabled) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (!networkEnabled) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                )
            } else {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = null,
                    colors = CustomCheckboxDefaults.colors(
                        checkedBackgroundColor = color,
                        checkedBorderColor = color
                    )
                )
            }
        }
    }
}

@Composable
fun AssetSummaryCard(
    hasSelections: Boolean,
    selectedNetworks: Set<Network>,
    selectedTokens: Map<EthereumNetwork, Set<EVMTokenType>>
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.selected_assets),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (!hasSelections) {
                Text(
                    text = stringResource(R.string.no_assets_selected),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                    fontStyle = FontStyle.Italic
                )
            } else {
                OptIn(ExperimentalLayoutApi::class)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Networks
                    selectedNetworks.forEach { network ->
                        val (name, color) = when (network) {
                            is BitcoinNetwork -> "BTC" to bitcoinLight
                            is EthereumNetwork -> "ETH" to ethereumLight
                            is SolanaNetwork -> "SOL" to solanaLight
                        }
                        val type = if (network.isTestnet) {
                            if (network is SolanaNetwork) " (Dev)" else " (Test)"
                        } else ""
                        AssetChip(text = "$name$type", color = color)
                    }

                    // Tokens
                    selectedTokens.forEach { (network, tokenTypes) ->
                        tokenTypes
                            .filter { it != EVMTokenType.NATIVE }
                            .forEach { tokenType ->
                                val (name, color) = when (tokenType) {
                                    EVMTokenType.USDC -> "USDC" to usdcLight
                                    EVMTokenType.USDT -> "USDT" to usdtLight
                                    else -> "Token" to MaterialTheme.colorScheme.primary
                                }
                                val type = if (network.isTestnet) " (Test)" else ""
                                AssetChip(text = "$name$type", color = color)
                            }
                    }
                }
            }
        }
    }
}

@Composable
fun AssetChip(text: String, color: Color) {
    Surface(
        color = color.copy(alpha = 0.1f),
        shape = RoundedCornerShape(6.dp),
        border = BorderStroke(1.dp, color.copy(alpha = 0.25f))
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            fontSize = 10.sp,
            fontWeight = FontWeight.ExtraBold,
            color = color
        )
    }
}

@Composable
fun Checkbox(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)? = null,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: CustomCheckboxColors = CustomCheckboxDefaults.colors()
) {
    val interactionSource = remember { MutableInteractionSource() }
    val backgroundColor by animateColorAsState(
        targetValue = when {
            !enabled -> colors.disabledBackgroundColor
            checked -> colors.checkedBackgroundColor
            else -> colors.uncheckedBackgroundColor
        },
        label = "checkbox_bg"
    )

    val borderColor by animateColorAsState(
        targetValue = when {
            !enabled -> colors.disabledBorderColor
            checked -> colors.checkedBorderColor
            else -> colors.uncheckedBorderColor
        },
        label = "checkbox_border"
    )

    val iconTintColor by animateColorAsState(
        targetValue = when {
            !enabled -> colors.disabledIconColor
            else -> colors.checkedIconColor
        },
        label = "checkbox_icon"
    )

    val baseModifier = modifier
        .size(24.dp)
        .clip(RoundedCornerShape(6.dp))
        .background(backgroundColor)
        .border(
            width = 2.dp,
            color = borderColor,
            shape = RoundedCornerShape(6.dp)
        )

    val finalModifier = if (onCheckedChange != null) {
        baseModifier.clickable(
            enabled = enabled,
            interactionSource = interactionSource,
            indication = ripple(bounded = true)
        ) { onCheckedChange(!checked) }
    } else {
        baseModifier
    }

    Box(
        modifier = finalModifier,
        contentAlignment = Alignment.Center
    ) {
        if (checked) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = "Checked",
                tint = iconTintColor,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
fun AssetDetailItem(iconRes: Int, name: String, color: Color, isTestnet: Boolean) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 4.dp)
    ) {
        Icon(
            painter = painterResource(id = iconRes),
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = Color.Unspecified
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = name + if (isTestnet) " (Testnet)" else "",
            color = color,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
fun SecurityWarningDialog(
    onAccept: () -> Unit,
    onCancel: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    Dialog(onDismissRequest = onCancel) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.error.copy(alpha = 0.1f))
                        .align(Alignment.CenterHorizontally),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(32.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = stringResource(R.string.security_warning_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = stringResource(R.string.recovery_phrase_warning),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onCancel,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(stringResource(R.string.cancel))
                    }

                    Button(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onAccept()
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text(stringResource(R.string.i_understand))
                    }
                }
            }
        }
    }
}

@Composable
fun MnemonicDisplayChip(
    word: String,
    index: Int,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier.padding(vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = index.toString(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = word,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
fun SimpleSelectedChip(
    word: String,
    index: Int,
    onRemove: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    Surface(
        onClick = {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            onRemove()
        },
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "$index.",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = word,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Spacer(modifier = Modifier.width(4.dp))
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Remove",
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

@Composable
fun SimpleWordChip(
    word: String,
    isEnabled: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = { if (isEnabled) onClick() },
        shape = RoundedCornerShape(12.dp),
        color = if (isEnabled) MaterialTheme.colorScheme.surface 
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        border = BorderStroke(
            1.dp, 
            if (isEnabled) MaterialTheme.colorScheme.outlineVariant 
            else Color.Transparent
        ),
        enabled = isEnabled
    ) {
        Text(
            text = word,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = if (isEnabled) MaterialTheme.colorScheme.onSurface 
                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        )
    }
}

@Composable
fun SafetyConfirmationItem(
    text: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Surface(
        onClick = { onCheckedChange(!checked) },
        shape = RoundedCornerShape(12.dp),
        color = if (checked) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f) 
                else Color.Transparent,
        border = BorderStroke(
            1.dp, 
            if (checked) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f) 
            else MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = checked,
                onCheckedChange = onCheckedChange
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = if (checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

data class CustomCheckboxColors(
    val checkedBackgroundColor: Color,
    val uncheckedBackgroundColor: Color,
    val disabledBackgroundColor: Color,
    val checkedBorderColor: Color,
    val uncheckedBorderColor: Color,
    val disabledBorderColor: Color,
    val checkedIconColor: Color,
    val disabledIconColor: Color
)

object CustomCheckboxDefaults {
    @Composable
    fun colors(
        checkedBackgroundColor: Color = MaterialTheme.colorScheme.primary,
        uncheckedBackgroundColor: Color = Color.Transparent,
        disabledBackgroundColor: Color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        checkedBorderColor: Color = MaterialTheme.colorScheme.primary,
        uncheckedBorderColor: Color = MaterialTheme.colorScheme.outline,
        disabledBorderColor: Color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
        checkedIconColor: Color = MaterialTheme.colorScheme.onPrimary,
        disabledIconColor: Color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
    ) = CustomCheckboxColors(
        checkedBackgroundColor = checkedBackgroundColor,
        uncheckedBackgroundColor = uncheckedBackgroundColor,
        disabledBackgroundColor = disabledBackgroundColor,
        checkedBorderColor = checkedBorderColor,
        uncheckedBorderColor = uncheckedBorderColor,
        disabledBorderColor = disabledBorderColor,
        checkedIconColor = checkedIconColor,
        disabledIconColor = disabledIconColor
    )
}
