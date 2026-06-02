package com.dancaai.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// ─────────────────────────────────────────────────────────────
// Tipografia. O design usa Inter (UI) + JetBrains Mono (números/score).
// Por decisão do projeto, usamos as fontes do sistema (Roboto ≈ Inter).
// Para adotar Inter/JetBrains Mono no futuro: coloque os .ttf em
// res/font e troque UiFontFamily/MonoFontFamily por
//   FontFamily(Font(R.font.inter_regular), Font(R.font.inter_medium, FontWeight.Medium), ...)
// (mantém o app 100% offline, sem rede).
// ─────────────────────────────────────────────────────────────
val UiFontFamily = FontFamily.Default
val MonoFontFamily = FontFamily.Monospace

/** Estilos monoespaçados para números, scores, BPM e ângulos. */
object MonoType {
    val score = TextStyle(
        fontFamily = MonoFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 48.sp,
        lineHeight = 48.sp,
        letterSpacing = (-0.5).sp,
    )
    val data = TextStyle(
        fontFamily = MonoFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
    )
}

val DcaTypography = Typography(
    // Display — marca / hero
    displayLarge = TextStyle(
        fontFamily = UiFontFamily, fontWeight = FontWeight.Bold,
        fontSize = 40.sp, lineHeight = 44.sp, letterSpacing = (-1).sp,
    ),
    // Title L — "Pronto para o próximo treino?"
    headlineMedium = TextStyle(
        fontFamily = UiFontFamily, fontWeight = FontWeight.Bold,
        fontSize = 28.sp, lineHeight = 32.sp, letterSpacing = (-0.5).sp,
    ),
    // Title M — app bar
    titleLarge = TextStyle(
        fontFamily = UiFontFamily, fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp, lineHeight = 28.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = UiFontFamily, fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp, lineHeight = 20.sp,
    ),
    // Body L
    bodyLarge = TextStyle(
        fontFamily = UiFontFamily, fontWeight = FontWeight.Medium,
        fontSize = 16.sp, lineHeight = 22.sp,
    ),
    // Body
    bodyMedium = TextStyle(
        fontFamily = UiFontFamily, fontWeight = FontWeight.Normal,
        fontSize = 14.sp, lineHeight = 19.sp,
    ),
    // Caption
    bodySmall = TextStyle(
        fontFamily = UiFontFamily, fontWeight = FontWeight.Normal,
        fontSize = 13.sp, lineHeight = 18.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = UiFontFamily, fontWeight = FontWeight.Medium,
        fontSize = 12.sp, lineHeight = 16.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = UiFontFamily, fontWeight = FontWeight.SemiBold,
        fontSize = 11.sp, lineHeight = 14.sp, letterSpacing = 0.8.sp,
    ),
)
