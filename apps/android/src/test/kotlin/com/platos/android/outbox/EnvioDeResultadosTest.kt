package com.platos.android.outbox

import com.platos.android.net.Retorno
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * O envio da fila, sem rede, sem `WorkManager` e sem aparelho.
 *
 * O que se afirma aqui e a regra que a classe H fixa — **o pendente so sai depois da confirmacao** —
 * e ela e afirmada **conferindo a guarda depois da chamada**, e nunca o retorno da funcao que apaga.
 * Um `apagarConfirmado` que nao apagasse nada devolveria exatamente o mesmo `Unit`.
 */
class EnvioDeResultadosTest {

    /**
     * A guarda em memoria.
     *
     * **Nao e um mock com verificacao de chamada.** Verificar "apagarConfirmado foi chamado" e
     * afirmacao sobre a interacao, e o requisito e sobre o **estado**: o que importa e se a linha
     * ainda esta la. Um dublê que registrasse chamadas passaria por uma implementacao que chama e
     * nao apaga.
     */
    private class GuardaEmMemoria(iniciais: List<EnvelopeDeEnvio>) : ResultadosPendentes {
        val linhas = iniciais.toMutableList()

        override fun guardar(resultado: ResultadoPendente) = error("nao usado neste teste")
        override fun pendentesDa(organizacao: String) = linhas.filter { it.organizacao == organizacao }
        override fun quantosPendentes(organizacao: String) = pendentesDa(organizacao).size
        override fun apagarConfirmado(captureId: String) {
            linhas.removeAll { it.captureId == captureId }
        }
    }

    private fun envelope(id: String, organizacao: String = ORG) =
        EnvelopeDeEnvio(captureId = id, organizacao = organizacao, prova = "prova-r", corpo = "{}")

    @Test
    fun `confirmado sai da fila`() = runBlocking {
        val guarda = GuardaEmMemoria(listOf(envelope("cap-1")))
        val envio = EnvioDeResultados(guarda) { Retorno.Respondeu(Unit) }

        val resumo = envio.enviarPendentesDa(ORG)

        assertEquals(1, resumo.confirmados)
        assertEquals(0, resumo.pendentesRestantes)
        assertTrue(guarda.linhas.isEmpty(), "o pendente confirmado continua no aparelho")
    }

    @Test
    fun `sem rede o pendente fica intacto`() = runBlocking {
        val guarda = GuardaEmMemoria(listOf(envelope("cap-1")))
        val envio = EnvioDeResultados(guarda) { Retorno.SemRede }

        val resumo = envio.enviarPendentesDa(ORG)

        assertEquals(0, resumo.confirmados)
        assertEquals(1, resumo.semRede)
        assertEquals(listOf("cap-1"), guarda.linhas.map { it.captureId }, "a linha foi mexida")
    }

    @Test
    fun `recusa do servidor nao apaga o pendente`() = runBlocking {
        val guarda = GuardaEmMemoria(listOf(envelope("cap-1")))
        val envio = EnvioDeResultados(guarda) { Retorno.Recusou(500) }

        val resumo = envio.enviarPendentesDa(ORG)

        assertEquals(1, resumo.recusados)
        assertEquals(listOf("cap-1"), guarda.linhas.map { it.captureId })
    }

    /**
     * 404 e o caso do vinculo revogado, e ele **nao** apaga.
     *
     * A rota nao distingue prova inexistente de prova de organizacao alheia, de proposito. Para o
     * aparelho as duas dizem "nao gravei", e apagar por causa disso destruiria uma correcao que
     * outro membro da organizacao ainda consegue enviar do mesmo aparelho.
     */
    @Test
    fun `recusa por vinculo revogado nao apaga o pendente`() = runBlocking {
        val guarda = GuardaEmMemoria(listOf(envelope("cap-revogado")))
        var statusVisto = 0
        val envio = EnvioDeResultados(guarda) { statusVisto = 404; Retorno.Recusou(404) }

        val resumo = envio.enviarPendentesDa(ORG)

        assertEquals(404, statusVisto, "o teste precisa ter exercitado o 404, e nao outro status")
        assertEquals(1, resumo.recusados)
        assertEquals(listOf("cap-revogado"), guarda.linhas.map { it.captureId })
    }

    /**
     * Um recusado nao prende quem vem atras.
     *
     * O conjunto que cai importa: se parasse no primeiro erro, `cap-2` ficaria na fila **e** o
     * resumo diria um confirmado a menos. As duas assercoes abaixo separam "nao enviou" de
     * "enviou e nao apagou".
     */
    @Test
    fun `um recusado nao bloqueia o resto da fila`() = runBlocking {
        val guarda = GuardaEmMemoria(listOf(envelope("cap-1"), envelope("cap-2")))
        val envio = EnvioDeResultados(guarda) { envelope ->
            if (envelope.captureId == "cap-1") Retorno.Recusou(400) else Retorno.Respondeu(Unit)
        }

        val resumo = envio.enviarPendentesDa(ORG)

        assertEquals(1, resumo.confirmados)
        assertEquals(1, resumo.recusados)
        assertEquals(
            listOf("cap-1"),
            guarda.linhas.map { it.captureId },
            "o recusado fica, e so ele",
        )
    }

    @Test
    fun `a fila de uma organizacao nao leva a de outra`() = runBlocking {
        val guarda = GuardaEmMemoria(
            listOf(envelope("cap-daqui"), envelope("cap-de-outra", organizacao = "outra-org")),
        )
        val enviados = mutableListOf<String>()
        val envio = EnvioDeResultados(guarda) { enviados += it.captureId; Retorno.Respondeu(Unit) }

        envio.enviarPendentesDa(ORG)

        assertEquals(listOf("cap-daqui"), enviados, "so os pendentes da organizacao pedida sobem")
        assertEquals(
            listOf("cap-de-outra"),
            guarda.linhas.map { it.captureId },
            "o pendente da outra organizacao continua no aparelho",
        )
    }

    @Test
    fun `fila vazia nao inventa confirmacao`() = runBlocking {
        val guarda = GuardaEmMemoria(emptyList())
        var tentativas = 0
        val envio = EnvioDeResultados(guarda) { tentativas++; Retorno.Respondeu(Unit) }

        val resumo = envio.enviarPendentesDa(ORG)

        assertEquals(0, tentativas, "fila vazia nao faz chamada")
        assertEquals(0, resumo.confirmados)
    }

    private companion object {
        const val ORG = "org-1"
    }
}
