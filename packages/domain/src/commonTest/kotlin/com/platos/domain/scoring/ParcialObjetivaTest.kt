package com.platos.domain.scoring

import com.platos.domain.capture.CapturePayload
import com.platos.domain.capture.QuestionAnswer
import com.platos.domain.exam.ExamPackage
import com.platos.domain.fixtures.Fixtures
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A apuracao parcial de uma prova com discursiva (`slice-5b-2-a-nota-objetiva-parcial`, spec de
 * `scoring`; tarefa 1.3). Um teste por cenario da ADDED.
 *
 * **O oraculo esta fixado aqui, e nao recalculado do pacote** (P4): a fixture tem quatro objetivas de
 * 1 ponto, `d1` de 3 e `d2` de 4, e maximo 11. Se a apuracao passasse a tirar o maximo objetivo ou o
 * valor das discursivas de outro lugar, uma comparacao contra o proprio pacote continuaria verde.
 *
 * Dois cenarios moram em outro lugar: "A parcial nao substitui a nota" e uma protecao de tipo, vista
 * falhar fora da arvore (tarefa 1.5); "A apuracao completa de prova com discursiva continua recusada"
 * e `ObjectiveScoringTest`, que nao mudou.
 */
class ParcialObjetivaTest {

    private val pacote: ExamPackage = Json.decodeFromString(
        ExamPackage.serializer(),
        Fixtures.PROVA_DISCURSIVA_PACKAGE_JSON,
    )

    private val folha = CapturePayload(
        examShortId = pacote.meta.examId,
        studentToken = "tok-a",
        variant = "v1",
        regionIndex = 0,
    )

    private val gabarito = pacote.answerKey.associate { it.itemId to it.correct }

    private fun certa(id: String) = QuestionAnswer.Marcada(id, gabarito.getValue(id))

    private fun errada(id: String) = QuestionAnswer.Marcada(id, listOf("A", "B", "C", "D").first { it != gabarito.getValue(id) })

    private fun apurada(outcome: PartialScoringOutcome): PartialScore {
        assertTrue(outcome is PartialScoringOutcome.Scored, "esperava parcial, veio: $outcome")
        return outcome.partial
    }

    private fun recusada(outcome: PartialScoringOutcome): String {
        assertTrue(outcome is PartialScoringOutcome.Rejected, "esperava recusa, veio: $outcome")
        return outcome.reason
    }

    /** Guarda de vacuidade: os numeros fixados abaixo so dizem algo sobre esta fixture. */
    @Test
    fun `a fixture e a prova com discursiva dos numeros fixados`() {
        assertEquals(false, pacote.meta.fullyOfflineGradable)
        assertEquals(listOf("q1", "q2", "q4", "q5"), pacote.answerKey.map { it.itemId })
        assertEquals(11, pacote.scoring.maxScore)
    }

    /** Cenario "Parcial de uma prova com discursiva". */
    @Test
    fun `tres objetivas certas e uma errada dao 3 de 4, com as discursivas aguardando`() {
        val respostas = listOf(certa("q1"), certa("q2"), errada("q4"), certa("q5"))

        val parcial = apurada(ObjectiveScoring.scorePartial(pacote, folha, respostas))

        assertEquals(3, parcial.objectivePoints)
        assertEquals(4, parcial.objectiveMaxScore)
        assertEquals(listOf(AwaitingEssay("d1", 3), AwaitingEssay("d2", 4)), parcial.awaiting)
        assertEquals(11, parcial.maxScore)
        assertEquals("v1", parcial.variantId)
        assertEquals(pacote.contentHash(), parcial.packageHash)
        assertEquals(
            listOf("q1", "q2", "q4", "q5"),
            parcial.outcomes.map { it.questionId },
            "a evidencia e das objetivas, e so delas",
        )
    }

    /**
     * Cenario "A parcial nunca e fechada".
     *
     * `PartialScore` nao tem `closed`, e isso e de compilacao: nao ha o que afirmar sobre um campo que
     * nao existe. O que se afirma aqui e o resto do cenario — sem pendencia objetiva, as discursivas
     * continuam aguardando correcao, e a parcial continua sendo parcial.
     */
    @Test
    fun `sem pendencia objetiva, as discursivas continuam aguardando correcao`() {
        val respostas = listOf(certa("q1"), QuestionAnswer.EmBranco("q2"), certa("q4"), errada("q5"))

        val parcial = apurada(ObjectiveScoring.scorePartial(pacote, folha, respostas))

        assertTrue(parcial.pending.isEmpty(), "a folha nao tem pendencia objetiva, ou o cenario nao mede")
        assertEquals(2, parcial.objectivePoints)
        assertEquals(listOf("d1", "d2"), parcial.awaiting.map { it.questionId })
        assertEquals(7, parcial.awaitingPoints)
    }

    /** Cenario "Os maximos fecham com a prova". */
    @Test
    fun `o maximo objetivo somado as discursivas e o maximo da prova`() {
        val respostas = listOf(certa("q1"), certa("q2"), certa("q4"), certa("q5"))

        val parcial = apurada(ObjectiveScoring.scorePartial(pacote, folha, respostas))

        assertEquals(4, parcial.objectiveMaxScore)
        assertEquals(7, parcial.awaitingPoints)
        assertEquals(11, parcial.objectiveMaxScore + parcial.awaitingPoints)
        assertEquals(parcial.maxScore, parcial.objectiveMaxScore + parcial.awaitingPoints)
    }

    /** Cenario "Conjunto objetivo divergente": uma objetiva a menos, e um item que nao e objetivo. */
    @Test
    fun `leitura que nao e o conjunto objetivo da variante e recusada, dizendo a divergencia`() {
        val semQ5 = listOf(certa("q1"), certa("q2"), certa("q4"))
        assertEquals(
            "itens objetivos lidos divergem da variante 'v1'; faltando: q5",
            recusada(ObjectiveScoring.scorePartial(pacote, folha, semQ5)),
        )

        val comD1 = listOf(certa("q1"), certa("q2"), certa("q4"), certa("q5"), QuestionAnswer.EmBranco("d1"))
        assertEquals(
            "itens objetivos lidos divergem da variante 'v1'; nao objetivos na variante: d1",
            recusada(ObjectiveScoring.scorePartial(pacote, folha, comD1)),
        )
    }

    /** Cenario "Ambigua continua sendo pendencia". */
    @Test
    fun `multipla marcacao entra nas pendencias, nao rende ponto, e fica em disputa`() {
        val respostas = listOf(
            QuestionAnswer.MultiplaMarcacao("q1", listOf("A", "B")),
            certa("q2"),
            certa("q4"),
            certa("q5"),
        )

        val parcial = apurada(ObjectiveScoring.scorePartial(pacote, folha, respostas))

        assertEquals(listOf(PendingQuestion("q1", PendingReason.MULTIPLA_MARCACAO, 1)), parcial.pending)
        assertEquals(0, parcial.outcomes.single { it.questionId == "q1" }.earned)
        assertEquals(3, parcial.objectivePoints)
        assertEquals(1, parcial.pointsAtStake)
    }

    /** Cenario "Parcial de prova so objetiva e recusada". */
    @Test
    fun `a prova so objetiva tem nota completa, e a parcial e recusada`() {
        val referencia: ExamPackage = Json.decodeFromString(
            ExamPackage.serializer(),
            Fixtures.PROVA_REFERENCIA_PACKAGE_JSON,
        )
        assertEquals(true, referencia.meta.fullyOfflineGradable)
        val todas = referencia.answerKey.map { QuestionAnswer.Marcada(it.itemId, it.correct) }

        val motivo = recusada(
            ObjectiveScoring.scorePartial(referencia, CapturePayload(referencia.meta.examId, "", "", 0), todas),
        )

        assertTrue(motivo.contains("tem nota completa"), "a recusa precisa dizer que a prova tem nota completa: $motivo")
    }
}
