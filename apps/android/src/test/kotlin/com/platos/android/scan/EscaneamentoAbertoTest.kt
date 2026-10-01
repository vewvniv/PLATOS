package com.platos.android.scan

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** A marca de processo que poupa os orfaos na varredura em segundo plano enquanto o escaneamento esta aberto. */
class EscaneamentoAbertoTest {

    @AfterEach
    fun fecha() = EscaneamentoAberto.fechar()

    @Test
    fun `fechado por padrao, aberto depois de abrir, fechado depois de fechar`() {
        assertEquals(false, EscaneamentoAberto.estaAberto())
        EscaneamentoAberto.abrir()
        assertEquals(true, EscaneamentoAberto.estaAberto())
        EscaneamentoAberto.fechar()
        assertEquals(false, EscaneamentoAberto.estaAberto())
    }
}
