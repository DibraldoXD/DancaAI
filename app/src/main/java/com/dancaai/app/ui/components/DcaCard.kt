package com.dancaai.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dancaai.app.ui.theme.DcaTheme
import com.dancaai.app.ui.theme.Shapes

/** Card padrão do app: surface-1, canto lg, contorno suave. */
@Composable
fun DcaCard(
    modifier: Modifier = Modifier,
    padding: Int = 16,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        shape = Shapes.lg,
        colors = CardDefaults.cardColors(
            containerColor = DcaTheme.colors.surface1,
            contentColor = DcaTheme.colors.onSurface,
        ),
        border = BorderStroke(1.dp, DcaTheme.colors.outlineSoft),
        modifier = modifier,
    ) {
        Column(modifier = Modifier.padding(padding.dp), content = content)
    }
}
