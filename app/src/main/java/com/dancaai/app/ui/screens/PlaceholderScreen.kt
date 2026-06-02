package com.dancaai.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dancaai.app.ui.components.DcaFilledButton
import com.dancaai.app.ui.components.DcaTextButton
import com.dancaai.app.ui.theme.DcaTheme

/**
 * Tela temporária da Fase 2: prova a navegação ponta a ponta.
 * Cada rota real (Onboarding, Início, etc.) substitui este placeholder na Fase 3.
 */
@Composable
fun PlaceholderScreen(
    title: String,
    modifier: Modifier = Modifier,
    primaryLabel: String? = null,
    onPrimary: (() -> Unit)? = null,
    secondaryLabel: String? = null,
    onSecondary: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            color = DcaTheme.colors.onSurface,
            textAlign = TextAlign.Center,
        )
        Text(
            text = "Tela em construção (Fase 3)",
            style = MaterialTheme.typography.bodyMedium,
            color = DcaTheme.colors.onSurfaceVar,
            modifier = Modifier.padding(top = 8.dp, bottom = 28.dp),
        )
        if (primaryLabel != null && onPrimary != null) {
            DcaFilledButton(text = primaryLabel, onClick = onPrimary)
        }
        if (secondaryLabel != null && onSecondary != null) {
            DcaTextButton(text = secondaryLabel, onClick = onSecondary)
        }
    }
}
