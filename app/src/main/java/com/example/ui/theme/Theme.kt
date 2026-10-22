package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val StrataDarkColorScheme = darkColorScheme(
    primary = StrataCyanPrimary,
    onPrimary = Color(0xFF003548),
    primaryContainer = Color(0xFF004D68),
    onPrimaryContainer = Color(0xFFBBE9FF),

    secondary = StrataIndigoSecondary,
    onSecondary = Color(0xFF202B7B),
    secondaryContainer = Color(0xFF384393),
    onSecondaryContainer = Color(0xFFDFE0FF),

    tertiary = StrataEmeraldTertiary,
    onTertiary = Color(0xFF003822),
    tertiaryContainer = Color(0xFF005234),
    onTertiaryContainer = Color(0xFF8CF6B8),

    background = StrataBackground,
    onBackground = StrataTextPrimary,

    surface = StrataSurface,
    onSurface = StrataTextPrimary,
    surfaceVariant = StrataSurfaceVariant,
    onSurfaceVariant = StrataTextSecondary,

    outline = StrataSurfaceHighlight,
    error = StrataErrorColor,
    onError = Color.White
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false, // Keep consistent cybernetic theme
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = StrataDarkColorScheme,
        typography = Typography,
        content = content
    )
}
