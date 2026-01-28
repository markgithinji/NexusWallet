package com.example.nexuswallet.feature.wallet.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.nexuswallet.feature.wallet.data.repository.QrCodeGenerator

@Composable
fun QrCodeDisplay(
    address: String,
    modifier: Modifier = Modifier,
    size: Dp = 200.dp,
    amount: String? = null,
    label: String? = null
) {
    var qrCodeBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    val qrCodeGenerator = remember { QrCodeGenerator() }

    LaunchedEffect(address, amount, label) {
        isLoading = true
        val qrContent = qrCodeGenerator.generateReceiveQrCode(
            address = address,
            amount = amount,
            label = label
        )
        qrCodeBitmap = qrCodeGenerator.generateQrCodeBitmap(qrContent)
        isLoading = false
    }

    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // QR Code Image
            if (isLoading) {
                Box(
                    modifier = Modifier
                        .size(size)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else if (qrCodeBitmap != null) {
                Image(
                    bitmap = qrCodeBitmap!!.asImageBitmap(),
                    contentDescription = "QR Code",
                    modifier = Modifier
                        .size(size)
                        .clip(RoundedCornerShape(8.dp))
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(size)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.QrCode2,
                            contentDescription = "QR Error",
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Failed to generate QR", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Address text
            Text(
                text = "Your Address:",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = formatAddressForDisplay(address),
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            if (amount != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Amount: $amount",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
            }

            if (label != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Label: $label",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun formatAddressForDisplay(address: String): String {
    return if (address.length > 16) {
        "${address.take(8)}...${address.takeLast(8)}"
    } else {
        address
    }
}