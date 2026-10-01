package com.platos.android.outbox

import com.platos.android.scan.Caderno
import com.platos.android.scan.CadernosGuardados
import com.platos.domain.capture.QuestionAnswer
import com.platos.domain.scoring.DiscursivaCorrigida
import com.platos.domain.scoring.NotaDoProfessor
import com.platos.domain.scoring.Pontos
import com.platos.domain.scoring.QuestionOutcome
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * A gravacao composta da nota (`slice-5c-3-a-nota-no-aparelho`, revisao final): a nota e a **unica** parte que decide se
 * o professor precisa tentar de novo. Falhar o caderno ou o agendamento depois de a nota estar duravel nao pode virar
 * "tente de novo": a nova tentativa cunharia outra chave e deixaria duas notas na fila.
 */
class GravacaoDaNotaTest {

    private class Fila(private val quebraNaNota: Boolean = false) : ResultadosPendentes {
        val notas = mutableListOf<NotaPendente>()
        override fun guardar(resultado: ResultadoPendente) = error("nao usado")
        override fun guardarNota(nota: NotaPendente) {
            if (quebraNaNota) throw IllegalStateException("disco cheio")
            notas += nota
        }
        override fun pendentesDa(organizacao: String) = emptyList<EnvelopeDeEnvio>()
        override fun quantosPendentes(organizacao: String) = notas.size
        override fun apagarConfirmado(captureId: String) = Unit
    }

    private class Cadernos(private val quebra: Boolean = false) : CadernosGuardados {
        val guardados = mutableListOf<Caderno>()
        override fun guardar(organizacao: String, examId: String, caderno: Caderno) {
            if (quebra) throw IllegalStateException("caderno.db cheio")
            guardados += caderno
        }
        override fun ler(organizacao: String, examId: String): Caderno? = null
        override fun todos(): List<Caderno> = guardados
    }

    private val nota = NotaPendente(
        captureId = "cap-nota-1",
        completaCaptura = "cap-parcial-1",
        organizacao = "org-1",
        prova = "prova-r",
        studentToken = "aluno-1",
        apuradoEm = 1L,
        nota = NotaDoProfessor(
            packageHash = "a".repeat(64),
            variantId = "v1",
            objectivePoints = 1,
            objectiveMaxScore = 1,
            maxScore = 4,
            pending = emptyList(),
            outcomes = listOf(QuestionOutcome("q01", QuestionAnswer.Marcada("q01", "A"), worth = 1, earned = 1)),
            essays = listOf(DiscursivaCorrigida("d1", worth = 3, earned = Pontos.parse("1.75"))),
        ),
    )

    private val caderno = Caderno("aluno-1", emptyList(), null, corrigido = true)

    @Test
    fun `tudo certo grava a nota, o caderno e agenda o envio`() {
        val fila = Fila()
        val cadernos = Cadernos()
        var agendou = false

        val gravou = gravarNota(fila, cadernos, nota, caderno, "exam-1") { agendou = true }

        assertTrue(gravou)
        assertEquals(listOf(nota), fila.notas)
        assertEquals(listOf(caderno), cadernos.guardados)
        assertTrue(agendou)
    }

    @Test
    fun `se a nota nao grava, nada mais e feito e o professor pode tentar de novo`() {
        val fila = Fila(quebraNaNota = true)
        val cadernos = Cadernos()
        var agendou = false

        val gravou = gravarNota(fila, cadernos, nota, caderno, "exam-1") { agendou = true }

        assertFalse(gravou)
        assertTrue(cadernos.guardados.isEmpty(), "o caderno nao pode ser corrigido sem a nota na fila")
        assertFalse(agendou)
    }

    @Test
    fun `a nota duravel e sucesso mesmo que o caderno nao grave, para a nova tentativa nao duplicar`() {
        val fila = Fila()
        var agendou = false

        val gravou = gravarNota(fila, Cadernos(quebra = true), nota, caderno, "exam-1") { agendou = true }

        assertTrue(gravou)
        assertEquals(1, fila.notas.size)
        assertTrue(agendou)
    }

    @Test
    fun `a nota duravel e sucesso mesmo que o agendamento falhe, o envio sobe na proxima sessao`() {
        val fila = Fila()

        val gravou = gravarNota(fila, Cadernos(), nota, caderno, "exam-1") { throw IllegalStateException("sem WorkManager") }

        assertTrue(gravou)
        assertEquals(1, fila.notas.size)
    }
}
