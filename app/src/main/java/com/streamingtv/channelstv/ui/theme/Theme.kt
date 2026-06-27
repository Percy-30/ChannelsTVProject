package com.streamingtv.channelstv.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary          = GreenAccent,
    onPrimary        = TextPrimary,
    primaryContainer = GreenDark,
    secondary        = BlueButton,
    onSecondary      = TextPrimary,
    background       = BackgroundDark,
    onBackground     = TextPrimary,
    surface          = SurfaceDark,
    onSurface        = TextPrimary,
    surfaceVariant   = SurfaceMedium,
    onSurfaceVariant = TextSecondary
)

@Composable
fun ChannelsTVTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography  = Typography,
        content     = content
    )
}
