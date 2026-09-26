package com.platos.domain.scoring

import com.platos.domain.capture.QuestionAnswer
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * O `@Serializable` novo (`slice-5b-3-guardar-a-parcial-e-o-caderno`, tarefa 1.1) precisa voltar a
 * ser o mesmo objeto de dominio, guardas de construcao incluidas — e nao um DTO que so parece igual.
 * Um teste por caso de [QuestionAnswer], porque cada um e um ramo distinto do serializador
 * polimorfico, e o quarto que sobra ([PartialScoringOutcome.Rejected]) confere o outro lado do tipo
 * que a parcial usa para dizer "recusada".
 */
class PartialScoreSerializationTest {

    private fun certa(id: String, vale: Int = 1) =
        QuestionOutcome(id, QuestionAnswer.Marcada(id, "A"), worth = vale, earned = vale)

    private fun indecisa(id: String) =
        QuestionOutcome(id, QuestionAnswer.Indecisa(id, listOf("A")), worth = 1, earned = 0)

    private fun emBranco(id: String) =
        QuestionOutcome(id, QuestionAnswer.EmBranco(id), worth = 1, earned = 0)

    private fun multiplaMarcacao(id: String) =
        QuestionOutcome(id, QuestionAnswer.MultiplaMarcacao(id, listOf("A", "B")), worth = 1, earned = 0)

    private fun <T> roundTrip(serializer: kotlinx.serialization.KSerializer<T>, valor: T): T {
        val json = Json.encodeToString(serializer, valor)
        return Json.decodeFromString(serializer, json)
    }

    @Test
    fun `a parcial completa volta igual, com os quatro casos de resposta`() {
        val parcial = PartialScore(
            packageHash = "hash",
            variantId = "v1",
            objectivePoints = 1,
            objectiveMaxScore = 4,
            maxScore = 11,
            awaiting = listOf(AwaitingEssay("d1", 3), AwaitingEssay("d2", 4)),
            pending = listOf(
                PendingQuestion("q3", PendingReason.INDECISA, 1),
                PendingQuestion("q4", PendingReason.MULTIPLA_MARCACAO, 1),
            ),
            outcomes = listOf(certa("q1"), emBranco("q2"), indecisa("q3"), multiplaMarcacao("q4")),
        )

        assertEquals(parcial, roundTrip(PartialScore.serializer(), parcial))
    }

    @Test
    fun `o resultado apurado volta igual`() {
        val parcial = PartialScore(
            packageHash = "hash",
            variantId = "v1",
            objectivePoints = 1,
            objectiveMaxScore = 1,
            maxScore = 4,
            awaiting = listOf(AwaitingEssay("d1", 3)),
            pending = emptyList(),
            outcomes = listOf(certa("q1")),
        )
        val apurada: PartialScoringOutcome = PartialScoringOutcome.Scored(parcial)

        assertEquals(apurada, roundTrip(PartialScoringOutcome.serializer(), apurada))
    }

    @Test
    fun `a recusa volta igual`() {
        val recusada: PartialScoringOutcome = PartialScoringOutcome.Rejected("motivo qualquer")

        assertEquals(recusada, roundTrip(PartialScoringOutcome.serializer(), recusada))
    }
}
