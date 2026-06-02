package com.dancaai.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccessibilityNew
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Straighten
import androidx.compose.material.icons.rounded.TrendingUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dancaai.app.data.MockRepository
import com.dancaai.app.data.model.Improvement
import com.dancaai.app.ui.components.CircleScore
import com.dancaai.app.ui.components.DcaCard
import com.dancaai.app.ui.components.DcaFilledButton
import com.dancaai.app.ui.components.DcaIconButton
import com.dancaai.app.ui.components.DcaOutlinedButton
import com.dancaai.app.ui.components.DcaTopBar
import com.dancaai.app.ui.components.SectionLabel
import com.dancaai.app.ui.theme.DcaTheme
import com.dancaai.app.ui.theme.MonoFontFamily
import com.dancaai.app.ui.theme.Shapes
import com.dancaai.app.ui.theme.scoreColor

@Composable
fun ResultsScreen(onAgain: () -> Unit, onHome: () -> Unit, modifier: Modifier = Modifier) {
    val colors = DcaTheme.colors
    val r = MockRepository.lastResult
    var play by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { play = true }
    val animScore by animateIntAsState(
        targetValue = if (play) r.total else 0,
        animationSpec = tween(1000),
        label = "totalScore",
    )

    Column(modifier = modifier.fillMaxSize().background(colors.bg)) {
        DcaTopBar(
            title = "Resultado da sessão",
            navigationIcon = Icons.Rounded.Close,
            onNavigationClick = onHome,
            actions = { DcaIconButton(Icons.Rounded.IosShare, "Compartilhar", onClick = {}) },
        )

        Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp)) {
            // score circular
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 24.dp)) {
                CircleScore(value = r.total) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("$animScore", fontFamily = MonoFontFamily, fontWeight = FontWeight.Bold, fontSize = 64.sp, color = scoreColor(r.total))
                        Text("SCORE GERAL", style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVar)
                    }
                }
                Text(r.summary, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVar, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 14.dp))
            }

            // breakdown
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                BreakdownCard(Icons.Rounded.AccessibilityNew, "Biomecânica", r.posture, r.postureDelta, r.posturePrev, Modifier.weight(1f))
                BreakdownCard(Icons.Rounded.GraphicEq, "Ritmo", r.rhythm, r.rhythmDelta, r.rhythmPrev, Modifier.weight(1f))
            }

            Spacer(Modifier.height(20.dp))
            SectionLabel("Evolução durante a sessão")
            Spacer(Modifier.height(10.dp))
            DcaCard(padding = 16) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    listOf("0:00", "2:30", "5:00").forEach {
                        Text(it, style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceDim)
                    }
                }
                Spacer(Modifier.height(6.dp))
                com.dancaai.app.ui.components.LineAreaChart(
                    data = r.series, minValue = 30, maxValue = 100, gridLines = listOf(50, 75),
                    modifier = Modifier.height(80.dp),
                )
            }

            Spacer(Modifier.height(16.dp))
            HighlightBox(r.highlightTitle, r.highlightBody)

            Spacer(Modifier.height(20.dp))
            SectionLabel("Pontos a melhorar")
            val icons = listOf(Icons.Rounded.Straighten, Icons.Rounded.AccessibilityNew, Icons.Rounded.Schedule)
            r.improvements.forEachIndexed { i, imp ->
                ImprovementRow(icons[i % icons.size], imp)
            }
            Spacer(Modifier.height(24.dp))
        }

        Row(
            modifier = Modifier.fillMaxWidth().background(colors.bg).padding(horizontal = 24.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            DcaOutlinedButton("Início", onClick = onHome, modifier = Modifier.weight(1f))
            DcaFilledButton("Treinar novamente", onClick = onAgain, leadingIcon = Icons.Rounded.Refresh, modifier = Modifier.weight(2f), height = 48)
        }
    }
}

@Composable
private fun BreakdownCard(icon: ImageVector, label: String, value: Int, delta: Int, prev: Int, modifier: Modifier = Modifier) {
    val colors = DcaTheme.colors
    val up = delta >= 0
    DcaCard(padding = 14, modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(icon, contentDescription = null, tint = colors.onSurfaceVar, modifier = Modifier.size(18.dp))
            Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVar)
        }
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
            Text("$value", fontFamily = MonoFontFamily, fontWeight = FontWeight.Bold, fontSize = 36.sp, color = scoreColor(value))
            Text("${if (up) "↑" else "↓"} ${kotlin.math.abs(delta)}%", style = MaterialTheme.typography.bodySmall, color = if (up) colors.good else colors.bad, fontWeight = FontWeight.SemiBold)
        }
        Text("vs. $prev anterior", style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceDim, modifier = Modifier.padding(top = 2.dp))
    }
}

@Composable
private fun HighlightBox(title: String, body: String) {
    val colors = DcaTheme.colors
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(Shapes.md)
            .background(colors.goodDim)
            .border(1.dp, colors.good.copy(alpha = 0.25f), Shapes.md)
            .padding(14.dp),
    ) {
        Icon(Icons.Rounded.TrendingUp, contentDescription = null, tint = colors.good, modifier = Modifier.size(24.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleMedium, color = colors.good)
            Text(body, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVar, modifier = Modifier.padding(top = 2.dp))
        }
    }
}

@Composable
private fun ImprovementRow(icon: ImageVector, imp: Improvement) {
    val colors = DcaTheme.colors
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(36.dp).clip(Shapes.sm).background(colors.surface2),
        ) {
            Icon(icon, contentDescription = null, tint = colors.onSurface, modifier = Modifier.size(20.dp))
        }
        Column {
            Text(imp.title, style = MaterialTheme.typography.titleMedium, color = colors.onSurface)
            Text(imp.description, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVar, modifier = Modifier.padding(top = 2.dp))
        }
    }
}
