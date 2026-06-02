package com.dancaai.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.dancaai.app.ui.theme.DcaTheme
import com.dancaai.app.ui.theme.Shapes

/** Abas da navegação inferior. A `route` casa com as rotas de navegação (Fase 2). */
enum class DcaTab(
    val route: String,
    val label: String,
    val iconActive: ImageVector,
    val iconInactive: ImageVector,
) {
    Home("home", "Início", Icons.Filled.Home, Icons.Outlined.Home),
    History("history", "Histórico", Icons.Filled.BarChart, Icons.Outlined.BarChart),
    Profile("profile", "Perfil", Icons.Filled.Person, Icons.Outlined.Person),
}

/** Navigation bar M3 com pílula de acento na aba ativa. */
@Composable
fun BottomNav(
    selected: DcaTab,
    onSelect: (DcaTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = DcaTheme.colors
    Column(modifier = modifier.fillMaxWidth().background(colors.surface)) {
        androidx.compose.material3.HorizontalDivider(thickness = 1.dp, color = colors.outlineSoft)
        Row(modifier = Modifier.fillMaxWidth().height(76.dp)) {
        DcaTab.entries.forEach { tab ->
            val active = tab == selected
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier
                    .weight(1f)
                    .clickable { onSelect(tab) },
            ) {
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .width(64.dp)
                        .height(32.dp)
                        .clip(Shapes.pill)
                        .background(if (active) colors.accentSoft else androidx.compose.ui.graphics.Color.Transparent),
                ) {
                    Icon(
                        imageVector = if (active) tab.iconActive else tab.iconInactive,
                        contentDescription = tab.label,
                        tint = if (active) colors.accent else colors.onSurfaceVar,
                        modifier = Modifier.size(22.dp),
                    )
                }
                Text(
                    text = tab.label,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (active) colors.onSurfaceStrong else colors.onSurfaceVar,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
        }
    }
}
