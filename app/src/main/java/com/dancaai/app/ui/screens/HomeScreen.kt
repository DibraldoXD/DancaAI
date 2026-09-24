package com.dancaai.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dancaai.app.data.model.HomeSummary
import com.dancaai.app.data.model.Session
import com.dancaai.app.ui.components.DcaCard
import com.dancaai.app.ui.components.DcaIconButton
import com.dancaai.app.ui.components.LineAreaChart
import com.dancaai.app.ui.components.ScoreInline
import com.dancaai.app.ui.components.SectionLabel
import com.dancaai.app.ui.home.HomeViewModel
import com.dancaai.app.ui.theme.DcaTheme
import com.dancaai.app.ui.theme.MonoFontFamily
import com.dancaai.app.ui.theme.Shapes

private val MagentaDark = Color(0xFF7B0F4A)

@Composable
fun HomeScreen(
    onStartTraining: () -> Unit,
    onSeeHistory: () -> Unit,
    onOpenProfile: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = viewModel(),
) {
    val colors = DcaTheme.colors
    val summary by viewModel.summary.collectAsStateWithLifecycle()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.bg)
            .verticalScroll(rememberScrollState())
            .padding(bottom = 16.dp),
    ) {
        val current = summary ?: return@Column

        // saudação
        Row(
            verticalAlignment = Alignment.Top,
            modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 12.dp, top = 24.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(current.greeting, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVar)
                Text(
                    current.firstName.ifEmpty { "Dançarino" },
                    style = MaterialTheme.typography.headlineMedium,
                    color = colors.onSurface,
                )
            }
            DcaIconButton(Icons.Rounded.Notifications, "Notificações", onClick = {})
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .padding(start = 4.dp)
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(colors.surface2)
                    .border(1.dp, colors.outline, CircleShape)
                    .clickable(onClick = onOpenProfile),
            ) {
                Text(
                    current.initial.ifEmpty { "?" },
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.onSurface,
                )
            }
        }

        HeroCard(streakDays = current.streakDays, onStart = onStartTraining)

        // último treino
        Column(Modifier.padding(start = 24.dp, end = 24.dp, top = 24.dp)) {
            SectionLabel("Último treino")
            Spacer(Modifier.height(10.dp))
            when (val last = current.lastSession) {
                null -> EmptyCard("Nenhum treino ainda. Comece o primeiro para acompanhar sua evolução.")
                else -> LastSessionCard(last)
            }
        }

        // evolução semanal
        Column(Modifier.padding(start = 24.dp, end = 24.dp, top = 20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SectionLabel("Evolução desta semana", modifier = Modifier.weight(1f))
                Text(
                    "Ver tudo",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.accent,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clickable(onClick = onSeeHistory),
                )
            }
            Spacer(Modifier.height(10.dp))
            WeekCard(current)
        }
    }
}

@Composable
private fun LastSessionCard(session: Session) {
    val colors = DcaTheme.colors
    DcaCard(padding = 16) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            EmojiTile(session.emoji, 48)
            Column(Modifier.weight(1f)) {
                Text(
                    "${session.styleName} · ${session.durationMin} min",
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.onSurface,
                )
                Text(session.date, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVar)
            }
            ScoreInline("Postura", session.scores.posture)
            Spacer(Modifier.size(10.dp))
            ScoreInline("Ritmo", session.scores.rhythm)
        }
    }
}

@Composable
private fun WeekCard(summary: HomeSummary) {
    val colors = DcaTheme.colors
    DcaCard(padding = 16) {
        Row(verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) {
                Text("Score médio", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVar)
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        summary.weekAverage?.toString() ?: "—",
                        fontFamily = MonoFontFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 30.sp,
                        color = if (summary.weekAverage != null) colors.onSurface else colors.onSurfaceDim,
                    )
                    summary.weekDeltaPercent?.let { delta ->
                        Text(
                            "${if (delta >= 0) "↑" else "↓"} ${kotlin.math.abs(delta)}%",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (delta >= 0) colors.good else colors.bad,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
            Text(
                "${summary.weekSessions} ${if (summary.weekSessions == 1) "sessão" else "sessões"}",
                style = MaterialTheme.typography.labelMedium,
                color = colors.onSurfaceDim,
            )
        }
        Spacer(Modifier.height(12.dp))
        if (summary.weekScores.size >= 2) {
            LineAreaChart(data = summary.weekScores, showDots = true, modifier = Modifier.height(60.dp))
        } else {
            // sem pontuação medida ainda: o gráfico ficaria vazio e pareceria quebrado
            Text(
                "A evolução aparece aqui assim que as sessões tiverem pontuação.",
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceDim,
                modifier = Modifier.padding(vertical = 16.dp),
            )
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            summary.weekDays.forEach { day ->
                Text(day, style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceDim)
            }
        }
    }
}

@Composable
private fun EmptyCard(message: String) {
    DcaCard(padding = 16) {
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = DcaTheme.colors.onSurfaceVar,
        )
    }
}

@Composable
private fun HeroCard(streakDays: Int, onStart: () -> Unit) {
    val colors = DcaTheme.colors
    Box(Modifier.padding(start = 24.dp, end = 24.dp, top = 24.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(Shapes.xl)
                .background(Brush.linearGradient(listOf(colors.accent, MagentaDark)))
                .padding(24.dp),
        ) {
            Column {
                Text(
                    "HOJE",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.9f),
                )
                Text(
                    "Pronto para o próximo treino?",
                    style = MaterialTheme.typography.headlineMedium.copy(fontSize = 26.sp),
                    color = Color.White,
                    modifier = Modifier.padding(top = 8.dp),
                )
                Text(
                    if (streakDays > 0) {
                        "Sua sequência: $streakDays ${if (streakDays == 1) "dia" else "dias"} 🔥"
                    } else {
                        "Comece uma sequência hoje 🔥"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.9f),
                    modifier = Modifier.padding(top = 6.dp),
                )
                Spacer(Modifier.height(20.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .height(52.dp)
                        .clip(Shapes.pill)
                        .background(Color.White)
                        .clickable(onClick = onStart)
                        .padding(horizontal = 24.dp),
                ) {
                    Icon(Icons.Rounded.PlayArrow, contentDescription = null, tint = colors.accent, modifier = Modifier.size(22.dp))
                    Text("Iniciar Treino", style = MaterialTheme.typography.titleMedium, color = colors.accent, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
internal fun EmojiTile(emoji: String, sizeDp: Int) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(sizeDp.dp).clip(Shapes.md).background(DcaTheme.colors.surface2),
    ) {
        Text(emoji, fontSize = (sizeDp * 0.46).sp)
    }
}
