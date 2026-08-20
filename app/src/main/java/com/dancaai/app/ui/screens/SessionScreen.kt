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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dancaai.app.audio.BeatPattern
import com.dancaai.app.audio.Metronome
import com.dancaai.app.data.DanceCatalog
import com.dancaai.app.data.model.SessionConfig
import com.dancaai.app.ui.components.DcaCard
import com.dancaai.app.ui.components.DcaFilledButton
import com.dancaai.app.ui.components.DcaTopBar
import com.dancaai.app.ui.components.SectionLabel
import com.dancaai.app.ui.theme.DcaTheme
import com.dancaai.app.ui.theme.MonoFontFamily
import com.dancaai.app.ui.theme.Shapes
import kotlin.math.roundToInt

/**
 * Configuração da sessão. O que é escolhido aqui vira o [SessionConfig] que a
 * tela de Treino consome — duração do cronômetro e BPM inicial do metrônomo.
 */
@Composable
fun SessionScreen(
    config: SessionConfig,
    onConfigChange: ((SessionConfig) -> SessionConfig) -> Unit,
    onBack: () -> Unit,
    onStart: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = DcaTheme.colors
    val style = DanceCatalog.styleById(config.styleId)

    Column(modifier = modifier.fillMaxSize().background(colors.bg)) {
        DcaTopBar(
            title = "Nova sessão",
            navigationIcon = Icons.AutoMirrored.Rounded.ArrowBack,
            onNavigationClick = onBack,
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
        ) {
            Spacer(Modifier.height(8.dp))
            SectionLabel("Estilo de dança")
            Spacer(Modifier.height(10.dp))
            DcaCard(padding = 16) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    EmojiTile(style.emoji, 48)
                    Column(Modifier.weight(1f)) {
                        Text(
                            style.name,
                            style = MaterialTheme.typography.titleMedium,
                            color = colors.onSurface,
                        )
                        Text(
                            "${style.bpmRange} BPM",
                            fontFamily = MonoFontFamily,
                            fontSize = 11.sp,
                            color = colors.onSurfaceDim,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
            SectionLabel("Metrônomo")
            Spacer(Modifier.height(10.dp))
            BpmCard(
                bpm = config.bpm,
                bpmRange = style.bpmRange,
                onBpmChange = { bpm -> onConfigChange { it.copy(bpm = bpm) } },
                pattern = config.beatPattern,
                onPatternChange = { pattern -> onConfigChange { it.copy(beatPattern = pattern) } },
            )

            Spacer(Modifier.height(24.dp))
            SectionLabel("Duração")
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SessionConfig.DURATION_OPTIONS.forEach { minutes ->
                    DurationCard(
                        minutes = minutes,
                        selected = config.durationMin == minutes,
                        onClick = { onConfigChange { it.copy(durationMin = minutes) } },
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            Spacer(Modifier.height(24.dp))
            SectionLabel("Posicionamento")
            Spacer(Modifier.height(10.dp))
            DcaCard(padding = 16) {
                CameraDiagram()
                Text(
                    "Apoie o celular em pé num suporte, a ~2 m de distância. " +
                        "Sua figura inteira deve aparecer no enquadramento.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVar,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
            Spacer(Modifier.height(24.dp))
        }

        Box(modifier = Modifier.fillMaxWidth().background(colors.bg).padding(24.dp)) {
            DcaFilledButton(
                "Começar",
                onClick = onStart,
                leadingIcon = Icons.Rounded.PlayArrow,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** Escolha do BPM e do tipo de batida — os dois parâmetros do módulo de ritmo. */
@Composable
private fun BpmCard(
    bpm: Int,
    bpmRange: String,
    onBpmChange: (Int) -> Unit,
    pattern: BeatPattern,
    onPatternChange: (BeatPattern) -> Unit,
) {
    val colors = DcaTheme.colors
    DcaCard(padding = 16) {
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                "$bpm",
                fontFamily = MonoFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 30.sp,
                color = colors.accent,
            )
            Text(
                "BPM",
                style = MaterialTheme.typography.labelSmall,
                color = colors.onSurfaceVar,
                modifier = Modifier.padding(bottom = 6.dp),
            )
            Spacer(Modifier.weight(1f))
            Text(
                "Forró: $bpmRange",
                style = MaterialTheme.typography.labelSmall,
                color = colors.onSurfaceDim,
                modifier = Modifier.padding(bottom = 6.dp),
            )
        }
        Slider(
            value = bpm.toFloat(),
            onValueChange = { onBpmChange(it.roundToInt()) },
            valueRange = Metronome.MIN_BPM.toFloat()..Metronome.MAX_BPM.toFloat(),
            colors = SliderDefaults.colors(
                thumbColor = colors.accent,
                activeTrackColor = colors.accent,
                inactiveTrackColor = colors.surface2,
            ),
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "TIPO DE BATIDA",
            style = MaterialTheme.typography.labelSmall,
            color = colors.onSurfaceDim,
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BeatPattern.entries.forEach { option ->
                PatternCard(
                    pattern = option,
                    selected = pattern == option,
                    onClick = { onPatternChange(option) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            when (pattern) {
                BeatPattern.THREE_AND_PAUSE ->
                    "O 4º tique soa diferente, marcando a quebra do forró."
                BeatPattern.FOUR_EVEN ->
                    "Quatro tempos sem quebra; o 1º tique marca o início do compasso."
            },
            style = MaterialTheme.typography.bodySmall,
            color = colors.onSurfaceVar,
        )
    }
}

@Composable
private fun PatternCard(
    pattern: BeatPattern,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = DcaTheme.colors
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .height(44.dp)
            .clip(Shapes.md)
            .background(if (selected) colors.accentSoft else colors.surface1)
            .border(1.5.dp, if (selected) colors.accent else colors.outlineSoft, Shapes.md)
            .clickable(onClick = onClick),
    ) {
        Text(
            pattern.label,
            fontFamily = MonoFontFamily,
            fontSize = 13.sp,
            color = if (selected) colors.accent else colors.onSurface,
        )
    }
}

@Composable
private fun DurationCard(
    minutes: Int,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
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
        Text(
            "$minutes",
            fontFamily = MonoFontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 20.sp,
            color = if (selected) colors.accent else colors.onSurface,
        )
        Text(
            "min",
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) colors.accent else colors.onSurfaceDim,
        )
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
