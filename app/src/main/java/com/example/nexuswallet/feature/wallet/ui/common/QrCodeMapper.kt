package com.example.nexuswallet.feature.wallet.ui.common

import android.graphics.Bitmap
import android.graphics.Color
import com.example.nexuswallet.feature.wallet.domain.model.QrCodeData

fun QrCodeData.toBitmap(): Bitmap {
    val bitmapPixels = IntArray(width * height)
    for (i in bitmapPixels.indices) {
        bitmapPixels[i] = if (this.pixels[i]) Color.BLACK else Color.WHITE
    }
    return Bitmap.createBitmap(bitmapPixels, width, height, Bitmap.Config.ARGB_8888)
}
