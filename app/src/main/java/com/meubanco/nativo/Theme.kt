package com.meubanco.nativo

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** Cores copiadas do CSS do app HTML (variáveis :root e body.theme-light). */
class Palette(
    val bg: Color,
    val bgSoft: Color,
    val card: Color,
    val cardAlt: Color,
    val border: Color,
    val borderSoft: Color,
    val text: Color,
    val muted: Color,
    val mutedDim: Color,
    val brand: Color,
    val brandDim: Color,
    val brandInk: Color,
    val gold: Color,
    val emerald: Color,
    val coral: Color,
    val sapphire: Color,
)

val DarkPalette = Palette(
    bg = Color(0xFF090D14),
    bgSoft = Color(0xFF0E1420),
    card = Color(0xFF131A27),
    cardAlt = Color(0xFF1A2333),
    border = Color(0xFF29344A),
    borderSoft = Color(0xFF212A3D),
    text = Color(0xFFEEF2F9),
    muted = Color(0xFF8996AB),
    mutedDim = Color(0xFF5B6579),
    brand = Color(0xFF4F7CFF),
    brandDim = Color(0xFF24407A),
    brandInk = Color(0xFF061022),
    gold = Color(0xFFD4A24C),
    emerald = Color(0xFF34C98F),
    coral = Color(0xFFEF6461),
    sapphire = Color(0xFF5D9BFF),
)

val LightPalette = Palette(
    bg = Color(0xFFF3F5FA),
    bgSoft = Color(0xFFEAEEF6),
    card = Color(0xFFFFFFFF),
    cardAlt = Color(0xFFEEF1F8),
    border = Color(0xFFDBE1EE),
    borderSoft = Color(0xFFE6EAF3),
    text = Color(0xFF10151F),
    muted = Color(0xFF5C6478),
    mutedDim = Color(0xFF838BA0),
    brand = Color(0xFF4F7CFF),
    brandDim = Color(0xFFD7E0FA),
    brandInk = Color(0xFF061022),
    gold = Color(0xFFA3752B),
    emerald = Color(0xFF1F9C69),
    coral = Color(0xFFCF453C),
    sapphire = Color(0xFF3169C9),
)

val LocalPalette = staticCompositionLocalOf { DarkPalette }
