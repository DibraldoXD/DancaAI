package com.dancaai.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.dancaai.app.ui.theme.DancaAITheme
import com.dancaai.app.ui.theme.DcaTheme

/**
 * Galeria de referência do design system (equivale ao artboard "Componentes"
 * do design). Não é usada em runtime — serve só para @Preview no Android Studio
 * e como teste de compilação dos componentes da Fase 1.
 */
@Preview(showBackground = true, backgroundColor = 0xFF0E0E10, heightDp = 900)
@Composable
private fun DesignSystemGallery() {
    DancaAITheme {
        var selectedTab by remember { mutableStateOf(DcaTab.Home) }
        var chipActive by remember { mutableStateOf("forro") }
        var toggle by remember { mutableStateOf(true) }

        Column(
            modifier = Modifier
                .background(DcaTheme.colors.bg)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Logo(size = 48)

            SectionLabel("Botões")
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                DcaFilledButton("Iniciar", onClick = {}, leadingIcon = Icons.Filled.PlayArrow)
                DcaOutlinedButton("Voltar", onClick = {})
            }
            DcaTextButton("Ver tudo", onClick = {})

            SectionLabel("Chips de estilo")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DcaChip("Forró", chipActive == "forro", { chipActive = "forro" }, leading = "💃")
                DcaChip("Zouk", chipActive == "zouk", { chipActive = "zouk" }, leading = "🌊")
                DcaChip("Bolero", chipActive == "bolero", { chipActive = "bolero" }, leading = "🌙")
            }

            SectionLabel("Score badges")
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                ScoreInline("Ritmo", 84)
                ScoreInline("Postura", 62)
                ScoreInline("Postura", 38)
            }

            SectionLabel("Card")
            DcaCard {
                Text("Forró · 5 min", color = DcaTheme.colors.onSurface)
                Text("Ontem · 21:42", color = DcaTheme.colors.onSurfaceVar)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                SectionLabel("Switch")
                DcaSwitch(checked = toggle, onCheckedChange = { toggle = it })
            }

            Dots(total = 3, index = 1)

            BottomNav(selected = selectedTab, onSelect = { selectedTab = it })
        }
    }
}
