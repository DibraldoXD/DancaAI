package com.dancaai.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dancaai.app.data.model.SessionOutcome
import com.dancaai.app.ui.components.DcaFilledButton
import com.dancaai.app.ui.components.DcaOutlinedButton
import com.dancaai.app.ui.components.DcaTopBar
import com.dancaai.app.ui.components.EmptyMetricsNotice
import com.dancaai.app.ui.components.ModuleMetricsGrid
import com.dancaai.app.ui.components.PostureIssueList
import com.dancaai.app.ui.components.ScorePendingNotice
import com.dancaai.app.ui.components.SectionLabel
import com.dancaai.app.ui.components.SessionSummaryCard
import com.dancaai.app.ui.components.navigationBarsOrMinPadding
import com.dancaai.app.ui.theme.DcaTheme

/**
 * Resultado da sessão recém-encerrada, com um cartão por módulo de análise
 * ([ModuleMetricsGrid], compartilhado com o detalhe do histórico).
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
    val metrics = outcome.session.metrics

    Spacer(Modifier.height(12.dp))
    SessionSummaryCard(
        session = outcome.session,
        footnote = if (outcome.previous == null) {
            "Primeira sessão registrada — a partir da próxima, a comparação aparece aqui."
        } else {
            null
        },
    )

    Spacer(Modifier.height(24.dp))
    SectionLabel("Medições por módulo")
    Spacer(Modifier.height(10.dp))
    ModuleMetricsGrid(metrics = metrics, previous = outcome.previous?.metrics)

    if (metrics.isEmpty) {
        Spacer(Modifier.height(16.dp))
        EmptyMetricsNotice()
    }

    if (metrics.topPostureIssues.isNotEmpty()) {
        Spacer(Modifier.height(24.dp))
        SectionLabel("Pontos a melhorar")
        Spacer(Modifier.height(4.dp))
        PostureIssueList(metrics)
    }

    Spacer(Modifier.height(20.dp))
    ScorePendingNotice()
    Spacer(Modifier.height(24.dp))
}
