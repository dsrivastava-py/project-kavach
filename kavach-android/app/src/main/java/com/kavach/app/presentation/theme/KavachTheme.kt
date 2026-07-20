package com.kavach.app.presentation.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// ── Brand colours ─────────────────────────────────────────────────────────
val KavachGreen       = Color(0xFF1B5E20)
val KavachGreenLight  = Color(0xFF4CAF50)
val KavachGreenDark   = Color(0xFF003300)
val KavachOnGreen     = Color(0xFFFFFFFF)
val KavachContainer   = Color(0xFFA5D6A7)
val KavachOnContainer = Color(0xFF002107)
val RiskLow           = Color(0xFF388E3C)
val RiskMedium        = Color(0xFFF57F17)
val RiskHigh          = Color(0xFFD32F2F)

private val LightColorScheme = lightColorScheme(
    primary          = KavachGreen,
    onPrimary        = KavachOnGreen,
    primaryContainer = KavachContainer,
    onPrimaryContainer = KavachOnContainer,
    secondary        = Color(0xFF386A20),
    onSecondary      = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFB6F286),
    onSecondaryContainer = Color(0xFF0C2000),
    tertiary         = Color(0xFF006C51),
    error            = Color(0xFFBA1A1A),
    background       = Color(0xFFF8FDF8),
    surface          = Color(0xFFF8FDF8),
    onBackground     = Color(0xFF1A1C19),
    onSurface        = Color(0xFF1A1C19),
)

private val DarkColorScheme = darkColorScheme(
    primary          = KavachContainer,
    onPrimary        = KavachOnContainer,
    primaryContainer = Color(0xFF003910),
    onPrimaryContainer = Color(0xFFA5D6A7),
    secondary        = Color(0xFF9AD56D),
    onSecondary      = Color(0xFF1B3700),
    secondaryContainer = Color(0xFF294F00),
    onSecondaryContainer = Color(0xFFB6F286),
    tertiary         = Color(0xFF6DDBBE),
    error            = Color(0xFFFFB4AB),
    background       = Color(0xFF1A1C19),
    surface          = Color(0xFF1A1C19),
    onBackground     = Color(0xFFE2E3DC),
    onSurface        = Color(0xFFE2E3DC),
)

/**
 * Kavach Material 3 theme.
 *
 * Dynamic color is enabled on Android 12+ (API 31+) when the system supports it.
 * Falls back to the hand-crafted Kavach green palette on older devices.
 */
@Composable
fun KavachTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else      -> LightColorScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = Color.Transparent.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography  = KavachTypography,
        content     = content,
    )
}
