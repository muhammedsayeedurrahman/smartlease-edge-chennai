package com.smartlease.edge.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Professional Light Theme for Corporate use
private val CorporateLightColors = lightColorScheme(
    primary = CorporateYellow,
    onPrimary = PureBlack,
    secondary = CorporateYellow,
    onSecondary = PureBlack,
    background = PureWhite,
    onBackground = TextPrimaryDark,
    surface = SurfaceLight,
    onSurface = TextPrimaryDark,
    surfaceVariant = SurfaceVariantLight,
    onSurfaceVariant = TextSecondaryDark,
    outline = BorderLight,
    error = LampRed,
    onError = PureWhite
)

val ReadoutValue = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontWeight = FontWeight.Medium,
    fontSize = 15.sp,
    letterSpacing = 0.sp
)

val ReadoutValueLarge = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontWeight = FontWeight.Bold,
    fontSize = 26.sp,
    letterSpacing = (-0.5).sp
)

private val AppTypography = Typography(
    displaySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 30.sp,
        lineHeight = 34.sp,
        letterSpacing = (-0.7).sp
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 24.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 22.sp
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 18.sp
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp
    )
)

@Composable
fun SmartLeaseEdgeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(), 
    content: @Composable () -> Unit
) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            // Use light status bars (dark icons) because our background is Light White/Gray
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = true
        }
    }

    MaterialTheme(
        colorScheme = CorporateLightColors,
        typography = AppTypography,
        content = content
    )
}
