package com.platos.android.scan

import com.platos.domain.scoring.PontuacaoDada
import com.platos.domain.scoring.Pontos
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** O que o professor digita vira pontuacao, ou um motivo que nomeia a questao (`slice-5c-3`, spec `scan-session`). */
class EntradaDaNotaTest {

    private val linhas = listOf(
        LinhaDaNota("d1", "3", worth = 3, resposta = null),
        LinhaDaNota("d2", "4", worth = 4, resposta = null),
    )

    private fun invalida(e: EntradaDaNota) = (e as? EntradaDaNota.Invalida ?: throw AssertionError("veio $e")).motivo

    @Test
    fun `textos validos viram pontuacoes e o total exato`() {
        val e = interpretarEntrada(linhas, mapOf("d1" to "2.5", "d2" to "3.75"), objetivos = 3) as EntradaDaNota.Valida

        assertEquals(
            listOf(PontuacaoDada("d1", Pontos.parse("2.5")), PontuacaoDada("d2", Pontos.parse("3.75"))),
            e.pontuacoes,
        )
        assertEquals("9.25", e.total.toString())
    }

    @Test
    fun `virgula decimal e recusada dizendo ponto decimal, nunca lida como 15`() {
        val motivo = invalida(interpretarEntrada(linhas, mapOf("d1" to "1,5", "d2" to "1"), 3))

        assertTrue(motivo.contains("questao 3"), motivo)
        assertTrue(motivo.contains("ponto decimal"), motivo)
    }

    @Test
    fun `campo em branco diz qual questao falta`() {
        val motivo = invalida(interpretarEntrada(linhas, mapOf("d1" to "2", "d2" to "  "), 3))

        assertTrue(motivo.contains("questao 4"), motivo)
    }

    @Test
    fun `tres casas, negativo e texto nao numerico sao recusados nomeando a questao`() {
        for (texto in listOf("1.333", "-1", "abc")) {
            val motivo = invalida(interpretarEntrada(linhas, mapOf("d1" to texto, "d2" to "1"), 3))
            assertTrue(motivo.contains("questao 3"), "$texto: $motivo")
        }
    }

    @Test
    fun `acima do valor da questao e recusado com o maximo`() {
        val motivo = invalida(interpretarEntrada(linhas, mapOf("d1" to "3.01", "d2" to "1"), 3))

        assertTrue(motivo.contains("questao 3") && motivo.contains("3"), motivo)
    }

    @Test
    fun `zero em todas e maximo em todas sao aceitos`() {
        val zero = interpretarEntrada(linhas, mapOf("d1" to "0", "d2" to "0.00"), 3) as EntradaDaNota.Valida
        val maximo = interpretarEntrada(linhas, mapOf("d1" to "3", "d2" to "4.00"), 3) as EntradaDaNota.Valida

        assertEquals("3.00", zero.total.toString())
        assertEquals("10.00", maximo.total.toString())
    }
}
