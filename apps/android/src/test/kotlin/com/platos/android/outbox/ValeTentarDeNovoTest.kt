package com.platos.android.outbox

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * A decisao de reagendar, exercitada fora do `WorkManager`.
 *
 * Os quatro casos sao as combinacoes que mudam a resposta. O par que importa e o ultimo contra o
 * penultimo: **transitorio e definitivo sao o mesmo numero de pendentes restantes**, e respostas
 * opostas. Um teste que so olhasse `pendentesRestantes` aprovaria as duas.
 */
class ValeTentarDeNovoTest {

    @Test
    fun `nada pendente nao pede tentativa`() {
        assertFalse(valeTentarDeNovo(resumo(confirmados = 2)))
    }

    @Test
    fun `sem rede pede tentativa`() {
        assertTrue(valeTentarDeNovo(resumo(semRede = 1)))
    }

    @Test
    fun `falha do servidor pede tentativa`() {
        assertTrue(
            valeTentarDeNovo(resumo(transitorios = 1)),
            "um 500 transitorio ficaria parado ate alguem escanear outra folha",
        )
    }

    @Test
    fun `recusa definitiva nao pede tentativa`() {
        assertFalse(
            valeTentarDeNovo(resumo(recusados = 1)),
            "repetir contra um servidor que ja disse nao e laco quente",
        )
    }

    /**
     * O canario (P13): se `pendentesRestantes` deixasse de contar o transitorio, o caso do 503 acima
     * passaria a medir uma fila vazia, e `assertTrue` continuaria verde pelo motivo errado.
     */
    @Test
    fun `transitorio e definitivo deixam o mesmo tanto de pendente`() {
        assertTrue(resumo(transitorios = 1).pendentesRestantes == 1)
        assertTrue(resumo(recusados = 1).pendentesRestantes == 1)
    }

    private fun resumo(
        confirmados: Int = 0,
        semRede: Int = 0,
        transitorios: Int = 0,
        recusados: Int = 0,
    ) = ResumoDoEnvio(
        confirmados = confirmados,
        semRede = semRede,
        transitorios = transitorios,
        recusados = recusados,
    )
}
