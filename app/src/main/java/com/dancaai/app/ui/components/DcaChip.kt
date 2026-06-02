package com.dancaai.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.dancaai.app.ui.theme.DcaTheme
import com.dancaai.app.ui.theme.Shapes

/** Chip de filtro/estilo. Quando ativo, ganha fundo e contorno de acento. */
@Composable
fun DcaChip(
    label: String,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leading: String? = null,
) {
    val colors = DcaTheme.colors
    val bg = if (active) colors.accentSoft else colors.surface2
    val border = if (active) colors.accent else colors.outline
    val fg = if (active) colors.accent else colors.onSurface

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier
            .defaultMinSize(minHeight = 32.dp)
            .clip(Shapes.sm)
            .background(bg)
            .border(BorderStroke(1.dp, border), Shapes.sm)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        if (leading != null) {
            Text(leading, color = Color.Unspecified, style = MaterialTheme.typography.bodyMedium)
        }
        Text(label, color = fg, style = MaterialTheme.typography.bodySmall)
    }
}
