package com.example.nexuswallet.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.nexuswallet.R

// Set of Material typography styles to start with
val Typography = Typography(
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp
    )
    /* Other default text styles to override
    titleLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp
    )
    */
)

val NexusTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = FontFamily(
            Font(R.font.google_sans_flex_72pt_regular, FontWeight.Normal)
        ),
        fontSize = 57.sp
    ),

    headlineLarge = TextStyle(
        fontFamily = FontFamily(
            Font(R.font.google_sans_flex_36pt_semibold, FontWeight.SemiBold)
        ),
        fontSize = 32.sp
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily(
            Font(R.font.google_sans_flex_36pt_semibold, FontWeight.SemiBold)
        ),
        fontSize = 28.sp
    ),

    titleLarge = TextStyle(
        fontFamily = FontFamily(
            Font(R.font.google_sans_flex_24pt_semibold, FontWeight.SemiBold)
        ),
        fontSize = 22.sp
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily(
            Font(R.font.google_sans_flex_24pt_medium, FontWeight.Medium)
        ),
        fontSize = 18.sp
    ),

    bodyLarge = TextStyle(
        fontFamily = FontFamily(
            Font(R.font.google_sans_flex_24pt_regular, FontWeight.Normal)
        ),
        fontSize = 16.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily(
            Font(R.font.google_sans_flex_24pt_regular, FontWeight.Normal)
        ),
        fontSize = 14.sp
    ),

    labelSmall = TextStyle(
        fontFamily = FontFamily(
            Font(R.font.google_sans_flex_9pt_regular, FontWeight.Medium)
        ),
        fontSize = 11.sp
    )
)