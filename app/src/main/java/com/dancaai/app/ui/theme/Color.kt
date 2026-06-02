package com.dancaai.app.ui.theme

import androidx.compose.ui.graphics.Color

// ─────────────────────────────────────────────────────────────
// Design tokens — espelham o tokens.css do design (Material 3 dark).
// alpha 0.14 ≈ 0x24 nos tokens "soft"/"dim".
// ─────────────────────────────────────────────────────────────

// Surfaces
val Bg = Color(0xFF0E0E10)
val SurfaceBase = Color(0xFF161618)
val Surface1 = Color(0xFF1B1B1F)
val Surface2 = Color(0xFF232328)
val Surface3 = Color(0xFF2C2C32)
val OutlineColor = Color(0xFF2A2A30)
val OutlineSoft = Color(0xFF1F1F23)

// Text
val OnSurfaceColor = Color(0xFFECECEF)
val OnSurfaceStrong = Color(0xFFFFFFFF)
val OnSurfaceVar = Color(0xFFA8A8B0)
val OnSurfaceDim = Color(0xFF6E6E78)

// Accent — magenta
val Accent = Color(0xFFE91E8C)
val AccentDim = Color(0xFF5A1142)
val AccentSoft = Color(0x24E91E8C)
val OnAccent = Color(0xFFFFFFFF)

// Status (semânticas)
val Good = Color(0xFF34D399)
val GoodDim = Color(0x2434D399)
val Warn = Color(0xFFFBBF24)
val WarnDim = Color(0x24FBBF24)
val Bad = Color(0xFFF87171)
val BadDim = Color(0x24F87171)

/**
 * Cor de um score (Postura/Ritmo) pela lógica do design:
 * <50 = vermelho · 50–75 = âmbar · >75 = verde.
 */
fun scoreColor(value: Int): Color = when {
    value >= 75 -> Good
    value >= 50 -> Warn
    else -> Bad
}
