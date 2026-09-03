package com.platos.android.session

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * O requisito "tres estados distintos" na parte dele que a JVM alcanca.
 *
 * Nao se afirma aqui que a faixa aparece — isso e aparelho. Afirma-se o que a faixa **diz**, que e
 * onde o requisito pode ser violado sem ninguem notar: tres motivos diferentes apresentados com a
 * mesma frase passam em qualquer teste de que "a faixa apareceu".
 */
class MensagemDeEntradaTest {

    @Test
    fun `todo motivo tem mensagem, e nenhuma e vazia`() {
        MotivoDeEntrada.entries.forEach { motivo ->
            val mensagem = mensagemDeEntrada(motivo)
            assertNotNull(mensagem, "o motivo $motivo nao tem mensagem")
            assertTrue(mensagem!!.isNotBlank(), "a mensagem de $motivo e so espaco em branco")
        }
    }

    @Test
    fun `nenhum motivo e apresentado como outro`() {
        val mensagens = MotivoDeEntrada.entries.map { mensagemDeEntrada(it) }

        // Distintas duas a duas. E o que pega o defeito real desta camada: copiar a frase de um
        // motivo para outro nao quebra nada, nao aparece em revisao de tela, e faz o professor ler
        // "confira a conexao" com a senha errada.
        assertEquals(
            MotivoDeEntrada.entries.size,
            mensagens.toSet().size,
            "dois motivos compartilham a mesma frase: $mensagens",
        )
    }

    @Test
    fun `primeira abertura nao tem motivo, e nao tem faixa`() {
        // `null`, e nao string vazia: vazia desenharia uma faixa em branco, que le como defeito.
        assertNull(mensagemDeEntrada(null))
    }
}
