package com.meubanco.patrimonio.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalDensity

class Palette(
    val bg: Color, val card: Color, val card2: Color, val line: Color, val text: Color, val muted: Color,
    val brand: Color, val gold: Color, val green: Color, val red: Color, val dark: Boolean,
)

val DarkPalette = Palette(
    Color(0xFF090D14), Color(0xFF131A27), Color(0xFF1A2335), Color(0xFF263149), Color(0xFFE8EDF7), Color(0xFF8B97AD),
    Color(0xFF4F7CFF), Color(0xFFD4A24C), Color(0xFF34C98F), Color(0xFFEF6461), true,
)
val LightPalette = Palette(
    Color(0xFFF3F5FA), Color(0xFFFFFFFF), Color(0xFFEBEFF7), Color(0xFFD7DEEC), Color(0xFF0F1626), Color(0xFF5B677F),
    Color(0xFF2F5FE8), Color(0xFFA9781F), Color(0xFF16915F), Color(0xFFD33F3C), false,
)
val HighContrastDark = DarkPalette.let {
    Palette(Color.Black, Color(0xFF0D0D0D), Color(0xFF1A1A1A), Color.White, Color.White, Color(0xFFD0D0D0),
        Color(0xFF7FA0FF), Color(0xFFFFD27A), Color(0xFF5BFFB6), Color(0xFFFF8A87), true)
}

val LocalPalette = staticCompositionLocalOf { DarkPalette }
val P: Palette @Composable get() = LocalPalette.current

@Composable
fun AppTheme(theme: String, fontSize: String, highContrast: Boolean, content: @Composable () -> Unit) {
    val dark = when (theme) { "light" -> false; "auto" -> isSystemInDarkTheme(); else -> true }
    val pal = if (dark) (if (highContrast) HighContrastDark else DarkPalette) else LightPalette
    val scheme = if (dark) darkColorScheme(
        primary = pal.brand, background = pal.bg, surface = pal.card, onSurface = pal.text, onBackground = pal.text,
        surfaceVariant = pal.card2, onSurfaceVariant = pal.muted, outline = pal.line, error = pal.red,
    ) else lightColorScheme(
        primary = pal.brand, background = pal.bg, surface = pal.card, onSurface = pal.text, onBackground = pal.text,
        surfaceVariant = pal.card2, onSurfaceVariant = pal.muted, outline = pal.line, error = pal.red,
    )
    val scale = when (fontSize) { "grande" -> 1.15f; "xgrande" -> 1.3f; else -> 1f }
    val d = LocalDensity.current
    CompositionLocalProvider(LocalPalette provides pal, LocalDensity provides Density(d.density, d.fontScale * scale)) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}

object T {
    val title = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Bold)
    val h2 = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
    val body = TextStyle(fontSize = 14.sp)
    val small = TextStyle(fontSize = 12.sp)
    val big = TextStyle(fontSize = 30.sp, fontWeight = FontWeight.Bold)
}
