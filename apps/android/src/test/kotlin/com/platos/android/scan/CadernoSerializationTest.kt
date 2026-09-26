package com.platos.android.scan

import com.platos.domain.capture.QuestionAnswer
import com.platos.domain.scoring.AwaitingEssay
import com.platos.domain.scoring.PartialScore
import com.platos.domain.scoring.PartialScoringOutcome
import com.platos.domain.scoring.PendingQuestion
import com.platos.domain.scoring.PendingReason
import com.platos.domain.scoring.QuestionOutcome
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * O `@Serializable` do caderno (`slice-5b-3-guardar-a-parcial-e-o-caderno`, tarefa 1.1) precisa
 * voltar a ser o mesmo `Caderno`, com os tres estados de regiao e a parcial guardada juntos — e nao
 * so compilar.
 */
class CadernoSerializationTest {

    private fun parcial(): PartialScore {
        val certa = QuestionOutcome("q1", QuestionAnswer.Marcada("q1", "A"), worth = 1, earned = 1)
        val indecisa = QuestionOutcome("q2", QuestionAnswer.Indecisa("q2", listOf("A")), worth = 1, earned = 0)
        return PartialScore(
            packageHash = "hash",
            variantId = "v1",
            objectivePoints = 1,
            objectiveMaxScore = 2,
            maxScore = 6,
            awaiting = listOf(AwaitingEssay("d1", 4)),
            pending = listOf(PendingQuestion("q2", PendingReason.INDECISA, 1)),
            outcomes = listOf(certa, indecisa),
        )
    }

    private fun caderno(parcial: PartialScoringOutcome?) = Caderno(
        aluno = "tok-a",
        regioes = listOf(
            RegiaoDoCaderno(0, gabarito = true, rotulo = Caderno.ROTULO_GABARITO, estado = EstadoDaRegiao.Capturada),
            RegiaoDoCaderno(1, gabarito = false, rotulo = "1", estado = EstadoDaRegiao.ComProblema("QR ilegivel")),
            RegiaoDoCaderno(2, gabarito = false, rotulo = "2", estado = EstadoDaRegiao.NaoVista),
        ),
        parcial = parcial,
    )

    @Test
    fun `o caderno com a parcial apurada volta igual`() {
        val original = caderno(PartialScoringOutcome.Scored(parcial()))

        val json = Json.encodeToString(Caderno.serializer(), original)
        val deVolta = Json.decodeFromString(Caderno.serializer(), json)

        assertEquals(original, deVolta)
    }

    @Test
    fun `o caderno com a parcial recusada volta igual`() {
        val original = caderno(PartialScoringOutcome.Rejected("itens lidos divergem da variante"))

        val json = Json.encodeToString(Caderno.serializer(), original)
        val deVolta = Json.decodeFromString(Caderno.serializer(), json)

        assertEquals(original, deVolta)
    }

    @Test
    fun `o caderno sem gabarito lido ainda volta igual`() {
        val original = caderno(parcial = null)

        val json = Json.encodeToString(Caderno.serializer(), original)
        val deVolta = Json.decodeFromString(Caderno.serializer(), json)

        assertEquals(original, deVolta)
    }
}
