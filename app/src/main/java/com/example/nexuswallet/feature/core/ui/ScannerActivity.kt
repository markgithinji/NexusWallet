package com.example.nexuswallet.feature.core.ui

import com.journeyapps.barcodescanner.CaptureActivity
import com.journeyapps.barcodescanner.DecoratedBarcodeView
import com.example.nexuswallet.R

/**
 * A custom CaptureActivity to force portrait orientation and provide a cleaner UI.
 */
class ScannerActivity : CaptureActivity() {
    override fun initializeContent(): DecoratedBarcodeView {
        setContentView(R.layout.activity_scanner)
        val barcodeView = findViewById<DecoratedBarcodeView>(R.id.zxing_barcode_scanner)
        
        // UX: Hide the default viewfinder since we are using a custom modern one in the layout
        barcodeView.viewFinder.visibility = android.view.View.GONE
        
        return barcodeView
    }
}
