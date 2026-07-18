package com.winlator.cmod.console

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalOverscrollConfiguration
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.text.googlefonts.GoogleFont
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.winlator.cmod.R

/**
 * Modern Apple-inspired console palette: soft system gray canvas,
 * large continuous radii, iOS-blue accent. Keeps light console home.
 */
object ConsoleColors {
    val CanvasTop = Color(0xFFF7F7FA)
    val Canvas = Color(0xFFF2F2F7)
    val CanvasDeep = Color(0xFFE5E5EA)
    val Surface = Color(0xFFF9F9FB)
    val SurfaceRaised = Color(0xFFFFFFFF)
    val Glass = Color(0xF2FFFFFF)
    val AccentBlue = Color(0xFF007AFF)
    val AccentRed = Color(0xFFFF3B30)
    val Accent = AccentBlue
    val AccentDim = Color(0xFF0066D6)
    val TextPrimary = Color(0xFF1C1C1E)
    val TextSecondary = Color(0xFF8E8E93)
    val Danger = Color(0xFFFF3B30)
    val CardStroke = Color(0x14000000)
    val FocusRing = AccentBlue
    val HintLabel = Color(0xFF8E8E93)
    val Scrim = Color(0x66000000)
}

object ConsoleRadii {
    val tile = 28.dp
    val sheet = 36.dp
    val button = 22.dp
    val chip = 18.dp
    val row = 20.dp
    /** ~32% corners — Apple-like squircle for icon FABs. */
    val squircle = 32
}

val ConsoleTileShape = RoundedCornerShape(ConsoleRadii.tile)
val ConsoleSheetShape = RoundedCornerShape(topStart = ConsoleRadii.sheet, topEnd = ConsoleRadii.sheet)
val ConsoleRowShape = RoundedCornerShape(ConsoleRadii.row)
val ConsoleChipShape = RoundedCornerShape(ConsoleRadii.chip)
val ConsoleButtonShape = RoundedCornerShape(ConsoleRadii.button)
val ConsoleSquircleShape = RoundedCornerShape(percent = ConsoleRadii.squircle)

private val provider = GoogleFont.Provider(
    providerAuthority = "com.google.android.gms.fonts",
    providerPackage = "com.google.android.gms",
    certificates = R.array.com_google_android_gms_fonts_certs,
)

private val nunitoFont = GoogleFont("Nunito")

val ConsoleFontFamily = FontFamily(
    Font(googleFont = nunitoFont, fontProvider = provider, weight = FontWeight.Normal),
    Font(googleFont = nunitoFont, fontProvider = provider, weight = FontWeight.Medium),
    Font(googleFont = nunitoFont, fontProvider = provider, weight = FontWeight.SemiBold),
    Font(googleFont = nunitoFont, fontProvider = provider, weight = FontWeight.Bold),
    Font(googleFont = nunitoFont, fontProvider = provider, weight = FontWeight.ExtraBold),
    Font(googleFont = nunitoFont, fontProvider = provider, weight = FontWeight.Black),
)

private val ConsoleColorScheme = lightColorScheme(
    primary = ConsoleColors.AccentBlue,
    onPrimary = Color.White,
    secondary = ConsoleColors.AccentRed,
    onSecondary = Color.White,
    background = ConsoleColors.Canvas,
    surface = ConsoleColors.SurfaceRaised,
    onBackground = ConsoleColors.TextPrimary,
    onSurface = ConsoleColors.TextPrimary,
    onSurfaceVariant = ConsoleColors.TextSecondary,
)

private val ConsoleTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = ConsoleFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 38.sp,
        letterSpacing = (-0.5).sp,
        color = ConsoleColors.TextPrimary,
    ),
    headlineMedium = TextStyle(
        fontFamily = ConsoleFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 26.sp,
        letterSpacing = (-0.3).sp,
        color = ConsoleColors.TextPrimary,
    ),
    titleLarge = TextStyle(
        fontFamily = ConsoleFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        color = ConsoleColors.TextPrimary,
    ),
    bodyLarge = TextStyle(
        fontFamily = ConsoleFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        color = ConsoleColors.TextSecondary,
    ),
    bodyMedium = TextStyle(
        fontFamily = ConsoleFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        color = ConsoleColors.TextSecondary,
    ),
    labelLarge = TextStyle(
        fontFamily = ConsoleFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp,
        letterSpacing = 0.2.sp,
        color = ConsoleColors.HintLabel,
    ),
)

@Composable
@OptIn(ExperimentalFoundationApi::class)
@Suppress("DEPRECATION")
fun ConsoleTheme(content: @Composable () -> Unit) {
    // Kill stretch/glow overscroll app-wide — major scroll jitter source on OEM devices.
    CompositionLocalProvider(LocalOverscrollConfiguration provides null) {
        MaterialTheme(
            colorScheme = ConsoleColorScheme,
            typography = ConsoleTypography,
            content = content,
        )
    }
}

/** Soft vertical canvas used across Home, Scaffold, and Session menu. */
val ConsoleCanvasBrush: Brush = Brush.verticalGradient(
    listOf(ConsoleColors.CanvasTop, ConsoleColors.Canvas, ConsoleColors.CanvasDeep),
)

/** @deprecated Prefer [ConsoleCanvasBrush] — kept for call-site compatibility. */
fun consoleCanvasBrush(): Brush = ConsoleCanvasBrush
