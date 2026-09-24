package com.dancaai.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dancaai.app.data.model.HistorySummary
import com.dancaai.app.ui.components.DcaCard
import com.dancaai.app.ui.components.DcaIconButton
import com.dancaai.app.ui.components.DcaTopBar
import com.dancaai.app.ui.components.HistoryBars
import com.dancaai.app.ui.components.ScoreInline
import com.dancaai.app.ui.history.HistoryViewModel
import com.dancaai.app.ui.theme.DcaTheme
import com.dancaai.app.ui.theme.MonoFontFamily

@Composable
fun HistoryScreen(
    modifier: Modifier = Modifier,
    viewModel: HistoryViewModel = viewModel(),
) {
    val colors = DcaTheme.colors
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(modifier = modifier.fillMaxSize().background(colors.bg)) {
        DcaTopBar(
            title = "Histórico",
            actions = { DcaIconButton(Icons.Rounded.Tune, "Filtros", onClick = {}) },
        )

        LazyColumn(modifier = Modifier.weight(1f), contentPadding = PaddingValues(bottom = 16.dp)) {
            state.summary?.let { summary ->
                item {
                    Column(Modifier.padding(horizontal = 24.dp)) {
                        MonthlyCard(summary)
                    }
                }
            }

            item { Spacer(Modifier.height(16.dp)) }

            items(state.sessions, key = { it.id }) { session ->
                Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 8.dp)) {
                    DcaCard(padding = 14) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            EmojiTile(session.emoji, 44)
                            Column(Modifier.weight(1f)) {
                                Text(
                                    session.styleName,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = colors.onSurface,
                                )
                                Text(
                                    "${session.date} · ${session.durationMin} min",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.onSurfaceVar,
                                )
                            }
                            ScoreInline("P", session.scores.posture)
                            Spacer(Modifier.padding(horizontal = 5.dp))
                            ScoreInline("R", session.scores.rhythm)
                        }
                    }
                }
            }

            if (!state.loading && state.sessions.isEmpty()) {
                item {
                    Text(
                        "Você ainda não treinou. Sua primeira sessão aparece aqui.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceDim,
                        modifier = Modifier.fillMaxWidth().padding(40.dp),
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

@Composable
private fun MonthlyCard(summary: HistorySummary) {
    val colors = DcaTheme.colors
    DcaCard(padding = 16) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text("ÚLTIMOS 30 DIAS", style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceDim)
                Row(
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(top = 4.dp),
                ) {
                    Text(
                        summary.average?.toString() ?: "—",
                        fontFamily = MonoFontFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 28.sp,
                        color = if (summary.average != null) colors.onSurface else colors.onSurfaceDim,
                    )
                    summary.deltaPercent?.let { delta ->
                        Text(
                            "${if (delta >= 0) "↑" else "↓"} ${kotlin.math.abs(delta)}%",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (delta >= 0) colors.good else colors.bad,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    "${summary.sessions}",
                    fontFamily = MonoFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                    color = colors.onSurface,
                )
                Text(
                    if (summary.sessions == 1) "sessão" else "sessões",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurfaceDim,
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        HistoryBars(summary.bars)
    }
}
