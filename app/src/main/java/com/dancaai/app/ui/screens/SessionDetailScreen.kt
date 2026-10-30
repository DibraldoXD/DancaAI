package com.dancaai.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dancaai.app.data.model.SessionOutcome
import com.dancaai.app.ui.components.DcaTopBar
import com.dancaai.app.ui.components.EmptyMetricsNotice
import com.dancaai.app.ui.components.ModuleMetricsGrid
import com.dancaai.app.ui.components.PostureIssueList
import com.dancaai.app.ui.components.ScorePendingNotice
import com.dancaai.app.ui.components.SectionLabel
import com.dancaai.app.ui.components.SessionSummaryCard
import com.dancaai.app.ui.history.SessionDetailViewModel
import com.dancaai.app.ui.theme.DcaTheme

/**
 * Uma sessão do histórico aberta por inteiro. Mostra as mesmas medições da tela
 * de Resultado ([ModuleMetricsGrid]) — o histórico listava só estilo, data e
 * duração, e as medições gravadas ficavam inacessíveis depois do treino.
 */
@Composable
fun SessionDetailScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SessionDetailViewModel = viewModel(),
) {
    val colors = DcaTheme.colors
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(modifier = modifier.fillMaxSize().background(colors.bg)) {
        DcaTopBar(
            title = "Sessão",
            navigationIcon = Icons.AutoMirrored.Rounded.ArrowBack,
            onNavigationClick = onBack,
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
        ) {
            val outcome = state.outcome
            when {
                state.loading -> Notice("Carregando a sessão…")
                outcome == null -> Notice("Esta sessão não está mais no histórico.")
                else -> OutcomeDetail(outcome)
            }
        }
    }
}

@Composable
private fun OutcomeDetail(outcome: SessionOutcome) {
    val metrics = outcome.session.metrics

    Spacer(Modifier.height(12.dp))
    SessionSummaryCard(
        session = outcome.session,
        footnote = if (outcome.previous == null) {
            "Primeira sessão do histórico — não há anterior para comparar."
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

@Composable
private fun Notice(message: String) {
    Text(
        message,
        style = MaterialTheme.typography.bodyMedium,
        color = DcaTheme.colors.onSurfaceDim,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
    )
}
