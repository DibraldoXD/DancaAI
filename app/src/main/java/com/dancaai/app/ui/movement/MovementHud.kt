package com.dancaai.app.ui.movement

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dancaai.app.analysis.movement.DanceStep
import com.dancaai.app.ui.components.DcaChip
import com.dancaai.app.ui.theme.DcaTheme

/**
 * Painel de execução de movimentos sobre a câmera.
 *
 * Fica deliberadamente curto em texto. O usuário está dançando e olhando a tela
 * de relance: o que precisa ser legível em um relance é o nome do passo e se
 * está certo. Explicação longa aqui não é lida.
 */
@Composable
fun MovementHud(
    state: MovementUiState,
    onModeChange: (MovementMode) -> Unit,
    onRestart: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DcaChip(
                label = "Livre",
                active = state.mode == MovementMode.LIVRE,
                onClick = { onModeChange(MovementMode.LIVRE) },
            )
            DcaChip(
                label = "Desafio",
                active = state.mode == MovementMode.DESAFIO,
                onClick = { onModeChange(MovementMode.DESAFIO) },
            )
        }

        if (!state.modelAvailable) {
            // if/else em vez de retorno antecipado: `return@Column` depende de
            // Column ser inline, o que é detalhe de implementação do Compose.
            // O .tflite não vai para o repositório público enquanto a publicação
            // não estiver amparada pelo consentimento e pelo CEP. Dizer isso é
            // melhor que uma tela que nunca sai de "aguardando".
            HudBox {
                Text(
                    "Modelo de movimentos não instalado",
                    color = DcaTheme.colors.warn,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    "Copie movement_classifier.tflite para app/src/main/assets/ " +
                        "e recompile. Os outros módulos seguem funcionando.",
                    color = DcaTheme.colors.onSurfaceVar,
                    fontSize = 11.sp,
                )
            }
        } else when (state.mode) {
            MovementMode.LIVRE -> LivreBox(state)
            MovementMode.DESAFIO -> DesafioBox(state, onRestart)
        }
    }
}

@Composable
private fun LivreBox(state: MovementUiState) {
    HudBox {
        val step = state.detectedStep
        if (step == null) {
            Text(
                if (state.windowsSeen == 0) "Dance para o app reconhecer o passo"
                else "Analisando…",
                color = DcaTheme.colors.onSurfaceVar,
                fontSize = 13.sp,
            )
        } else {
            Text(
                step.display,
                color = DcaTheme.colors.onSurfaceStrong,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
            )
            // Concordância e confiança são grandezas diferentes e as duas
            // importam: o modelo pode estar muito confiante em janelas que
            // discordam entre si. Mostrar as duas evita a leitura de que um
            // número alto significa certeza.
            Text(
                "confiança ${(state.confidence * 100).toInt()}% · " +
                    "${(state.agreement * 100).toInt()}% das janelas",
                color = DcaTheme.colors.onSurfaceVar,
                fontSize = 11.sp,
            )
        }
    }
}

@Composable
private fun DesafioBox(state: MovementUiState, onRestart: () -> Unit) {
    HudBox {
        if (state.challengeComplete) {
            Text(
                "Sequência concluída",
                color = DcaTheme.colors.good,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                "${state.hits} acerto(s), ${state.misses} erro(s)",
                color = DcaTheme.colors.onSurfaceVar,
                fontSize = 12.sp,
            )
            Spacer(Modifier.size(4.dp))
            DcaChip(label = "Repetir", active = false, onClick = onRestart)
        } else {
        Text(
            "Faça agora",
            color = DcaTheme.colors.onSurfaceVar,
            fontSize = 11.sp,
        )
        Text(
            state.expectedStep?.display ?: "—",
            color = DcaTheme.colors.onSurfaceStrong,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
        )

        SequenceDots(state)

        val outcomeColor = when (state.lastOutcome) {
            MovementUiState.Outcome.HIT -> DcaTheme.colors.good
            MovementUiState.Outcome.MISS -> DcaTheme.colors.bad
            null -> DcaTheme.colors.onSurfaceVar
        }
        Text(
            when (state.lastOutcome) {
                MovementUiState.Outcome.HIT -> "Certo"
                MovementUiState.Outcome.MISS ->
                    "Saiu ${state.detectedStep?.display ?: "outro passo"}"
                null -> "${state.hits} acerto(s) · ${state.misses} erro(s)"
            },
            color = outcomeColor,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
        )
        }
    }
}

/** Um ponto por passo da sequência: cumprido, atual, ou ainda por vir. */
@Composable
private fun SequenceDots(state: MovementUiState) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        state.sequence.forEachIndexed { index, _ ->
            val color = when {
                index < state.sequencePosition -> DcaTheme.colors.good
                index == state.sequencePosition -> DcaTheme.colors.accent
                else -> DcaTheme.colors.outline
            }
            Spacer(
                Modifier
                    .size(if (index == state.sequencePosition) 10.dp else 7.dp)
                    .clip(RoundedCornerShape(50))
                    .background(color),
            )
        }
    }
}

@Composable
private fun HudBox(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xCC161618))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
        content = content,
    )
}
