package com.streamingtv.channelstv.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val Typography = Typography(
    // "BIENVENID@ A CHANNELS TV"
    displayLarge = TextStyle(
        fontFamily   = FontFamily.Default,
        fontWeight   = FontWeight.ExtraBold,
        fontSize     = 28.sp,
        lineHeight   = 34.sp,
        color        = TextPrimary
    ),
    // Section headers: "Eventos de Hoy", "Deportes"
    headlineMedium = TextStyle(
        fontFamily  = FontFamily.Default,
        fontWeight  = FontWeight.Bold,
        fontSize    = 18.sp,
        color       = TextPrimary
    ),
    // Channel name under card
    titleSmall = TextStyle(
        fontFamily  = FontFamily.Default,
        fontWeight  = FontWeight.Medium,
        fontSize    = 11.sp,
        color       = TextPrimary
    ),
    // Event title
    bodyLarge = TextStyle(
        fontFamily  = FontFamily.Default,
        fontWeight  = FontWeight.SemiBold,
        fontSize    = 14.sp,
        color       = TextPrimary
    ),
    // Event subtitle
    bodyMedium = TextStyle(
        fontFamily  = FontFamily.Default,
        fontWeight  = FontWeight.Normal,
        fontSize    = 12.sp,
        color       = TextSecondary
    ),
    // Time "21:00"
    labelLarge = TextStyle(
        fontFamily  = FontFamily.Default,
        fontWeight  = FontWeight.Bold,
        fontSize    = 20.sp,
        color       = GreenAccent
    ),
    // "HORA" label
    labelSmall = TextStyle(
        fontFamily  = FontFamily.Default,
        fontWeight  = FontWeight.Normal,
        fontSize    = 10.sp,
        color       = TextSecondary
    )
)
