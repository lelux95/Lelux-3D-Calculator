package com.lelux.calc3d.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.lelux.calc3d.data.ThemeMode

val Accent = Color(0xFFFF8A3D)
val AccentDark = Color(0xFFC2591B)
val Good = Color(0xFF4CAF7D)
val Bad = Color(0xFFE5534B)

private val DarkScheme = darkColorScheme(
    primary = Accent,
    onPrimary = Color(0xFF1B0E05),
    primaryContainer = Color(0xFF3A2416),
    onPrimaryContainer = Color(0xFFFFD9C2),
    secondary = Color(0xFFB9C3D0),
    background = Color(0xFF121316),
    onBackground = Color(0xFFE8E9EC),
    surface = Color(0xFF1A1B1F),
    onSurface = Color(0xFFE8E9EC),
    surfaceVariant = Color(0xFF24262B),
    onSurfaceVariant = Color(0xFFA9AEB7),
    surfaceContainer = Color(0xFF1E2024),
    surfaceContainerHigh = Color(0xFF26282D),
    surfaceContainerHighest = Color(0xFF2E3036),
    outline = Color(0xFF454951),
    outlineVariant = Color(0xFF34373D),
    error = Bad,
)

private val LightScheme = lightColorScheme(
    primary = AccentDark,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFE3D1),
    onPrimaryContainer = Color(0xFF3A1A06),
    secondary = Color(0xFF55606E),
    background = Color(0xFFF6F4F1),
    onBackground = Color(0xFF1C1D20),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1C1D20),
    surfaceVariant = Color(0xFFF0ECE8),
    onSurfaceVariant = Color(0xFF5F646C),
    surfaceContainer = Color(0xFFF7F3EF),
    surfaceContainerHigh = Color(0xFFF1ECE7),
    surfaceContainerHighest = Color(0xFFEAE4DE),
    outline = Color(0xFFC9C3BD),
    outlineVariant = Color(0xFFE2DCD6),
    error = Bad,
)

@Composable
fun isDark(mode: ThemeMode): Boolean = when (mode) {
    ThemeMode.DARK -> true
    ThemeMode.LIGHT -> false
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
}

@Composable
fun LeluxTheme(mode: ThemeMode, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isDark(mode)) DarkScheme else LightScheme,
        typography = Typography(),
        content = content,
    )
}
