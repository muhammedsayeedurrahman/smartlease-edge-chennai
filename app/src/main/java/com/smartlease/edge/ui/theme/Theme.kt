package com.smartlease.edge.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val DarkColors = darkColorScheme(
    primary = LampAmber,
    onPrimary = SlateGround,
    secondary = LampGreen,
    onSecondary = SlateGround,
    background = SlateGround,
    onBackground = ReadoutPrimary,
    surface = SlatePanel,
    onSurface = ReadoutPrimary,
    surfaceVariant = SlateEdge,
    onSurfaceVariant = ReadoutDim,
    outline = SlateEdge,
    error = LampRed,
    onError = SlateGround
)

private val LightColors = lightColorScheme(
    primary = InkPrimary,
    onPrimary = PaperGround,
    secondary = LampGreen,
    background = PaperGround,
    onBackground = InkPrimary,
    surface = PaperPanel,
    onSurface = InkPrimary,
    surfaceVariant = PaperEdge,
    onSurfaceVariant = InkDim,
    outline = PaperEdge,
    error = LampRed
)

/**
 * Monospace is reserved for measured values -- degrees of tilt, square feet, confidence
 * percentages, decay in milliseconds. It is not used for labels or headings.
 *
 * The distinction is deliberate. Tabular figures stop a readout from jittering as the last
 * digit changes while the phone is being held against a wall, and it visually separates "a
 * number this device measured" from "text we wrote". Monospace applied to ordinary labels is
 * just costume; applied to instrument readings it is doing a job.
 */
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
        letterSpacing = (-0.7).sp      // large text needs tightening, not default tracking
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 20.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 21.sp             // 1.5x: this is read in poor light
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 18.sp
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp
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
            // Tell the system which way to draw the clock and battery. Without this the
            // status bar keeps light icons over the paper background and the time is
            // effectively invisible -- which is exactly the sort of detail a judge notices
            // in the first two seconds of a demo.
            WindowCompat.getInsetsController(window, view)
                .isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = AppTypography,
        content = content
    )
}
