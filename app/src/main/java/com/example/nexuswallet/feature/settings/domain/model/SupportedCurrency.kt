package com.example.nexuswallet.feature.settings.domain.model

enum class SupportedCurrency(
    val code: String,
    val symbol: String
) {
    USD("USD", "$"),
    EUR("EUR", "€"),
    GBP("GBP", "£"),
    JPY("JPY", "¥"),
    AUD("AUD", "A$"),
    CAD("CAD", "C$"),
    KES("KES", "KSh");

    companion object {
        fun fromCode(code: String): SupportedCurrency {
            return entries.find { it.code.equals(code, ignoreCase = true) } ?: USD
        }
    }
}
