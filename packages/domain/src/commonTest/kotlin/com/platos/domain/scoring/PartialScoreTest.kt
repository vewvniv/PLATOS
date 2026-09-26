package com.platos.domain.scoring

import com.platos.domain.capture.QuestionAnswer
import com.platos.domain.exam.ExamPackage
import com.platos.domain.exam.QuestionKind
import com.platos.domain.fixtures.Fixtures
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * As guardas de construcao da parcial (`slice-5b-2-a-nota-objetiva-parcial`, decisao 1; tarefa 1.2).
 *
 * Um teste por guarda, e cada um viola **so** a dele, a partir de uma parcial valida: a da fixture
 * discursiva, com quatro objetivas de 1 ponto, discursivas de 3 e 4, e maximo 11. Cada assercao le a
 * mensagem, porque "recusou" nao distingue qual guarda segurou (`rigorous.md` §3).
 */
class PartialScoreTest {

    private val pacote: ExamPackage = Json.decodeFromString(
        ExamPackage.serializer(),
        Fixtures.PROVA_DISCURSIVA_PACKAGE_JSON,
    )

    private fun certa(id: String, vale: Int = 1) =
        QuestionOutcome(id, QuestionAnswer.Marcada(id, "A"), worth = vale, earned = vale)

    private fun indecisa(id: String) =
        QuestionOutcome(id, QuestionAnswer.Indecisa(id, listOf("A")), worth = 1, earned = 0)

    private val discursivas = listOf(AwaitingEssay("d1", 3), AwaitingEssay("d2", 4))

    /** A parcial valida de onde cada caso parte: tres certas e uma pendente, 3 de 4 com 1 em disputa. */
    private fun parcial(
        objectivePoints: Int = 3,
        objectiveMaxScore: Int = 4,
        maxScore: Int = 11,
        awaiting: List<AwaitingEssay> = discursivas,
        pending: List<PendingQuestion> = listOf(PendingQuestion("q5", PendingReason.INDECISA, 1)),
        outcomes: List<QuestionOutcome> = listOf(certa("q1"), certa("q2"), certa("q4"), indecisa("q5")),
    ) = PartialScore("hash", "v1", objectivePoints, objectiveMaxScore, maxScore, awaiting, pending, outcomes)

    private fun recusa(bloco: () -> Unit): String =
        assertFailsWith<IllegalArgumentException> { bloco() }.message.orEmpty()

    @Test
    fun `a parcial de partida e valida`() {
        val valida = parcial()

        assertEquals(1, valida.pointsAtStake)
        assertEquals(7, valida.awaitingPoints)
    }

    @Test
    fun `parcial fora da escala objetiva nao e representavel`() {
        // Acima do maximo objetivo tambem passa da guarda de disputa, e e a mensagem que diz qual
        // segurou: a de escala vem antes.
        val motivo = recusa {
            parcial(
                objectivePoints = 5,
                pending = emptyList(),
                outcomes = listOf(certa("q1", 5)),
            )
        }

        assertTrue(motivo.contains("parcial 5 fora de 0..4"), "a recusa precisa ser pela escala: $motivo")
    }

    @Test
    fun `parcial que com a disputa passa do maximo objetivo nao e representavel`() {
        val motivo = recusa {
            parcial(
                objectivePoints = 4,
                pending = listOf(PendingQuestion("q5", PendingReason.INDECISA, 1)),
                outcomes = listOf(certa("q1"), certa("q2"), certa("q4", 2), indecisa("q5")),
            )
        }

        assertTrue(motivo.contains("em disputa passam de 4"), "a recusa precisa ser pela disputa: $motivo")
    }

    @Test
    fun `evidencia que nao soma a parcial nao e representavel`() {
        val motivo = recusa { parcial(objectivePoints = 2) }

        assertTrue(
            motivo.contains("a evidencia soma 3 ponto(s) e a parcial apurada e 2"),
            "a recusa precisa ser pela soma: $motivo",
        )
    }

    @Test
    fun `item repetido na evidencia nao e representavel`() {
        val motivo = recusa {
            parcial(
                objectivePoints = 3,
                pending = emptyList(),
                outcomes = listOf(certa("q1"), certa("q1"), certa("q2")),
            )
        }

        assertTrue(motivo.contains("a parcial repete o item: q1"), "a recusa precisa ser pela repeticao: $motivo")
    }

    @Test
    fun `a mesma questao objetiva e aguardando correcao nao e representavel`() {
        val motivo = recusa {
            parcial(awaiting = listOf(AwaitingEssay("q1", 3), AwaitingEssay("d2", 4)))
        }

        assertTrue(motivo.contains("a parcial repete o item: q1"), "a recusa precisa ser pela repeticao: $motivo")
    }

    @Test
    fun `pendencias que a evidencia nao confirma nao sao representaveis`() {
        val motivo = recusa {
            parcial(pending = listOf(PendingQuestion("q4", PendingReason.INDECISA, 1)))
        }

        assertTrue(
            motivo.contains("dependem de revisao [q5] e a lista de pendencias diz [q4]"),
            "a recusa precisa ser pelo desencontro entre evidencia e pendencias: $motivo",
        )
    }

    /**
     * A guarda nova: o maximo objetivo somado as discursivas e o maximo da prova.
     *
     * **O pacote deste teste tem uma rubrica que nao fecha**: um criterio de `d1` passa de 2 para 3
     * pontos, e `max_score` continua 11. Os numeros da parcial saem dele como a apuracao os tiraria —
     * o gabarito para o maximo objetivo, as rubricas para as discursivas, `scoring` para o maximo —, e
     * a evidencia continua coerente, entao esta e a unica guarda que ele viola.
     */
    @Test
    fun `parcial cujo maximo nao fecha com o da prova nao e representavel`() {
        val d1 = pacote.items.single { it.id == "d1" }
        val rubrica = requireNotNull(d1.rubric)
        val criterio = rubrica.criteria.first()
        val quebrado = pacote.copy(
            items = pacote.items.map {
                if (it.id != "d1") {
                    it
                } else {
                    it.copy(
                        rubric = rubrica.copy(
                            criteria = listOf(criterio.copy(points = criterio.points + 1)) + rubrica.criteria.drop(1),
                        ),
                    )
                }
            },
        )
        val objetivoMaximo = quebrado.answerKey.sumOf { it.points }
        val aguardando = quebrado.items.filter { it.kind == QuestionKind.ESSAY }.map { item ->
            AwaitingEssay(item.id, requireNotNull(item.rubric).criteria.sumOf { it.points })
        }
        assertEquals(4, objetivoMaximo)
        assertEquals(listOf(AwaitingEssay("d1", 4), AwaitingEssay("d2", 4)), aguardando)
        assertEquals(11, quebrado.scoring.maxScore)

        val motivo = recusa {
            parcial(
                objectiveMaxScore = objetivoMaximo,
                maxScore = quebrado.scoring.maxScore,
                awaiting = aguardando,
            )
        }

        assertTrue(
            motivo.contains("o maximo objetivo (4) mais as discursivas aguardando correcao (8) somam 12, e a prova vale 11"),
            "a recusa precisa ser pelo maximo que nao fecha: $motivo",
        )
    }
}
