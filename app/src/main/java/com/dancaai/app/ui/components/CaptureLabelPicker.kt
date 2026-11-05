package com.dancaai.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dancaai.app.data.model.CaptureGroup
import com.dancaai.app.data.model.CaptureLabel
import com.dancaai.app.data.model.CaptureSubject
import com.dancaai.app.data.model.CaptureView
import com.dancaai.app.data.model.ParticipantId
import com.dancaai.app.ui.theme.DcaTheme
import com.dancaai.app.ui.theme.MonoFontFamily
import com.dancaai.app.ui.theme.Shapes

/** Placeholder do código quando nenhum participante está armado. */
private const val SEM_PARTICIPANTE = "p--"

/**
 * Pílula do HUD com o rótulo armado no momento. Fica logo acima dos botões de
 * captura porque gravar com o rótulo errado só aparece depois, na planilha —
 * o valor tem que estar visível sem precisar abrir nada.
 */
@Composable
fun CaptureLabelPill(label: CaptureLabel, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier
            .clip(Shapes.pill)
            .background(Color.Black.copy(alpha = 0.55f))
            .border(1.dp, DcaTheme.colors.accent.copy(alpha = 0.55f), Shapes.pill)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Text(
            "GRAVANDO",
            fontFamily = MonoFontFamily,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            color = DcaTheme.colors.accent,
        )
        Text(
            label.display,
            style = MaterialTheme.typography.labelLarge,
            color = Color.White,
            maxLines = 1,
        )
        // O participante fica na pílula, e não só dentro do seletor, porque
        // esquecer de trocar entre duas pessoas não dá nenhum sinal na hora — só
        // aparece no treino, como dois participantes fundidos num grupo só.
        Text(
            label.participant?.token ?: SEM_PARTICIPANTE,
            fontFamily = MonoFontFamily,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = if (label.participant == null) DcaTheme.colors.warn else Color.White,
        )
        Text("▾", fontSize = 11.sp, color = Color.White.copy(alpha = 0.6f))
    }
}

/**
 * Seletor do que está sendo gravado: assunto (postura ou movimento) e ângulo da
 * câmera. Sai como overlay de tela cheia, e não como linha de chips no HUD,
 * porque são dez assuntos — em chips roláveis sobre a câmera o alvo de toque
 * fica pequeno demais para quem está de pé, longe do celular, no meio da coleta.
 */
@Composable
fun CaptureLabelPicker(
    current: CaptureLabel,
    onChange: (CaptureLabel) -> Unit,
    onParticipantNext: () -> Unit,
    onParticipantPrevious: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = DcaTheme.colors

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xF2101014))
            // Um fundo opaco não consome toque no Compose: sem isto o tap atravessa
            // o overlay e aciona o que está atrás (o "Registrar" dispara uma captura
            // com o rótulo errado, e o erro só aparece depois, na planilha).
            .pointerInput(Unit) { detectTapGestures { } }
            .padding(horizontal = 16.dp),
    ) {
        Spacer(Modifier.height(40.dp))
        Text(
            "O que está sendo gravado",
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
            color = Color.White,
        )
        Text(
            "Vai para a coluna Rotulo da planilha: ${current.token()}",
            fontFamily = MonoFontFamily,
            fontSize = 11.sp,
            color = colors.accent,
            modifier = Modifier.padding(top = 4.dp, bottom = 14.dp),
        )

        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            // Primeiro da lista: é o campo que se troca a cada pessoa, enquanto
            // assunto e ângulo costumam ficar os mesmos por vários minutos.
            ParticipantRow(
                participant = current.participant,
                onNext = onParticipantNext,
                onPrevious = onParticipantPrevious,
            )

            CaptureGroup.entries.forEach { group ->
                Text(
                    group.display.uppercase(),
                    fontFamily = MonoFontFamily,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.onSurfaceDim,
                    modifier = Modifier.padding(top = 10.dp, bottom = 2.dp),
                )
                CaptureSubject.entries.filter { it.group == group }.forEach { subject ->
                    SubjectRow(
                        subject = subject,
                        active = subject == current.subject,
                        onClick = { onChange(current.copy(subject = subject)) },
                    )
                }
            }

            Text(
                "ÂNGULO DA CÂMERA",
                fontFamily = MonoFontFamily,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = colors.onSurfaceDim,
                modifier = Modifier.padding(top = 16.dp, bottom = 2.dp),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                CaptureView.entries.forEach { view ->
                    DcaChip(
                        label = view.display,
                        active = view == current.view,
                        onClick = { onChange(current.copy(view = view)) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        DcaFilledButton("Pronto", onClick = onDismiss, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.navigationBarsOrMinPadding(24.dp))
    }
}

/**
 * Participante corrente: código opaco e sequencial, trocado com − / +.
 *
 * Não é campo de texto livre porque o que se precisa aqui é só separar uma
 * pessoa da outra — nome ou inicial no rótulo iria para uma planilha de dados
 * corporais num repositório público. O botão "Próximo" é o caminho normal; o
 * "−" existe para corrigir um avanço acidental.
 */
@Composable
private fun ParticipantRow(
    participant: ParticipantId?,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
) {
    val colors = DcaTheme.colors
    val armado = participant != null

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(Shapes.md)
            .background(if (armado) colors.surface2 else colors.warnDim)
            .border(1.dp, if (armado) colors.outline else colors.warn, Shapes.md)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "PARTICIPANTE",
                fontFamily = MonoFontFamily,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = if (armado) colors.onSurfaceDim else colors.warn,
                modifier = Modifier.weight(1f),
            )
            Text(
                participant?.token ?: SEM_PARTICIPANTE,
                fontFamily = MonoFontFamily,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = if (armado) colors.accent else colors.warn,
            )
        }
        Text(
            if (armado) {
                "Troque a cada pessoa. É este código que separa os participantes " +
                    "na validação do modelo."
            } else {
                "Sem participante as capturas saem sem identificador, e o " +
                    "agrupamento por pessoa volta a ser adivinhado pelo horário."
            },
            style = MaterialTheme.typography.bodySmall,
            color = if (armado) colors.onSurfaceVar else colors.warn,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            DcaChip(
                label = "−",
                active = false,
                onClick = onPrevious,
                modifier = Modifier.weight(1f),
            )
            DcaChip(
                label = if (armado) "Próxima pessoa +" else "Começar em p01",
                active = !armado,
                onClick = onNext,
                modifier = Modifier.weight(3f),
            )
        }
    }
}

@Composable
private fun SubjectRow(subject: CaptureSubject, active: Boolean, onClick: () -> Unit) {
    val colors = DcaTheme.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(Shapes.md)
            .background(if (active) colors.accentSoft else colors.surface2)
            .border(1.dp, if (active) colors.accent else colors.outline, Shapes.md)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Text(
            subject.display,
            style = MaterialTheme.typography.bodyMedium,
            color = if (active) colors.accent else colors.onSurface,
            modifier = Modifier.weight(1f),
        )
        Text(
            subject.token,
            fontFamily = MonoFontFamily,
            fontSize = 10.sp,
            color = if (active) colors.accent else colors.onSurfaceDim,
        )
    }
}
