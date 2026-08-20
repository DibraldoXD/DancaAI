package com.dancaai.app.data

import com.dancaai.app.data.model.DanceLevel
import com.dancaai.app.data.model.DanceStyle

/**
 * Catálogo estático de estilos e níveis.
 *
 * O escopo do trabalho é o forró universitário — é o único estilo com módulos de
 * análise (peso, postura, ritmo e passos) implementados, e por isso o único
 * oferecido no app. Os demais estilos do protótipo de design foram removidos
 * para que a interface não prometa uma análise que não existe.
 */
object DanceCatalog {

    const val FORRO_ID = "forro"

    val forro = DanceStyle(FORRO_ID, "Forró", "💃", "112–148")

    val styles = listOf(forro)

    val levels = listOf(
        DanceLevel("iniciante", "Iniciante", "Estou começando agora"),
        DanceLevel("intermediario", "Intermediário", "Já danço há algum tempo"),
    )

    fun styleById(id: String): DanceStyle = styles.firstOrNull { it.id == id } ?: forro

    fun levelLabel(id: String): String = levels.firstOrNull { it.id == id }?.label ?: ""
}
