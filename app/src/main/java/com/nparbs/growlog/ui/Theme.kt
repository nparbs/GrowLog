package com.nparbs.growlog.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF1F6B3F),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFC2EFCB),
    onPrimaryContainer = Color(0xFF00210E),
    secondary = Color(0xFF1C6685),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFC6E7F9),
    onSecondaryContainer = Color(0xFF001E2B),
    tertiary = Color(0xFF8A5A12),
    tertiaryContainer = Color(0xFFFFDDB3),
    onTertiaryContainer = Color(0xFF2C1800),
    background = Color(0xFFF7FAF4),
    surface = Color(0xFFF7FAF4),
    surfaceContainer = Color(0xFFEBF0E7),
    surfaceContainerHigh = Color(0xFFE5EAE1),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF8FD8A3),
    onPrimary = Color(0xFF00391C),
    primaryContainer = Color(0xFF00522B),
    onPrimaryContainer = Color(0xFFC2EFCB),
    secondary = Color(0xFF90CFEF),
    onSecondary = Color(0xFF003548),
    secondaryContainer = Color(0xFF004D67),
    onSecondaryContainer = Color(0xFFC6E7F9),
    tertiary = Color(0xFFF2BF76),
    tertiaryContainer = Color(0xFF6B4300),
    onTertiaryContainer = Color(0xFFFFDDB3),
    background = Color(0xFF101410),
    surface = Color(0xFF101410),
    surfaceContainer = Color(0xFF1C211C),
    surfaceContainerHigh = Color(0xFF262B26),
)

@Composable
fun GrowLogTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        content = content,
    )
}
