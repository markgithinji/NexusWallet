package com.example.nexuswallet.feature.market.domain.model

enum class ChartDuration(val days: String, val label: String) {
    ONE_DAY("1", "24H"),
    ONE_WEEK("7", "7D"),
    ONE_MONTH("30", "30D"),
    THREE_MONTHS("90", "90D"),
    ONE_YEAR("365", "1Y"),
    MAX("max", "All")
}