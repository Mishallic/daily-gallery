package com.dailygallery.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily

// A gallery palette: warm paper by day, a dim room by night, and gilt-frame gold for accents.
// Fixed colours rather than the wallpaper's, so the artwork is never framed in a clashing tint.
private val Light = lightColorScheme(
    primary = Color(0xFF7D5A1C),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFF1E2C2),
    onPrimaryContainer = Color(0xFF2B1E06),
    secondary = Color(0xFF6B5E4E),
    onSecondary = Color(0xFFFFFFFF),
    background = Color(0xFFF6F1E8),
    onBackground = Color(0xFF1D1A16),
    surface = Color(0xFFF6F1E8),
    onSurface = Color(0xFF1D1A16),
    surfaceVariant = Color(0xFFECE5D8),
    onSurfaceVariant = Color(0xFF5B5347),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFBF8F2),
    surfaceContainer = Color(0xFFF1EBE0),
    surfaceContainerHigh = Color(0xFFEAE3D6),
    surfaceContainerHighest = Color(0xFFE3DBCC),
    outline = Color(0xFF8F8576),
    outlineVariant = Color(0xFFD5CCBC),
    error = Color(0xFFA8322A),
)

private val Dark = darkColorScheme(
    primary = Color(0xFFD4B26A),
    onPrimary = Color(0xFF2B2210),
    primaryContainer = Color(0xFF4A3A16),
    onPrimaryContainer = Color(0xFFF1E2C2),
    secondary = Color(0xFFC9BBA6),
    onSecondary = Color(0xFF2E271D),
    background = Color(0xFF12110F),
    onBackground = Color(0xFFEDE6DA),
    surface = Color(0xFF12110F),
    onSurface = Color(0xFFEDE6DA),
    surfaceVariant = Color(0xFF2A2621),
    onSurfaceVariant = Color(0xFFB8AE9F),
    surfaceContainerLowest = Color(0xFF0D0C0A),
    surfaceContainerLow = Color(0xFF181614),
    surfaceContainer = Color(0xFF1D1B18),
    surfaceContainerHigh = Color(0xFF26231F),
    surfaceContainerHighest = Color(0xFF312D28),
    outline = Color(0xFF7C7366),
    outlineVariant = Color(0xFF3D3831),
    error = Color(0xFFF2A49C),
)

private val Serif = FontFamily.Serif

/** Titles in a serif, like a museum label; running text in the system sans for reading on a phone. */
private val GalleryTypography = Typography().run {
    copy(
        displayLarge = displayLarge.copy(fontFamily = Serif),
        displayMedium = displayMedium.copy(fontFamily = Serif),
        displaySmall = displaySmall.copy(fontFamily = Serif),
        headlineLarge = headlineLarge.copy(fontFamily = Serif),
        headlineMedium = headlineMedium.copy(fontFamily = Serif),
        headlineSmall = headlineSmall.copy(fontFamily = Serif),
        titleLarge = titleLarge.copy(fontFamily = Serif),
    )
}

@Composable
fun DailyGalleryTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (dark) Dark else Light, typography = GalleryTypography, content = content)
}
