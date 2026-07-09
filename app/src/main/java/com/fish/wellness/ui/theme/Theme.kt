package com.fish.wellness.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val LightColorScheme = lightColorScheme(
    primary = Primary,
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFB8E8DE),
    onPrimaryContainer = Color(0xFF003830),
    secondary = Secondary,
    onSecondary = Color(0xFFFFFFFF),
    tertiary = Tertiary,
    surface = SurfaceLight,
    onSurface = OnSurfaceLight,
    surfaceVariant = SurfaceVariantLight,
    onSurfaceVariant = Color(0xFF4D4639),
    error = Error,
    background = SurfaceLight,
    onBackground = OnSurfaceLight,
)

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF7CD0C0),
    onPrimary = Color(0xFF003830),
    primaryContainer = PrimaryDark,
    onPrimaryContainer = Color(0xFFB8E8DE),
    secondary = Secondary,
    onSecondary = Color(0xFF4A2A0E),
    tertiary = Tertiary,
    surface = SurfaceDark,
    onSurface = OnSurfaceDark,
    surfaceVariant = SurfaceVariantDark,
    onSurfaceVariant = Color(0xFFCFC6B5),
    error = Error,
    background = SurfaceDark,
    onBackground = OnSurfaceDark,
)

@Composable
fun FishWellnessTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
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
