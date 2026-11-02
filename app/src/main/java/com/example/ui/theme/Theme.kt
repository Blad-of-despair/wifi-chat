package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = HomeLinkDarkPrimary,
    onPrimary = HomeLinkDarkOnPrimary,
    primaryContainer = HomeLinkDarkPrimaryContainer,
    onPrimaryContainer = HomeLinkDarkOnPrimaryContainer,
    secondary = HomeLinkDarkSecondary,
    onSecondary = HomeLinkDarkOnSecondary,
    secondaryContainer = HomeLinkDarkSecondaryContainer,
    onSecondaryContainer = HomeLinkDarkOnSecondaryContainer,
    tertiary = HomeLinkDarkTertiary,
    onTertiary = HomeLinkDarkOnTertiary,
    tertiaryContainer = HomeLinkDarkTertiaryContainer,
    onTertiaryContainer = HomeLinkDarkOnTertiaryContainer,
    background = Color(0xFF0F172A), // Slate 900
    surface = Color(0xFF1E293B), // Slate 800
    surfaceVariant = Color(0xFF334155),
    onBackground = Color(0xFFF8FAFC),
    onSurface = Color(0xFFF8FAFC),
    onSurfaceVariant = Color(0xFFCBD5E1)
)

private val AmoledColorScheme = DarkColorScheme.copy(
    background = Color.Black,
    surface = Color(0xFF121212),
    surfaceVariant = Color(0xFF1E1E1E)
)

private val LightColorScheme = lightColorScheme(
    primary = HomeLinkPrimary,
    onPrimary = HomeLinkOnPrimary,
    primaryContainer = HomeLinkPrimaryContainer,
    onPrimaryContainer = HomeLinkOnPrimaryContainer,
    secondary = HomeLinkSecondary,
    onSecondary = HomeLinkOnSecondary,
    secondaryContainer = HomeLinkSecondaryContainer,
    onSecondaryContainer = HomeLinkOnSecondaryContainer,
    tertiary = HomeLinkTertiary,
    onTertiary = HomeLinkOnTertiary,
    tertiaryContainer = HomeLinkTertiaryContainer,
    onTertiaryContainer = HomeLinkOnTertiaryContainer,
    background = Color(0xFFF8FAFC), // Slate 50
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFF1F5F9),
    onBackground = Color(0xFF0F172A),
    onSurface = Color(0xFF0F172A),
    onSurfaceVariant = Color(0xFF475569)
)

@Composable
fun HomeLinkTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    isAmoled: Boolean = false,
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val colorScheme = when {
        isAmoled && darkTheme -> AmoledColorScheme
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
