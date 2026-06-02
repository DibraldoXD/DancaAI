package com.dancaai.app.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dancaai.app.ui.theme.DcaTheme

/** Botão de ícone redondo padrão (40dp), tint onSurface. */
@Composable
fun DcaIconButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: androidx.compose.ui.graphics.Color = DcaTheme.colors.onSurface,
) {
    IconButton(onClick = onClick, modifier = modifier.size(40.dp)) {
        Icon(icon, contentDescription = contentDescription, tint = tint, modifier = Modifier.size(24.dp))
    }
}

/** App bar do app (56dp): ícone de navegação opcional + título + ações. */
@Composable
fun DcaTopBar(
    title: String,
    modifier: Modifier = Modifier,
    navigationIcon: ImageVector? = null,
    onNavigationClick: () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .padding(start = if (navigationIcon != null) 4.dp else 16.dp, end = 4.dp),
    ) {
        if (navigationIcon != null) {
            DcaIconButton(navigationIcon, "Voltar", onNavigationClick)
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = DcaTheme.colors.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .padding(start = if (navigationIcon != null) 8.dp else 0.dp),
        )
        actions()
    }
}
