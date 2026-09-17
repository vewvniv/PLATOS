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

    /**
     * A frase de saida diz quantas correcoes ficaram, e diz que elas **continuam no aparelho**.
     *
     * As duas metades importam. O numero, porque quem deixa o aparelho precisa saber que a turma nao
     * subiu; e "continuam guardadas", porque sair **preserva** o pendente — uma frase que sugerisse
     * perda faria o professor procurar a correcao onde ela nao esta, ou refazer trabalho que existe.
     */
    @Test
    fun sair_com_pendentes_diz_quantos_sao_e_que_eles_ficaram() {
        val frase = mensagemDeEntrada(MotivoDeEntrada.SAIU, pendentes = 3)!!

        assertTrue(frase.contains("3"), "a frase precisa dizer quantas sao: $frase")
        assertTrue(
            frase.contains("guardadas neste aparelho"),
            "a frase precisa dizer que o trabalho ficou, e nao que sumiu: $frase",
        )
    }

    @Test
    fun sair_com_um_pendente_fala_no_singular() {
        val frase = mensagemDeEntrada(MotivoDeEntrada.SAIU, pendentes = 1)!!

        assertTrue(frase.contains("1 correcao ainda nao foi enviada"), frase)
        assertTrue(frase.contains("guardada neste aparelho"), frase)
    }

    @Test
    fun sair_sem_pendente_nao_menciona_envio() {
        val frase = mensagemDeEntrada(MotivoDeEntrada.SAIU, pendentes = 0)!!

        assertEquals("Voce saiu. Entre para continuar.", frase)
    }

    /** Os outros tres motivos nao mudam de frase por causa do numero: eles nao vem de uma sessao. */
    @Test
    fun o_numero_de_pendentes_nao_contamina_os_outros_motivos() {
        assertEquals(
            mensagemDeEntrada(MotivoDeEntrada.SEM_REDE, pendentes = 0),
            mensagemDeEntrada(MotivoDeEntrada.SEM_REDE, pendentes = 7),
        )
        assertEquals(
            mensagemDeEntrada(MotivoDeEntrada.SESSAO_EXPIRADA, pendentes = 0),
            mensagemDeEntrada(MotivoDeEntrada.SESSAO_EXPIRADA, pendentes = 7),
        )
    }
}
