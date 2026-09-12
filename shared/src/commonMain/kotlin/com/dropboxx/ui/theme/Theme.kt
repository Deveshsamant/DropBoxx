package com.dropboxx.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.dropboxx.domain.ThemeMode

private val Indigo = Color(0xFF4F5BD5)
private val IndigoDark = Color(0xFFB8BEFF)
private val Teal = Color(0xFF0FA3A3)

private val LightScheme = lightColorScheme(
    primary = Indigo,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE0E2FF),
    onPrimaryContainer = Color(0xFF0A0F6B),
    secondary = Teal,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFCFF3F1),
    onSecondaryContainer = Color(0xFF00332F),
    tertiary = Color(0xFFB4436C),
    background = Color(0xFFF7F7FB),
    onBackground = Color(0xFF1B1B21),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1B1B21),
    surfaceVariant = Color(0xFFE4E3EE),
    onSurfaceVariant = Color(0xFF46464F),
    outline = Color(0xFF777680),
    error = Color(0xFFBA1A1A),
)

private val DarkScheme = darkColorScheme(
    primary = IndigoDark,
    onPrimary = Color(0xFF1B1F8F),
    primaryContainer = Color(0xFF3540B9),
    onPrimaryContainer = Color(0xFFE0E2FF),
    secondary = Color(0xFF70D8D4),
    onSecondary = Color(0xFF003735),
    secondaryContainer = Color(0xFF004F4C),
    onSecondaryContainer = Color(0xFFCFF3F1),
    tertiary = Color(0xFFFFB0C8),
    background = Color(0xFF121218),
    onBackground = Color(0xFFE4E1EA),
    surface = Color(0xFF1B1B22),
    onSurface = Color(0xFFE4E1EA),
    surfaceVariant = Color(0xFF2A2A33),
    onSurfaceVariant = Color(0xFFC7C5D0),
    outline = Color(0xFF908F9A),
    error = Color(0xFFFFB4AB),
)

@Composable
fun DropBoxxTheme(mode: ThemeMode, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    MaterialTheme(colorScheme = if (dark) DarkScheme else LightScheme, content = content)
}
