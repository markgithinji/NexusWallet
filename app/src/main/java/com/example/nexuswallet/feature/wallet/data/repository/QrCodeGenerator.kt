package com.example.nexuswallet.feature.wallet.data.repository

import android.graphics.Bitmap
import android.graphics.Color
import android.util.Log
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.EnumMap

class QrCodeGenerator {

    suspend fun generateQrCodeBitmap(
        content: String,
        size: Int = 400
    ): Bitmap? = withContext(Dispatchers.IO) {
        return@withContext try {
            val hints = EnumMap<EncodeHintType, Any>(EncodeHintType::class.java)
            hints[EncodeHintType.ERROR_CORRECTION] = ErrorCorrectionLevel.H
            hints[EncodeHintType.MARGIN] = 1

            val bitMatrix = QRCodeWriter().encode(
                content,
                BarcodeFormat.QR_CODE,
                size,
                size,
                hints
            )

            val width = bitMatrix.width
            val height = bitMatrix.height
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)

            for (x in 0 until width) {
                for (y in 0 until height) {
                    bitmap.setPixel(x, y, if (bitMatrix.get(x, y)) Color.BLACK else Color.WHITE)
                }
            }

            bitmap
        } catch (e: Exception) {
            Log.e("QrCodeGenerator", "Error generating QR code: ${e.message}")
            null
        }
    }

    fun generateReceiveQrCode(
        address: String,
        amount: String? = null,
        label: String? = null,
        message: String? = null
    ): String {
        return when {
            address.startsWith("0x") -> {
                // Ethereum URI scheme: ethereum:address[?param=value]
                buildString {
                    append("ethereum:$address")
                    if (amount != null || label != null || message != null) {
                        append("?")
                        val params = mutableListOf<String>()
                        amount?.let { params.add("value=$it") }
                        label?.let { params.add("label=${it.urlEncode()}") }
                        message?.let { params.add("message=${it.urlEncode()}") }
                        append(params.joinToString("&"))
                    }
                }
            }

            address.startsWith("bc1") || address.startsWith("1") || address.startsWith("3") -> {
                // Bitcoin URI scheme: bitcoin:address[?amount=amount&label=label&message=message]
                buildString {
                    append("bitcoin:$address")
                    if (amount != null || label != null || message != null) {
                        append("?")
                        val params = mutableListOf<String>()
                        amount?.let { params.add("amount=$it") }
                        label?.let { params.add("label=${it.urlEncode()}") }
                        message?.let { params.add("message=${it.urlEncode()}") }
                        append(params.joinToString("&"))
                    }
                }
            }

            else -> address // Fallback to plain address
        }
    }

    private fun String.urlEncode(): String {
        return this.replace(" ", "%20")
            .replace("&", "%26")
            .replace("?", "%3F")
            .replace("=", "%3D")
            .replace("#", "%23")
    }
}