package com.dancaai.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// ─────────────────────────────────────────────────────────────
// Material 3 não tem slots para todos os tokens do design
// (surface1/2/3, accent-soft, good/warn/bad, onSurfaceVar/Dim...).
// Por isso expomos um conjunto extra via CompositionLocal, acessível
// por DcaTheme.colors, e mapeamos os principais no ColorScheme do M3.
// ─────────────────────────────────────────────────────────────
@Immutable
data class DcaColors(
    val bg: Color = Bg,
    val surface: Color = SurfaceBase,
    val surface1: Color = Surface1,
    val surface2: Color = Surface2,
    val surface3: Color = Surface3,
    val outline: Color = OutlineColor,
    val outlineSoft: Color = OutlineSoft,
    val onSurface: Color = OnSurfaceColor,
    val onSurfaceStrong: Color = OnSurfaceStrong,
    val onSurfaceVar: Color = OnSurfaceVar,
    val onSurfaceDim: Color = OnSurfaceDim,
    val accent: Color = Accent,
    val accentDim: Color = AccentDim,
    val accentSoft: Color = AccentSoft,
    val onAccent: Color = OnAccent,
    val good: Color = Good,
    val goodDim: Color = GoodDim,
    val warn: Color = Warn,
    val warnDim: Color = WarnDim,
    val bad: Color = Bad,
    val badDim: Color = BadDim,
)

val LocalDcaColors = staticCompositionLocalOf { DcaColors() }

private val DcaColorScheme = darkColorScheme(
    primary = Accent,
    onPrimary = OnAccent,
    background = Bg,
    onBackground = OnSurfaceColor,
    surface = SurfaceBase,
    onSurface = OnSurfaceColor,
    surfaceVariant = Surface2,
    onSurfaceVariant = OnSurfaceVar,
    outline = OutlineColor,
    outlineVariant = OutlineSoft,
    error = Bad,
)

@Composable
fun DancaAITheme(content: @Composable () -> Unit) {
    androidx.compose.runtime.CompositionLocalProvider(
        LocalDcaColors provides DcaColors()
    ) {
        MaterialTheme(
            colorScheme = DcaColorScheme,
            typography = DcaTypography,
            content = content,
        )
    }
}

/** Acesso conveniente aos tokens estendidos: DcaTheme.colors.accentSoft etc. */
object DcaTheme {
    val colors: DcaColors
        @Composable get() = LocalDcaColors.current
}
