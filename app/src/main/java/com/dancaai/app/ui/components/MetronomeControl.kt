package com.dancaai.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dancaai.app.audio.Metronome
import com.dancaai.app.ui.theme.DcaTheme
import com.dancaai.app.ui.theme.MonoFontFamily
import com.dancaai.app.ui.theme.Shapes

/**
 * Controle do metrônomo pro HUD do treino: stepper de BPM, play/pause manual e
 * indicador visual do compasso (4 tempos, o 4º destacado como pausa/quebra).
 * Estado (bpm/playing/tique atual) é hoisted — quem chama é dono do [Metronome].
 */
@Composable
fun MetronomeControl(
    bpm: Int,
    playing: Boolean,
    activeBeat: Int,
    onBpmChange: (Int) -> Unit,
    onTogglePlay: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = modifier
            .clip(Shapes.lg)
            .background(Color(0xFF141418).copy(alpha = 0.82f))
            .border(1.dp, Color.White.copy(alpha = 0.1f), Shapes.lg)
            .padding(16.dp),
    ) {
        BeatLabels(activeBeat)

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            StepperButton(Icons.Rounded.Remove, "Diminuir BPM") {
                onBpmChange((bpm - 1).coerceIn(Metronome.MIN_BPM, Metronome.MAX_BPM))
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(56.dp)) {
                Text(
                    "$bpm",
                    fontFamily = MonoFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 26.sp,
                    color = Color.White,
                )
                Text("BPM", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.6f))
            }
            StepperButton(Icons.Rounded.Add, "Aumentar BPM") {
                onBpmChange((bpm + 1).coerceIn(Metronome.MIN_BPM, Metronome.MAX_BPM))
            }
            Spacer(Modifier.width(4.dp))
            PlayPauseButton(playing, onTogglePlay)
        }
    }
}

/** Rótulos do compasso fixo de 4 tempos do metrônomo — reaproveitado pela captura contínua. */
internal val beatLabels = listOf("1", "2", "3", "Pausa")

@Composable
private fun BeatLabels(activeBeat: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        beatLabels.forEachIndexed { i, label ->
            val isPause = i == 3
            val isActive = i == activeBeat
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                color = when {
                    isActive && isPause -> Color(0xFFF59E0B) // laranja: tique de pausa/quebra
                    isActive -> DcaTheme.colors.accent
                    isPause -> Color.White.copy(alpha = 0.45f)
                    else -> Color.White.copy(alpha = 0.35f)
                },
            )
        }
    }
}

@Composable
private fun StepperButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.08f))
            .clickable(onClick = onClick),
    ) {
        Icon(icon, contentDescription = contentDescription, tint = Color.White, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun PlayPauseButton(playing: Boolean, onClick: () -> Unit) {
    val colors = DcaTheme.colors
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(if (playing) colors.accent else Color.White.copy(alpha = 0.12f))
            .clickable(onClick = onClick),
    ) {
        Icon(
            if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
            contentDescription = if (playing) "Pausar metrônomo" else "Tocar metrônomo",
            tint = Color.White,
            modifier = Modifier.size(22.dp),
        )
    }
}
