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
import androidx.compose.material.icons.rounded.Balance
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.automirrored.rounded.DirectionsRun
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dancaai.app.data.model.SessionOutcome
import com.dancaai.app.ui.components.DcaCard
import com.dancaai.app.ui.components.DcaFilledButton
import com.dancaai.app.ui.components.DcaOutlinedButton
import com.dancaai.app.ui.components.DcaTopBar
import com.dancaai.app.ui.components.SectionLabel
import com.dancaai.app.ui.components.navigationBarsOrMinPadding
import com.dancaai.app.ui.theme.DcaTheme
import com.dancaai.app.ui.theme.MonoFontFamily
import com.dancaai.app.ui.theme.Shapes
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Resultado da sessão, com um cartão por módulo de análise.
 *
 * Os cartões exibem as medições brutas — proporção de tempo, de acertos e desvio
 * médio —, não uma pontuação de 0 a 100. A fórmula de score será definida após a
 * calibração com praticantes, e converter as medições agora daria ao número uma
 * autoridade que ele ainda não tem.
 */
@Composable
fun ResultsScreen(
    outcome: SessionOutcome?,
    onAgain: () -> Unit,
    onHome: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = DcaTheme.colors

    Column(modifier = modifier.fillMaxSize().background(colors.bg)) {
        DcaTopBar(
            title = "Resultado da sessão",
            navigationIcon = Icons.Rounded.Close,
            onNavigationClick = onHome,
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
        ) {
            when (outcome) {
                null -> LoadingNotice()
                else -> OutcomeContent(outcome)
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.bg)
                .padding(start = 24.dp, top = 12.dp, end = 24.dp)
                .navigationBarsOrMinPadding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            DcaOutlinedButton("Início", onClick = onHome, modifier = Modifier.weight(1f))
            DcaFilledButton(
                "Treinar novamente",
                onClick = onAgain,
                leadingIcon = Icons.Rounded.Refresh,
                modifier = Modifier.weight(2f),
                height = 48,
            )
        }
    }
}

@Composable
private fun LoadingNotice() {
    Text(
        "Salvando a sessão…",
        style = MaterialTheme.typography.bodyMedium,
        color = DcaTheme.colors.onSurfaceDim,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
    )
}

@Composable
private fun OutcomeContent(outcome: SessionOutcome) {
    val colors = DcaTheme.colors
    val session = outcome.session
    val metrics = session.metrics
    val previous = outcome.previous?.metrics

    Spacer(Modifier.height(12.dp))
    SessionHeader(outcome)

    Spacer(Modifier.height(24.dp))
    SectionLabel("Medições por módulo")
    Spacer(Modifier.height(10.dp))

    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        ModuleCard(
            icon = Icons.Rounded.AccessibilityNew,
            label = "Postura",
            value = metrics.goodPostureRatio.asPercent(),
            detail = if (metrics.poseFrames > 0) "do tempo conforme" else "sem pose detectada",
            deltaPp = pointsDelta(metrics.goodPostureRatio, previous?.goodPostureRatio),
            modifier = Modifier.weight(1f),
        )
        ModuleCard(
            icon = Icons.Rounded.Balance,
            label = "Peso",
            value = metrics.correctTransitionRatio.asPercent(),
            detail = if (metrics.totalTransitions > 0) {
                "${metrics.correctTransitions} de ${metrics.totalTransitions} trocas"
            } else {
                "sem trocas de apoio"
            },
            deltaPp = pointsDelta(metrics.correctTransitionRatio, previous?.correctTransitionRatio),
            modifier = Modifier.weight(1f),
        )
    }

    Spacer(Modifier.height(10.dp))

    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        ModuleCard(
            icon = Icons.Rounded.GraphicEq,
            label = "Ritmo",
            value = metrics.rhythm.onTimeRatio.asPercent(),
            detail = metrics.rhythm.meanAbsOffsetMs?.let { "desvio médio ${it.roundToInt()} ms" }
                ?: "metrônomo desligado",
            deltaPp = pointsDelta(metrics.rhythm.onTimeRatio, previous?.rhythm?.onTimeRatio),
            modifier = Modifier.weight(1f),
        )
        ModuleCard(
            icon = Icons.AutoMirrored.Rounded.DirectionsRun,
            label = "Movimentos",
            value = "—",
            detail = "módulo em desenvolvimento",
            deltaPp = null,
            modifier = Modifier.weight(1f),
        )
    }

    if (metrics.isEmpty) {
        Spacer(Modifier.height(16.dp))
        EmptyMetricsNotice()
    }

    val issues = metrics.topPostureIssues
    if (issues.isNotEmpty()) {
        Spacer(Modifier.height(24.dp))
        SectionLabel("Pontos a melhorar")
        Spacer(Modifier.height(4.dp))
        issues.take(MAX_ISSUES_SHOWN).forEach { (issue, count) ->
            ImprovementRow(
                title = issue.label,
                description = issue.advice,
                share = count / metrics.poseFrames.toFloat(),
            )
        }
    }

    Spacer(Modifier.height(20.dp))
    Text(
        "As medições ainda não viram nota. Os limiares de pontuação serão definidos " +
            "após a calibração com praticantes de forró.",
        style = MaterialTheme.typography.bodySmall,
        color = colors.onSurfaceDim,
    )
    Spacer(Modifier.height(24.dp))
}

@Composable
private fun SessionHeader(outcome: SessionOutcome) {
    val colors = DcaTheme.colors
    val session = outcome.session
    DcaCard(padding = 16) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            EmojiTile(session.emoji, 48)
            Column(Modifier.weight(1f)) {
                Text(
                    "${session.styleName} · ${session.durationMin} min",
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.onSurface,
                )
                Text(
                    session.date,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVar,
                )
            }
        }
        if (outcome.previous == null) {
            Spacer(Modifier.height(10.dp))
            Text(
                "Primeira sessão registrada — a partir da próxima, a comparação aparece aqui.",
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceDim,
            )
        }
    }
}

@Composable
private fun ModuleCard(
    icon: ImageVector,
    label: String,
    value: String,
    detail: String,
    deltaPp: Int?,
    modifier: Modifier = Modifier,
) {
    val colors = DcaTheme.colors
    DcaCard(padding = 14, modifier = modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(icon, contentDescription = null, tint = colors.onSurfaceVar, modifier = Modifier.size(18.dp))
            Text(
                label.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = colors.onSurfaceVar,
            )
        }
        Row(
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(top = 8.dp),
        ) {
            Text(
                value,
                fontFamily = MonoFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 30.sp,
                // A cor não julga o valor absoluto: sem fórmula de score não há
                // limiar defensável para chamar 70% de bom ou ruim.
                color = colors.onSurface,
            )
            deltaPp?.let { delta ->
                Text(
                    "${if (delta >= 0) "↑" else "↓"} ${abs(delta)} pp",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (delta >= 0) colors.good else colors.bad,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            }
        }
        Text(
            detail,
            style = MaterialTheme.typography.labelSmall,
            color = colors.onSurfaceDim,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

@Composable
private fun EmptyMetricsNotice() {
    val colors = DcaTheme.colors
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(Shapes.md)
            .background(colors.surface2)
            .border(1.dp, colors.outline, Shapes.md)
            .padding(14.dp),
    ) {
        Column {
            Text(
                "Nenhuma medição registrada",
                style = MaterialTheme.typography.titleMedium,
                color = colors.onSurface,
            )
            Text(
                "A câmera não reconheceu uma pose durante a sessão. Confira o enquadramento " +
                    "— o corpo inteiro precisa aparecer — ou use um aparelho físico, já que o " +
                    "emulador não roda a detecção de pose.",
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVar,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

@Composable
private fun ImprovementRow(title: String, description: String, share: Float) {
    val colors = DcaTheme.colors
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(44.dp).clip(Shapes.sm).background(colors.surface2),
        ) {
            Text(
                share.asPercent(),
                fontFamily = MonoFontFamily,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = colors.onSurface,
            )
        }
        Column {
            Text(title, style = MaterialTheme.typography.titleMedium, color = colors.onSurface)
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVar,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

private const val MAX_ISSUES_SHOWN = 3


private fun Float?.asPercent(): String =
    this?.let { "${(it * 100).roundToInt()}%" } ?: "—"

/**
 * Diferença entre duas proporções, em pontos percentuais. Comparar proporções em
 * variação relativa exageraria a mudança quando a base é pequena.
 */
private fun pointsDelta(current: Float?, previous: Float?): Int? =
    if (current == null || previous == null) null
    else ((current - previous) * 100).roundToInt().takeIf { it != 0 }

