package com.dancaai.app.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.dancaai.app.ui.theme.DcaTheme
import com.dancaai.app.ui.theme.MonoFontFamily
import com.dancaai.app.ui.theme.scoreColor

/** Número de score em mono, colorido pela lógica de threshold (<50/50–75/>75). */
@Composable
fun ScoreNumber(
    value: Int,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 36.sp,
) {
    Text(
        text = value.toString(),
        fontFamily = MonoFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = fontSize,
        color = scoreColor(value),
        modifier = modifier,
    )
}

/** Score compacto com rótulo embaixo (usado em listas/cards). */
@Composable
fun ScoreInline(
    label: String,
    value: Int,
    modifier: Modifier = Modifier,
) {
    Column(horizontalAlignment = Alignment.End, modifier = modifier) {
        ScoreNumber(value = value, fontSize = 18.sp)
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = DcaTheme.colors.onSurfaceDim,
            textAlign = TextAlign.End,
        )
    }
}
