package com.dancaai.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.dancaai.app.ui.theme.DcaTheme
import com.dancaai.app.ui.theme.Shapes

/** Botão primário preenchido (pill, acento, 56dp). */
@Composable
fun DcaFilledButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
    enabled: Boolean = true,
    height: Int = 56,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = Shapes.pill,
        colors = ButtonDefaults.buttonColors(
            containerColor = DcaTheme.colors.accent,
            contentColor = DcaTheme.colors.onAccent,
        ),
        modifier = modifier.height(height.dp),
    ) {
        ButtonContent(text, leadingIcon)
    }
}

/** Botão secundário com contorno (pill, 48dp). */
@Composable
fun DcaOutlinedButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
    enabled: Boolean = true,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        shape = Shapes.pill,
        border = BorderStroke(1.dp, DcaTheme.colors.outline),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = DcaTheme.colors.onSurface),
        modifier = modifier.height(48.dp),
    ) {
        ButtonContent(text, leadingIcon)
    }
}

/** Botão de texto (acento). */
@Composable
fun DcaTextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TextButton(
        onClick = onClick,
        shape = Shapes.pill,
        colors = ButtonDefaults.textButtonColors(contentColor = DcaTheme.colors.accent),
        modifier = modifier,
    ) {
        Text(text, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun ButtonContent(text: String, leadingIcon: ImageVector?) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (leadingIcon != null) {
            Icon(leadingIcon, contentDescription = null, modifier = Modifier.size(22.dp))
        }
        Text(text, style = MaterialTheme.typography.titleMedium)
    }
}
