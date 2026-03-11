package com.example.nexuswallet.feature.wallet.ui


import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.nexuswallet.feature.core.domain.model.CoinType
import com.example.nexuswallet.feature.wallet.domain.TransactionDisplayInfo
import com.example.nexuswallet.feature.wallet.domain.TransactionStatus
import com.example.nexuswallet.ui.theme.bitcoinLight
import com.example.nexuswallet.ui.theme.ethereumLight
import com.example.nexuswallet.ui.theme.solanaLight
import com.example.nexuswallet.ui.theme.success
import com.example.nexuswallet.ui.theme.usdcLight
import com.example.nexuswallet.ui.theme.warning

@Composable
fun TransactionItem(
    transaction: TransactionDisplayInfo,
    coinType: CoinType,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    val (symbol, displayName, coinColor) = when (coinType) {
        CoinType.BITCOIN -> Triple("BTC", "Bitcoin", bitcoinLight)
        CoinType.ETHEREUM -> Triple("ETH", "Ethereum", ethereumLight)
        CoinType.SOLANA -> Triple("SOL", "Solana", solanaLight)
        CoinType.USDC -> Triple("USDC", "USD Coin", usdcLight)
    }

    val (statusColor, statusBgColor) = when (transaction.status) {
        TransactionStatus.SUCCESS -> Pair(
            MaterialTheme.colorScheme.success,
            MaterialTheme.colorScheme.success.copy(alpha = 0.1f)
        )
        TransactionStatus.PENDING -> Pair(
            MaterialTheme.colorScheme.warning,
            MaterialTheme.colorScheme.warning.copy(alpha = 0.1f)
        )
        TransactionStatus.FAILED -> Pair(
            MaterialTheme.colorScheme.error,
            MaterialTheme.colorScheme.error.copy(alpha = 0.1f)
        )
    }

    // Amount color: receive = green, send = blue
    val amountColor = if (transaction.isIncoming)
        MaterialTheme.colorScheme.success
    else
        MaterialTheme.colorScheme.primary

    Card(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp, horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Status icon
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(statusBgColor),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (transaction.isIncoming)
                        Icons.Outlined.ArrowDownward
                    else
                        Icons.Outlined.ArrowUpward,
                    contentDescription = if (transaction.isIncoming) "Received" else "Sent",
                    tint = amountColor,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Transaction details
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = if (transaction.isIncoming) "Received $displayName" else "Sent $displayName",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = transaction.formattedTime,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            // Amount and status
            Column(
                horizontalAlignment = Alignment.End,
                modifier = Modifier.widthIn(min = 80.dp, max = 120.dp)
            ) {
                Text(
                    text = "${if (transaction.isIncoming) "+" else "-"}${transaction.formattedAmount} $symbol",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = amountColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(4.dp))

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(statusBgColor)
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = transaction.status.name,
                        style = MaterialTheme.typography.labelSmall,
                        color = statusColor,
                        maxLines = 1
                    )
                }
            }
        }
    }
}