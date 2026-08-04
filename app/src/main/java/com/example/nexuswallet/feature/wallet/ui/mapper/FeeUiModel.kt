package com.example.nexuswallet.feature.wallet.ui.mapper

import androidx.compose.ui.graphics.Color
import com.example.nexuswallet.feature.core.domain.model.FeeLevel

data class FeeUiModel(
    val priority: FeeLevel,
    val priorityLabel: String,
    val priorityColor: Color,
    val feeDetails: List<Pair<String, String>>,
    val estimatedTimeText: String?
)
