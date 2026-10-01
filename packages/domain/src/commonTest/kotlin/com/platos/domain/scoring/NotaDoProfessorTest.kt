package com.platos.domain.scoring

import com.platos.domain.capture.QuestionAnswer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * A nota do professor completa a parcial (`slice-5c-2-a-nota-do-professor`, spec `scoring`).
 *
 * Um caso por recusa, e cada um **le a mensagem**: a camada que segurou e a que a mensagem nomeia
 * (`rigorous.md` §3). A parcial de partida: quatro objetivas de 1 ponto (tres certas, uma errada),
 * discursivas `d1` (3) e `d2` (4), prova de 11.
 */
class NotaDoProfessorTest {

    private fun certa(id: String) =
        QuestionOutcome(id, QuestionAnswer.Marcada(id, "A"), worth = 1, earned = 1)

    private fun errada(id: String) =
        QuestionOutcome(id, QuestionAnswer.Marcada(id, "B"), worth = 1, earned = 0)

    private fun multipla(id: String) =
        QuestionOutcome(id, QuestionAnswer.MultiplaMarcacao(id, listOf("A", "B")), worth = 1, earned = 0)

    private fun parcial(
        outcomes: List<QuestionOutcome> = listOf(certa("q1"), certa("q2"), certa("q3"), errada("q4")),
        pending: List<PendingQuestion> = emptyList(),
        awaiting: List<AwaitingEssay> = listOf(AwaitingEssay("d1", 3), AwaitingEssay("d2", 4)),
        maxScore: Int = 11,
    ) = PartialScore(
        packageHash = "hash",
        variantId = "v1",
        objectivePoints = outcomes.sumOf { it.earned },
        objectiveMaxScore = 4,
        maxScore = maxScore,
        awaiting = awaiting,
        pending = pending,
        outcomes = outcomes,
    )

    private fun dar(vararg notas: Pair<String, String>) =
        notas.map { PontuacaoDada(it.first, Pontos.parse(it.second)) }

    private fun nota(parcial: PartialScore = parcial(), vararg notas: Pair<String, String>): NotaDoProfessor =
        (CorrecaoDoProfessor.completar(parcial, dar(*notas)) as NotaDoProfessorOutcome.Scored).nota

    private fun recusa(parcial: PartialScore = parcial(), vararg notas: Pair<String, String>): String =
        (CorrecaoDoProfessor.completar(parcial, dar(*notas)) as NotaDoProfessorOutcome.Rejected).reason

    @Test
    fun `completar a parcial soma a objetiva e as discursivas, 3 + 1_5 + 3_75 = 8_25 de 11`() {
        val nota = nota(parcial(), "d1" to "1.5", "d2" to "3.75")

        assertEquals(Pontos.parse("8.25"), nota.total)
        assertEquals(11, nota.maxScore)
        assertEquals(listOf("d1", "d2"), nota.essays.map { it.questionId })
        assertEquals(listOf(Pontos.parse("1.5"), Pontos.parse("3.75")), nota.essays.map { it.earned })
    }

    @Test
    fun `sem pendencia objetiva a nota fecha`() {
        assertTrue(nota(parcial(), "d1" to "3", "d2" to "4").closed)
    }

    @Test
    fun `pendencia objetiva mantem a nota aberta, e o que ela disputa continua contado`() {
        val comPendencia = parcial(
            outcomes = listOf(certa("q1"), certa("q2"), certa("q3"), multipla("q4")),
            pending = listOf(PendingQuestion("q4", PendingReason.MULTIPLA_MARCACAO, 1)),
        )

        val nota = nota(comPendencia, "d1" to "3", "d2" to "4")

        assertFalse(nota.closed)
        assertEquals(1, nota.pointsAtStake)
        assertEquals(listOf("q4"), nota.pending.map { it.questionId })
    }

    @Test
    fun `a nota do professor nao mexe na objetiva`() {
        val base = parcial()

        val nota = nota(base, "d1" to "1", "d2" to "1")

        assertEquals(base.objectivePoints, nota.objectivePoints)
        assertEquals(base.outcomes, nota.outcomes)
        assertEquals(base.pending, nota.pending)
    }

    @Test
    fun `1_5 e 1_50 produzem o mesmo resultado`() {
        assertEquals(nota(parcial(), "d1" to "1.5", "d2" to "2"), nota(parcial(), "d1" to "1.50", "d2" to "2.00"))
    }

    @Test
    fun `a soma e exata, 0_1 e 0_2 dao 0_3 mais a objetiva`() {
        assertEquals(Pontos.parse("3.3"), nota(parcial(), "d1" to "0.1", "d2" to "0.2").total)
    }

    @Test
    fun `zero em todas e o maximo em todas sao validos`() {
        assertEquals(Pontos.parse("3"), nota(parcial(), "d1" to "0", "d2" to "0.00").total)
        assertEquals(Pontos.parse("10"), nota(parcial(), "d1" to "3.00", "d2" to "4").total)
    }

    @Test
    fun `um centesimo acima do valor e recusado e a mensagem diz questao e faixa`() {
        val motivo = recusa(parcial(), "d1" to "3.01", "d2" to "4")

        assertTrue("'d1'" in motivo && "3.01" in motivo && "no maximo 3" in motivo, motivo)
    }

    @Test
    fun `pontuacao em objetiva ou em item que a variante nao tem e recusada`() {
        val objetiva = recusa(parcial(), "d1" to "1", "d2" to "1", "q1" to "1")
        assertTrue("'q1'" in objetiva && "nao e discursiva" in objetiva, objetiva)

        val alheio = recusa(parcial(), "d1" to "1", "d2" to "1", "x9" to "1")
        assertTrue("'x9'" in alheio && "nao e discursiva" in alheio, alheio)
    }

    @Test
    fun `discursiva sem pontuacao e recusada e a mensagem diz qual falta`() {
        val motivo = recusa(parcial(), "d1" to "1")

        assertTrue("'d2'" in motivo && "nao recebeu pontuacao" in motivo, motivo)
    }

    @Test
    fun `discursiva pontuada duas vezes e recusada`() {
        val motivo = recusa(parcial(), "d1" to "1", "d1" to "2", "d2" to "1")

        assertTrue("'d1'" in motivo && "mais de uma vez" in motivo, motivo)
    }

    @Test
    fun `prova so objetiva nao tem o que pontuar`() {
        val soObjetiva = PartialScore(
            "hash", "v1", objectivePoints = 3, objectiveMaxScore = 4, maxScore = 4,
            awaiting = emptyList(), pending = emptyList(),
            outcomes = listOf(certa("q1"), certa("q2"), certa("q3"), errada("q4")),
        )

        val motivo = recusa(soObjetiva)

        assertTrue("nota completa" in motivo, motivo)
    }

    @Test
    fun `os maximos fecham com a prova e a soma por questao fecha com o total`() {
        val nota = nota(parcial(), "d1" to "1.75", "d2" to "2.5")

        assertEquals(
            nota.objectiveMaxScore + nota.essays.sumOf { it.worth },
            nota.maxScore,
        )
        val porQuestao = nota.outcomes.fold(Pontos.ZERO) { a, o -> a + Pontos.inteiros(o.earned) } +
            nota.essays.fold(Pontos.ZERO) { a, e -> a + e.earned }
        assertEquals(nota.total, porQuestao)
    }
}
