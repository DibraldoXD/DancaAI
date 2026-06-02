package com.dancaai.app.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dancaai.app.data.MockRepository
import com.dancaai.app.data.model.DanceStyle
import com.dancaai.app.ui.components.DcaCard
import com.dancaai.app.ui.components.DcaFilledButton
import com.dancaai.app.ui.components.DcaIconButton
import com.dancaai.app.ui.components.DcaTopBar
import com.dancaai.app.ui.components.SectionLabel
import com.dancaai.app.ui.theme.DcaTheme
import com.dancaai.app.ui.theme.MonoFontFamily
import com.dancaai.app.ui.theme.Shapes

private val MagentaDark = Color(0xFF7B0F4A)

@Composable
fun SessionScreen(onBack: () -> Unit, onStart: () -> Unit, modifier: Modifier = Modifier) {
    val colors = DcaTheme.colors
    val music = MockRepository.defaultMusic
    var styleId by remember { mutableStateOf("forro") }
    var duration by remember { mutableIntStateOf(5) }

    Column(modifier = modifier.fillMaxSize().background(colors.bg)) {
        DcaTopBar(title = "Nova sessão", navigationIcon = Icons.AutoMirrored.Rounded.ArrowBack, onNavigationClick = onBack)

        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
        ) {
            Spacer(Modifier.height(8.dp))
            SectionLabel("Estilo de dança")
            Spacer(Modifier.height(10.dp))
            val styles = MockRepository.styles
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                styles.chunked(2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        row.forEach { s ->
                            StyleCard(s, selected = styleId == s.id, onClick = { styleId = s.id }, modifier = Modifier.weight(1f))
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
            SectionLabel("Música")
            Spacer(Modifier.height(10.dp))
            DcaCard(padding = 14) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.size(56.dp).clip(Shapes.md)
                            .background(Brush.linearGradient(listOf(colors.accent, MagentaDark))),
                    ) {
                        Icon(Icons.Rounded.MusicNote, contentDescription = null, tint = Color.White, modifier = Modifier.size(28.dp))
                    }
                    Column(Modifier.weight(1f)) {
                        Text(music.title, style = MaterialTheme.typography.titleMedium, color = colors.onSurface, maxLines = 1)
                        Text("${music.artist} · ${music.duration}", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVar)
                    }
                    DcaIconButton(Icons.Rounded.SwapHoriz, "Trocar música", onClick = {})
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DcaCard(padding = 14, modifier = Modifier.weight(1f)) {
                    Text("BPM DETECTADO", style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceDim)
                    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 4.dp)) {
                        Text("${music.bpm}", fontFamily = MonoFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 26.sp, color = colors.accent)
                        Text("· ideal p/ Forró", style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVar)
                    }
                }
                DcaCard(padding = 14, modifier = Modifier.width(110.dp)) {
                    Text("TOM", style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceDim)
                    Text(music.key, fontFamily = MonoFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 26.sp, color = colors.onSurface, modifier = Modifier.padding(top = 4.dp))
                }
            }

            Spacer(Modifier.height(24.dp))
            SectionLabel("Duração")
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(3, 5, 10).forEach { m ->
                    DurationCard(m, selected = duration == m, onClick = { duration = m }, modifier = Modifier.weight(1f))
                }
            }

            Spacer(Modifier.height(24.dp))
            SectionLabel("Posicionamento")
            Spacer(Modifier.height(10.dp))
            DcaCard(padding = 16) {
                CameraDiagram()
                Text(
                    "Apoie o celular em pé num suporte, a ~2 m de distância. Sua figura inteira deve aparecer no enquadramento.",
                    style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVar,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
            Spacer(Modifier.height(24.dp))
        }

        Box(
            modifier = Modifier.fillMaxWidth().background(colors.bg)
                .border(0.dp, Color.Transparent).padding(24.dp),
        ) {
            DcaFilledButton("Começar", onClick = onStart, leadingIcon = Icons.Rounded.PlayArrow, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun StyleCard(style: DanceStyle, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = DcaTheme.colors
    Column(
        verticalArrangement = Arrangement.SpaceBetween,
        modifier = modifier
            .aspectRatio(1.4f)
            .clip(Shapes.md)
            .background(if (selected) colors.accentSoft else colors.surface1)
            .border(1.5.dp, if (selected) colors.accent else colors.outlineSoft, Shapes.md)
            .clickable(onClick = onClick)
            .padding(14.dp),
    ) {
        Text(style.emoji, fontSize = 28.sp)
        Column {
            Text(style.name, style = MaterialTheme.typography.titleMedium, color = if (selected) colors.accent else colors.onSurface)
            Text("${style.bpmRange} BPM", fontFamily = MonoFontFamily, fontSize = 11.sp, color = colors.onSurfaceDim, modifier = Modifier.padding(top = 2.dp))
        }
    }
}

@Composable
private fun DurationCard(minutes: Int, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = DcaTheme.colors
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier
            .height(56.dp)
            .clip(Shapes.md)
            .background(if (selected) colors.accentSoft else colors.surface1)
            .border(1.5.dp, if (selected) colors.accent else colors.outlineSoft, Shapes.md)
            .clickable(onClick = onClick),
    ) {
        Text("$minutes", fontFamily = MonoFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, color = if (selected) colors.accent else colors.onSurface)
        Text("min", style = MaterialTheme.typography.labelSmall, color = if (selected) colors.accent else colors.onSurfaceDim)
    }
}

/** Diagrama simplificado de posicionamento da câmera (~2 m). */
@Composable
private fun CameraDiagram() {
    val colors = DcaTheme.colors
    Canvas(modifier = Modifier.fillMaxWidth().height(110.dp)) {
        val w = size.width
        val h = size.height
        val floorY = h * 0.82f
        // chão
        drawLine(colors.outline, Offset(0f, floorY), Offset(w, floorY), strokeWidth = 1f)
        // cone de visão
        val phoneX = w * 0.12f
        val personX = w * 0.74f
        val camY = floorY - 30f
        drawLine(colors.accent.copy(alpha = 0.5f), Offset(phoneX, camY), Offset(personX, floorY - 80f), strokeWidth = 1f)
        drawLine(colors.accent.copy(alpha = 0.5f), Offset(phoneX, camY), Offset(personX, floorY), strokeWidth = 1f)
        // "celular"
        drawRoundRect(
            color = colors.accent,
            topLeft = Offset(phoneX - 7f, camY - 18f),
            size = androidx.compose.ui.geometry.Size(14f, 26f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(3f, 3f),
            style = Stroke(width = 2f),
        )
        // tripé
        drawLine(colors.onSurfaceVar, Offset(phoneX, camY + 8f), Offset(phoneX, floorY), strokeWidth = 2f)
        // pessoa (boneco)
        val s = colors.onSurface
        drawCircle(s, radius = 6f, center = Offset(personX, floorY - 70f), style = Stroke(width = 2f))
        drawLine(s, Offset(personX, floorY - 64f), Offset(personX, floorY - 30f), strokeWidth = 2f, cap = StrokeCap.Round)
        drawLine(s, Offset(personX, floorY - 54f), Offset(personX - 12f, floorY - 42f), strokeWidth = 2f, cap = StrokeCap.Round)
        drawLine(s, Offset(personX, floorY - 54f), Offset(personX + 12f, floorY - 42f), strokeWidth = 2f, cap = StrokeCap.Round)
        drawLine(s, Offset(personX, floorY - 30f), Offset(personX - 10f, floorY), strokeWidth = 2f, cap = StrokeCap.Round)
        drawLine(s, Offset(personX, floorY - 30f), Offset(personX + 10f, floorY), strokeWidth = 2f, cap = StrokeCap.Round)
    }
}
