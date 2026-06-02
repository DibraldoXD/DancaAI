package com.dancaai.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dancaai.app.data.MockRepository
import com.dancaai.app.ui.components.DcaCard
import com.dancaai.app.ui.components.DcaChip
import com.dancaai.app.ui.components.DcaIconButton
import com.dancaai.app.ui.components.DcaTopBar
import com.dancaai.app.ui.components.HistoryBars
import com.dancaai.app.ui.components.ScoreInline
import com.dancaai.app.ui.theme.DcaTheme
import com.dancaai.app.ui.theme.MonoFontFamily

private data class Filter(val id: String, val label: String)

@Composable
fun HistoryScreen(modifier: Modifier = Modifier) {
    val colors = DcaTheme.colors
    var filter by remember { mutableStateOf("all") }
    val filters = listOf(
        Filter("all", "Todos"), Filter("forró", "Forró"), Filter("zouk", "Zouk"),
        Filter("samba", "Samba"), Filter("bolero", "Bolero"),
    )
    val sessions = MockRepository.sessions.filter {
        filter == "all" || it.styleName.lowercase().contains(filter)
    }

    Column(modifier = modifier.fillMaxSize().background(colors.bg)) {
        DcaTopBar(
            title = "Histórico",
            actions = { DcaIconButton(Icons.Rounded.Tune, "Filtros", onClick = {}) },
        )

        LazyColumn(modifier = Modifier.weight(1f), contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 16.dp)) {
            item {
                Column(Modifier.padding(horizontal = 24.dp)) {
                    DcaCard(padding = 16) {
                        Row(verticalAlignment = Alignment.Top) {
                            Column(Modifier.weight(1f)) {
                                Text("ÚLTIMOS 30 DIAS", style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceDim)
                                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
                                    Text("${MockRepository.monthlyAverage}", fontFamily = MonoFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 28.sp, color = colors.onSurface)
                                    Text("↑ ${MockRepository.monthlyDeltaPercent}%", style = MaterialTheme.typography.labelMedium, color = colors.good, fontWeight = FontWeight.SemiBold)
                                }
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("${MockRepository.monthlySessions}", fontFamily = MonoFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = colors.onSurface)
                                Text("sessões", style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceDim)
                            }
                        }
                        Spacer(Modifier.height(14.dp))
                        HistoryBars(MockRepository.monthlyBars)
                    }
                }
            }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    filters.forEach { f ->
                        DcaChip(f.label, active = filter == f.id, onClick = { filter = f.id })
                    }
                }
            }
            items(sessions, key = { it.id }) { s ->
                Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 8.dp)) {
                    DcaCard(padding = 14) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            EmojiTile(s.emoji, 44)
                            Column(Modifier.weight(1f)) {
                                Text(s.styleName, style = MaterialTheme.typography.titleMedium, color = colors.onSurface)
                                Text("${s.date} · ${s.durationMin} min", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVar)
                            }
                            ScoreInline("P", s.posture)
                            Spacer(Modifier.padding(horizontal = 5.dp))
                            ScoreInline("R", s.rhythm)
                        }
                    }
                }
            }
            if (sessions.isEmpty()) {
                item {
                    Text(
                        "Nenhuma sessão para este filtro",
                        style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceDim,
                        modifier = Modifier.fillMaxWidth().padding(40.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                }
            }
        }
    }
}
