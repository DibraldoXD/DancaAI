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
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dancaai.app.BuildConfig
import com.dancaai.app.data.model.ProfileSummary
import com.dancaai.app.export.SheetsUploader
import com.dancaai.app.ui.components.DcaCard
import com.dancaai.app.ui.components.DcaSwitch
import com.dancaai.app.ui.components.DcaTopBar
import com.dancaai.app.ui.components.SectionLabel
import com.dancaai.app.ui.profile.ProfileViewModel
import com.dancaai.app.ui.theme.DcaTheme
import com.dancaai.app.ui.theme.MonoFontFamily
import com.dancaai.app.ui.theme.Shapes

private val MagentaDark = Color(0xFF7B0F4A)

/**
 * Perfil do usuário. Nome e nível vêm do onboarding; contagem de sessões,
 * sequência e espaço ocupado são derivados do histórico gravado — nenhum número
 * aqui é digitado à mão.
 */
@Composable
fun ProfileScreen(
    modifier: Modifier = Modifier,
    viewModel: ProfileViewModel = viewModel(),
) {
    val colors = DcaTheme.colors
    val profile by viewModel.profile.collectAsStateWithLifecycle()
    var confirmingClear by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxSize().background(colors.bg)) {
        DcaTopBar(title = "Perfil")

        val current = profile ?: return@Column

        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            ProfileHeader(current)

            Group("Feedback") {
                RowToggle(
                    icon = Icons.Rounded.Vibration,
                    label = "Vibração",
                    sub = "Pulsos no beat",
                    checked = current.vibrationEnabled,
                    onChange = viewModel::setVibrationEnabled,
                )
                RowDivider()
                RowToggle(
                    icon = Icons.Rounded.RecordVoiceOver,
                    label = "Feedback por voz",
                    sub = "Dicas faladas durante o treino",
                    checked = current.voiceFeedbackEnabled,
                    onChange = viewModel::setVoiceFeedbackEnabled,
                )
            }

            Group("Privacidade") {
                // O selo acompanha a configuração real do envio: com a URL de coleta
                // definida em local.properties, dizer "nada sai do aparelho" seria falso.
                if (SheetsUploader.enabled) {
                    RowInfo(
                        icon = Icons.Rounded.CloudUpload,
                        label = "Coleta de dados ativa",
                        sub = "As capturas de pontos corporais do treino são enviadas " +
                            "para a planilha de treino dos modelos.",
                        tint = colors.warn,
                    )
                } else {
                    RowInfo(
                        icon = Icons.Rounded.CloudOff,
                        label = "Tudo no aparelho",
                        sub = "Nada é enviado para a nuvem.",
                        tint = colors.good,
                    )
                }
                RowDivider()
                RowInfo(
                    icon = Icons.Rounded.FolderOpen,
                    label = "Meus dados",
                    sub = "${current.sessionsCount} ${sessionWord(current.sessionsCount)} · " +
                        current.storageBytes.asStorageSize(),
                    tint = colors.onSurfaceVar,
                )
                RowDivider()
                RowLink(
                    icon = Icons.Rounded.DeleteOutline,
                    label = "Apagar histórico",
                    sub = if (current.sessionsCount > 0) null else "Nada gravado ainda",
                    danger = true,
                    enabled = current.sessionsCount > 0,
                    onClick = { confirmingClear = true },
                )
            }

            Text(
                "Dança AI v${BuildConfig.VERSION_NAME} · " +
                    if (SheetsUploader.enabled) "coleta ativa" else "100% offline",
                fontFamily = MonoFontFamily,
                fontSize = 11.sp,
                color = colors.onSurfaceDim,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
            )
        }
    }

    // Fora do Column porque o corpo da tela sai cedo enquanto o perfil não chegou.
    val summary = profile
    if (confirmingClear && summary != null) {
        ClearHistoryDialog(
            sessionsCount = summary.sessionsCount,
            onConfirm = {
                viewModel.clearHistory()
                confirmingClear = false
            },
            onDismiss = { confirmingClear = false },
        )
    }
}

@Composable
private fun ProfileHeader(profile: ProfileSummary) {
    val colors = DcaTheme.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(80.dp)
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(colors.accent, MagentaDark))),
        ) {
            Text(
                profile.initial.ifEmpty { "?" },
                fontWeight = FontWeight.Bold,
                fontSize = 32.sp,
                color = Color.White,
            )
        }
        Column(Modifier.weight(1f)) {
            // Quem pulou o onboarding não tem nome gravado; a Home usa o mesmo apelido.
            Text(
                profile.name.ifEmpty { "Dançarino" },
                style = MaterialTheme.typography.titleLarge,
                color = colors.onSurface,
            )
            Text(
                listOf(profile.level, profile.favoriteStyle)
                    .filter { it.isNotEmpty() }
                    .joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVar,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(top = 8.dp),
            ) {
                StatText(
                    "${profile.sessionsCount}",
                    sessionWord(profile.sessionsCount),
                    colors.onSurface,
                )
                StatText("${profile.streakDays}", "🔥 sequência", colors.accent)
            }
        }
    }
}

@Composable
private fun ClearHistoryDialog(sessionsCount: Int, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val colors = DcaTheme.colors
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surface1,
        shape = Shapes.lg,
        title = {
            Text(
                "Apagar histórico?",
                style = MaterialTheme.typography.titleLarge,
                color = colors.onSurface,
            )
        },
        text = {
            Text(
                "$sessionsCount ${sessionWord(sessionsCount)} e todas as medições serão " +
                    "apagadas do aparelho. Não há como desfazer.",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVar,
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Apagar", color = colors.bad, fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar", color = colors.onSurfaceVar)
            }
        },
    )
}

@Composable
private fun StatText(value: String, label: String, valueColor: Color) {
    val colors = DcaTheme.colors
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            value,
            fontFamily = MonoFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
            color = valueColor,
        )
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
private fun RowToggle(
    icon: ImageVector,
    label: String,
    sub: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    val colors = DcaTheme.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.padding(14.dp),
    ) {
        Icon(icon, contentDescription = null, tint = colors.onSurfaceVar, modifier = Modifier.size(22.dp))
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge, color = colors.onSurface)
            Text(sub, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVar)
        }
        DcaSwitch(checked = checked, onCheckedChange = onChange)
    }
}

/**
 * Linha que leva a uma ação. O [onClick] é obrigatório de propósito: a versão
 * anterior tinha um `clickable {}` fixo no componente, e por isso toda linha do
 * Perfil parecia clicável sem fazer nada.
 */
@Composable
private fun RowLink(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    sub: String? = null,
    danger: Boolean = false,
    enabled: Boolean = true,
) {
    val colors = DcaTheme.colors
    val fg = when {
        !enabled -> colors.onSurfaceDim
        danger -> colors.bad
        else -> colors.onSurface
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(14.dp),
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = when {
                !enabled -> colors.onSurfaceDim
                danger -> colors.bad
                else -> colors.onSurfaceVar
            },
            modifier = Modifier.size(22.dp),
        )
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge, color = fg)
            if (sub != null) {
                Text(sub, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVar)
            }
        }
        if (enabled) {
            Icon(
                Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = colors.onSurfaceDim,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun RowInfo(icon: ImageVector, label: String, sub: String, tint: Color) {
    val colors = DcaTheme.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.padding(14.dp),
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge, color = colors.onSurface)
            Text(sub, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVar)
        }
    }
}

internal fun sessionWord(count: Int): String = if (count == 1) "sessão" else "sessões"

/** Tamanho legível do banco. Em bytes só faria sentido para quem escreveu o app. */
internal fun Long.asStorageSize(): String = when {
    this < 1_024L -> "$this B"
    this < 1_048_576L -> "%.0f KB".format(this / 1_024f)
    else -> "%.1f MB".format(this / 1_048_576f)
}
