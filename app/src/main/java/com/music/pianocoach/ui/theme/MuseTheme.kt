package com.music.pianocoach.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** 墨青夜想 — 全局语义色（跟练瀑布等也可引用） */
object MuseColors {
    val InkBackground = Color(0xFF0E1418)
    val InkSurface = Color(0xFF172025)
    val InkElevated = Color(0xFF1E2A30)
    val Ivory = Color(0xFFE8E4DC)
    val IvoryDim = Color(0xFFB8B2A8)
    val Amber = Color(0xFFD4A574)
    val AmberDim = Color(0xFF9A7A52)
    val Sage = Color(0xFF5B8A7A)
    val SageLight = Color(0xFF7AA896)
    val WaterfallBg = Color(0xFF10161A)
    val HandLeft = Color(0xFF6BA88F)
    val HandRight = Color(0xFF7EB8C9)
    val HandLeftDim = Color(0x556BA88F)
    val HandRightDim = Color(0x557EB8C9)
    val PlayAccent = Color(0xFF7EB8C9)
}

private val MuseDarkScheme = darkColorScheme(
    primary = MuseColors.Amber,
    onPrimary = Color(0xFF1A140E),
    primaryContainer = Color(0xFF3D3020),
    onPrimaryContainer = MuseColors.Ivory,
    secondary = MuseColors.Sage,
    onSecondary = Color(0xFF0E1418),
    secondaryContainer = Color(0xFF243530),
    onSecondaryContainer = MuseColors.Ivory,
    tertiary = MuseColors.SageLight,
    background = MuseColors.InkBackground,
    onBackground = MuseColors.Ivory,
    surface = MuseColors.InkSurface,
    onSurface = MuseColors.Ivory,
    surfaceVariant = MuseColors.InkElevated,
    onSurfaceVariant = MuseColors.IvoryDim,
    outline = Color(0xFF3A4A52),
    error = Color(0xFFE08A7A),
    onError = Color(0xFF1A0E0C),
)

private val MuseDisplay = FontFamily.Serif
private val MuseBody = FontFamily.SansSerif

private val MuseTypography = Typography(
    displaySmall = TextStyle(
        fontFamily = MuseDisplay,
        fontWeight = FontWeight.Normal,
        fontSize = 32.sp,
        letterSpacing = 0.5.sp,
        color = MuseColors.Ivory,
    ),
    titleLarge = TextStyle(
        fontFamily = MuseDisplay,
        fontWeight = FontWeight.Normal,
        fontSize = 24.sp,
        letterSpacing = 0.3.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = MuseDisplay,
        fontWeight = FontWeight.Medium,
        fontSize = 18.sp,
    ),
    titleSmall = TextStyle(
        fontFamily = MuseBody,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        letterSpacing = 0.4.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = MuseBody,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = MuseBody,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = MuseBody,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        color = MuseColors.IvoryDim,
    ),
    labelLarge = TextStyle(
        fontFamily = MuseBody,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        letterSpacing = 0.8.sp,
    ),
)

val LocalMuseColors = staticCompositionLocalOf { MuseColors }

@Composable
fun MuseTheme(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalMuseColors provides MuseColors) {
        MaterialTheme(
            colorScheme = MuseDarkScheme,
            typography = MuseTypography,
            content = content,
        )
    }
}
