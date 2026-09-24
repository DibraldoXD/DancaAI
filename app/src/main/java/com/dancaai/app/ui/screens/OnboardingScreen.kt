package com.dancaai.app.ui.screens

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccessibilityNew
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dancaai.app.data.DanceCatalog
import com.dancaai.app.data.local.UserSettings
import com.dancaai.app.ui.components.BrandMark
import com.dancaai.app.ui.components.DcaFilledButton
import com.dancaai.app.ui.components.DcaOutlinedButton
import com.dancaai.app.ui.components.DcaTextButton
import com.dancaai.app.ui.components.Dots
import com.dancaai.app.ui.components.SectionLabel
import com.dancaai.app.ui.components.navigationBarsOrMinPadding
import com.dancaai.app.ui.theme.DcaTheme
import com.dancaai.app.ui.theme.Shapes
import kotlinx.coroutines.launch

@Composable
fun OnboardingScreen(
    onFinish: (name: String, levelId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val pagerState = rememberPagerState(pageCount = { 3 })
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }
    var level by remember { mutableStateOf(UserSettings.DEFAULT_LEVEL_ID) }

    fun goTo(page: Int) = scope.launch { pagerState.animateScrollToPage(page) }

    Column(modifier = modifier.fillMaxSize().background(DcaTheme.colors.bg)) {
        HorizontalPager(state = pagerState, modifier = Modifier.weight(1f)) { page ->
            when (page) {
                0 -> OnboardSlide1()
                1 -> OnboardSlide2()
                else -> OnboardSlide3(name, { name = it }, level, { level = it })
            }
        }

        Column(
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .navigationBarsOrMinPadding(32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Dots(total = 3, index = pagerState.currentPage, modifier = Modifier.align(Alignment.CenterHorizontally))
            when (pagerState.currentPage) {
                0 -> {
                    DcaFilledButton("Continuar", onClick = { goTo(1) }, modifier = Modifier.fillMaxWidth())
                    DcaTextButton(
                        "Pular",
                        onClick = { onFinish("", UserSettings.DEFAULT_LEVEL_ID) },
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                    )
                }
                1 -> Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    DcaOutlinedButton("Voltar", onClick = { goTo(0) }, modifier = Modifier.weight(1f))
                    DcaFilledButton("Continuar", onClick = { goTo(2) }, modifier = Modifier.weight(2f), height = 48)
                }
                else -> Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    DcaOutlinedButton("Voltar", onClick = { goTo(1) }, modifier = Modifier.weight(1f))
                    DcaFilledButton(
                        "Começar",
                        onClick = { onFinish(name, level) },
                        modifier = Modifier.weight(2f),
                        height = 48,
                    )
                }
            }
        }
    }
}

@Composable
private fun OnboardSlide1() {
    val accent = DcaTheme.colors.accent
    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
        // glow radial atrás da marca
        Box(
            modifier = Modifier
                .size(320.dp)
                .blur(48.dp)
                .clip(androidx.compose.foundation.shape.CircleShape)
                .background(Brush.radialGradient(listOf(accent.copy(alpha = 0.45f), Color.Transparent))),
        )
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            BrandMark(size = 108)
            Spacer(Modifier.height(28.dp))
        Text(
            "Dança AI",
            style = MaterialTheme.typography.displayLarge,
            color = DcaTheme.colors.onSurface,
            textAlign = TextAlign.Center,
        )
        Text(
            "Aprenda a dançar com inteligência. Seu professor pessoal de dança de salão.",
            style = MaterialTheme.typography.bodyLarge,
            color = DcaTheme.colors.onSurfaceVar,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 12.dp).width(280.dp),
        )
        }
    }
}

@Composable
private fun OnboardSlide2() {
    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
        Spacer(Modifier.height(40.dp))
        Text("Como funciona", style = MaterialTheme.typography.headlineMedium, color = DcaTheme.colors.onSurface)
        Text(
            "Três etapas. Sem nuvem — tudo no seu aparelho.",
            style = MaterialTheme.typography.bodyLarge,
            color = DcaTheme.colors.onSurfaceVar,
            modifier = Modifier.padding(top = 8.dp),
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center,
        ) {
            OnboardStep(Icons.Rounded.Videocam, "1. Posicione a câmera", "Apoie o celular num suporte a ~2 metros, mostrando seu corpo todo.")
            StepConnector()
            OnboardStep(Icons.Rounded.AccessibilityNew, "2. Comece a dançar", "A IA rastreia 33 pontos do seu corpo, em tempo real.")
            StepConnector()
            OnboardStep(Icons.Rounded.AutoAwesome, "3. Receba feedback", "Postura e ritmo medidos em tempo real, sem distrair.")
        }
    }
}

@Composable
private fun OnboardStep(icon: ImageVector, title: String, sub: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(52.dp).clip(Shapes.md).background(DcaTheme.colors.accentSoft),
        ) {
            Icon(icon, contentDescription = null, tint = DcaTheme.colors.accent, modifier = Modifier.size(28.dp))
        }
        Column {
            Text(title, style = MaterialTheme.typography.titleMedium, color = DcaTheme.colors.onSurface)
            Text(sub, style = MaterialTheme.typography.bodyMedium, color = DcaTheme.colors.onSurfaceVar)
        }
    }
}

@Composable
private fun StepConnector() {
    Box(
        modifier = Modifier
            .padding(start = 26.dp, top = 8.dp, bottom = 8.dp)
            .width(2.dp)
            .height(16.dp)
            .background(DcaTheme.colors.outline),
    )
}

@Composable
private fun OnboardSlide3(
    name: String,
    onName: (String) -> Unit,
    level: String,
    onLevel: (String) -> Unit,
) {
    val colors = DcaTheme.colors
    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
        Spacer(Modifier.height(40.dp))
        Text("Vamos te conhecer", style = MaterialTheme.typography.headlineMedium, color = colors.onSurface)
        Text(
            "Personalizamos o feedback ao seu nível.",
            style = MaterialTheme.typography.bodyLarge,
            color = colors.onSurfaceVar,
            modifier = Modifier.padding(top = 8.dp),
        )

        Spacer(Modifier.height(32.dp))
        SectionLabel("Como podemos te chamar?")
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = name,
            onValueChange = onName,
            singleLine = true,
            placeholder = { Text("Seu nome", color = colors.onSurfaceDim) },
            shape = Shapes.md,
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = colors.onSurface,
                unfocusedTextColor = colors.onSurface,
                cursorColor = colors.accent,
                focusedBorderColor = colors.accent,
                unfocusedBorderColor = colors.outline,
            ),
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(28.dp))
        SectionLabel("Seu nível")
        Spacer(Modifier.height(12.dp))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            DanceCatalog.levels.forEach { l ->
                LevelRow(l.label, l.description, selected = level == l.id, onClick = { onLevel(l.id) })
            }
        }

        Spacer(Modifier.height(28.dp))
        Text(
            "O treino é focado no forró universitário — é o estilo que os módulos de " +
                "análise avaliam.",
            style = MaterialTheme.typography.bodySmall,
            color = colors.onSurfaceDim,
        )
    }
}

@Composable
private fun LevelRow(label: String, sub: String, selected: Boolean, onClick: () -> Unit) {
    val colors = DcaTheme.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(Shapes.md)
            .background(if (selected) colors.accentSoft else androidx.compose.ui.graphics.Color.Transparent)
            .border(1.5.dp, if (selected) colors.accent else colors.outline, Shapes.md)
            .clickable(onClick = onClick)
            .padding(16.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(22.dp)
                .clip(CircleShape)
                .border(2.dp, if (selected) colors.accent else colors.onSurfaceDim, CircleShape),
        ) {
            if (selected) {
                Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(colors.accent))
            }
        }
        Column {
            Text(label, style = MaterialTheme.typography.titleMedium, color = colors.onSurface)
            Text(sub, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVar)
        }
    }
}
