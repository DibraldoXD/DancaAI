package com.dancaai.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dancaai.app.ui.theme.DcaTheme
import com.dancaai.app.ui.theme.Shapes

/** Quadrado de surface-2 com o emoji do estilo de dança centralizado. */
@Composable
fun EmojiTile(emoji: String, sizeDp: Int, modifier: Modifier = Modifier) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.size(sizeDp.dp).clip(Shapes.md).background(DcaTheme.colors.surface2),
    ) {
        Text(emoji, fontSize = (sizeDp * 0.46).sp)
    }
}
