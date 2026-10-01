package com.platos.android.scan

import com.platos.android.net.Retorno
import com.platos.android.outbox.EnvelopeDeEnvio
import com.platos.android.outbox.EnvioDeResultados
import com.platos.android.outbox.NotaPendente
import com.platos.android.outbox.ResultadoPendente
import com.platos.android.outbox.ResultadosPendentes
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Cenario "Enviar a parcial nao elimina a resposta" (`slice-5c-1-a-resposta-fica-no-aparelho`, tarefa 4.2).
 *
 * O envio real ([EnvioDeResultados]) confirma a parcial do caderno, e o diretorio de respostas — um
 * [RespostasEmArquivo] de verdade, em disco — continua com o mesmo arquivo. A correcao discursiva ainda
 * precisa da imagem, e o gatilho "apos a sincronizacao" da classe H e o da nota, que nao existe nesta
 * mudanca; o unico limite e o prazo de 30 dias.
 *
 * **Canario (P13):** o teste afirma que a confirmacao aconteceu (`confirmados == 1` e a fila vazia). Sem
 * isso, "nada foi eliminado" valeria para um envio que nunca rodou.
 */
class EnvioDaParcialNaoEliminaARespostaTest {

    private class Fila(iniciais: List<EnvelopeDeEnvio>) : ResultadosPendentes {
        val linhas = iniciais.toMutableList()

        override fun guardar(resultado: ResultadoPendente) = error("nao usado neste teste")
        override fun guardarNota(nota: NotaPendente) = error("nao usado neste teste")
        override fun pendentesDa(organizacao: String) = linhas.filter { it.organizacao == organizacao }
        override fun quantosPendentes(organizacao: String) = pendentesDa(organizacao).size
        override fun apagarConfirmado(captureId: String) {
            linhas.removeAll { it.captureId == captureId }
        }
    }

    @Test
    fun `a parcial confirmada pelo servidor sai da fila e a resposta continua no aparelho`() = runBlocking {
        val diretorio = Files.createTempDirectory("respostas-envio").toFile()
        try {
            val respostas = RespostasEmArquivo(diretorio)
            val guardada = (respostas.gravar(byteArrayOf(1, 2, 3), 1L, false, 0) as RespostaDoQuadro.Guardada).resposta
            val fila = Fila(listOf(EnvelopeDeEnvio("cap-1", "org", "prova", "{}")))

            val resumo = EnvioDeResultados(fila) { Retorno.Respondeu(Unit) }.enviarPendentesDa("org")

            assertEquals(1, resumo.confirmados, "guarda de vacuidade: o servidor confirmou")
            assertEquals(0, fila.linhas.size, "guarda de vacuidade: a fila esvaziou")
            assertEquals(listOf(guardada.arquivo), respostas.listar())
            assertEquals(true, respostas.existe(guardada.arquivo))
        } finally {
            diretorio.deleteRecursively()
        }
    }
}
