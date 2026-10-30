package com.dancaai.app.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Formatação dos números que o Perfil mostra. São os valores que substituíram os
 * dados fixos do protótipo ("22 sessões, 86 MB"), então vale garantir que não
 * voltem a mentir por arredondamento.
 */
class ProfileFormattingTest {

    @Test
    fun `tamanho em bytes aparece cru`() {
        assertEquals("0 B", 0L.asStorageSize())
        assertEquals("512 B", 512L.asStorageSize())
    }

    @Test
    fun `kilobytes sao arredondados para inteiro`() {
        assertEquals("1 KB", 1_024L.asStorageSize())
        assertEquals("12 KB", (1_024L * 12).asStorageSize())
    }

    @Test
    fun `megabytes mantem uma casa decimal`() {
        // separador decimal segue o locale do aparelho, como o resto da interface
        val oneAndHalfMb = (1_048_576L * 3) / 2
        assertEquals("%.1f MB".format(1.5f), oneAndHalfMb.asStorageSize())
    }

    @Test
    fun `fronteira de um megabyte nao vira zero virgula zero`() {
        assertEquals("1024 KB", (1_048_576L - 1).asStorageSize())
        assertEquals("%.1f MB".format(1.0f), 1_048_576L.asStorageSize())
    }

    @Test
    fun `plural de sessao acompanha a contagem`() {
        assertEquals("sessões", sessionWord(0))
        assertEquals("sessão", sessionWord(1))
        assertEquals("sessões", sessionWord(2))
    }
}
