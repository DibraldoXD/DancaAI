package com.dancaai.app.ui.components

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp

/**
 * Espaço até a borda inferior de pelo menos [minimum] — sem *somar* esse mínimo à
 * barra de navegação do sistema quando ela for maior (ex.: navegação por 3 botões).
 * O conteúdo encosta perto da barra em vez de flutuar num vão duplicado.
 */
@Composable
fun Modifier.navigationBarsOrMinPadding(minimum: Dp): Modifier {
    val navBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    return this.padding(bottom = maxOf(navBarBottom, minimum))
}
