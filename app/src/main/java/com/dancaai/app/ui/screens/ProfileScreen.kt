package com.dancaai.app.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.rounded.CameraOutdoor
import androidx.compose.material.icons.rounded.CenterFocusWeak
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dancaai.app.data.MockRepository
import com.dancaai.app.ui.components.DcaCard
import com.dancaai.app.ui.components.DcaIconButton
import com.dancaai.app.ui.components.DcaSwitch
import com.dancaai.app.ui.components.DcaTopBar
import com.dancaai.app.ui.components.SectionLabel
import com.dancaai.app.ui.theme.DcaTheme
import com.dancaai.app.ui.theme.MonoFontFamily

private val MagentaDark = Color(0xFF7B0F4A)

@Composable
fun ProfileScreen(modifier: Modifier = Modifier) {
    val colors = DcaTheme.colors
    val profile = MockRepository.profile
    var vibrate by remember { mutableStateOf(true) }
    var voice by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxSize().background(colors.bg)) {
        DcaTopBar(
            title = "Perfil",
            actions = { DcaIconButton(Icons.Rounded.Settings, "Configurações", onClick = {}) },
        )

        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            // cabeçalho
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp),
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(80.dp).clip(CircleShape).background(Brush.linearGradient(listOf(colors.accent, MagentaDark))),
                ) {
                    Text(profile.initial, fontWeight = FontWeight.Bold, fontSize = 32.sp, color = Color.White)
                }
                Column(Modifier.weight(1f)) {
                    Text(profile.fullName, style = MaterialTheme.typography.titleLarge, color = colors.onSurface)
                    Text("${profile.level} · ${profile.favoriteStyle}", style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVar)
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 8.dp)) {
                        StatText("${profile.sessionsCount}", "sessões", colors.onSurface)
                        StatText("${profile.streakDays}", "🔥 sequência", colors.accent)
                    }
                }
            }

            Group("Feedback") {
                RowToggle(Icons.Rounded.Vibration, "Vibração", "Pulsos no beat", vibrate) { vibrate = it }
                RowDivider()
                RowToggle(Icons.Rounded.RecordVoiceOver, "Feedback por voz", "Dicas faladas durante o treino", voice) { voice = it }
            }
            Group("Câmera") {
                RowLink(Icons.Rounded.CenterFocusWeak, "Calibrar câmera", "Ajuste para o seu ambiente")
                RowDivider()
                RowLink(Icons.Rounded.CameraOutdoor, "Posicionamento ideal", "Tutorial em vídeo")
            }
            Group("Privacidade") {
                RowInfo(Icons.Rounded.CloudOff, "Tudo no aparelho", "Nada é enviado para a nuvem.")
                RowDivider()
                RowLink(Icons.Rounded.FolderOpen, "Meus dados", "22 sessões, 86 MB")
                RowDivider()
                RowLink(Icons.Rounded.DeleteOutline, "Apagar histórico", danger = true)
            }

            Text(
                "Dança AI v0.4.2 · 100% offline",
                fontFamily = MonoFontFamily, fontSize = 11.sp, color = colors.onSurfaceDim,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
            )
        }
    }
}

@Composable
private fun StatText(value: String, label: String, valueColor: Color) {
    val colors = DcaTheme.colors
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(value, fontFamily = MonoFontFamily, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = valueColor)
        Text(label, style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVar)
    }
}

@Composable
private fun Group(title: String, content: @Composable () -> Unit) {
    Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 16.dp)) {
        SectionLabel(title)
        Spacer(Modifier.height(10.dp))
        DcaCard(padding = 0) { content() }
    }
}

@Composable
private fun RowDivider() {
    HorizontalDivider(thickness = 1.dp, color = DcaTheme.colors.outlineSoft)
}

@Composable
private fun RowToggle(icon: ImageVector, label: String, sub: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val colors = DcaTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.padding(14.dp)) {
        Icon(icon, contentDescription = null, tint = colors.onSurfaceVar, modifier = Modifier.size(22.dp))
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge, color = colors.onSurface)
            Text(sub, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVar)
        }
        DcaSwitch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun RowLink(icon: ImageVector, label: String, sub: String? = null, danger: Boolean = false) {
    val colors = DcaTheme.colors
    val fg = if (danger) colors.bad else colors.onSurface
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.fillMaxWidth().clickable {}.padding(14.dp),
    ) {
        Icon(icon, contentDescription = null, tint = if (danger) colors.bad else colors.onSurfaceVar, modifier = Modifier.size(22.dp))
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge, color = fg)
            if (sub != null) Text(sub, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVar)
        }
        Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = colors.onSurfaceDim, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun RowInfo(icon: ImageVector, label: String, sub: String) {
    val colors = DcaTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.padding(14.dp)) {
        Icon(icon, contentDescription = null, tint = colors.good, modifier = Modifier.size(22.dp))
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge, color = colors.onSurface)
            Text(sub, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVar)
        }
    }
}
