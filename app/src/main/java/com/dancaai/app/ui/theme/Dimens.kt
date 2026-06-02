package com.dancaai.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

// ─────────────────────────────────────────────────────────────
// Raios de canto — Material 3 (tokens --r-* do design).
// ─────────────────────────────────────────────────────────────
object Radii {
    val sm = 8.dp
    val md = 12.dp
    val lg = 20.dp
    val xl = 28.dp
    val pill = 999.dp
}

object Shapes {
    val sm = RoundedCornerShape(Radii.sm)
    val md = RoundedCornerShape(Radii.md)
    val lg = RoundedCornerShape(Radii.lg)
    val xl = RoundedCornerShape(Radii.xl)
    val pill = RoundedCornerShape(Radii.pill)
}
