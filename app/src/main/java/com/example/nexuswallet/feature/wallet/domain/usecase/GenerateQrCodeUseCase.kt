package com.example.nexuswallet.feature.wallet.domain.usecase

import com.example.nexuswallet.feature.wallet.domain.model.QrCodeData
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GenerateQrCodeUseCase @Inject constructor() {

    operator fun invoke(content: String, size: Int = 512): QrCodeData? {
        return try {
            val writer = QRCodeWriter()
            val bitMatrix = writer.encode(content, BarcodeFormat.QR_CODE, size, size)
            val width = bitMatrix.width
            val height = bitMatrix.height
            val pixels = BooleanArray(width * height)

            for (y in 0 until height) {
                for (x in 0 until width) {
                    pixels[y * width + x] = bitMatrix.get(x, y)
                }
            }

            QrCodeData(pixels, width, height)
        } catch (e: Exception) {
            null
        }
    }
}
