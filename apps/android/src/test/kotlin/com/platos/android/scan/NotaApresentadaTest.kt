package com.platos.android.scan

import com.platos.domain.capture.QuestionAnswer
import com.platos.domain.scoring.ObjectiveScore
import com.platos.domain.scoring.PendingQuestion
import com.platos.domain.scoring.PendingReason
import com.platos.domain.scoring.QuestionOutcome
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * O que a tela mostra da nota.
 *
 * A pergunta que este arquivo responde e uma so: **uma nota com pendencia pode ser apresentada como
 * fechada?** Nao pode, e a diferenca entre as duas apresentacoes e invisivel para quem le a tela —
 * o numero e plausivel nos dois casos.
 */
class NotaApresentadaTest {

    /**
     * A nota como a tela a recebe.
     *
     * A evidencia por questao vai coerente com [pontos] e com [pendentes] porque a guarda de
     * construcao de `ObjectiveScore` a exige — nao porque esta classe a use. O que a tela mostra
     * continua sendo o total, o estado de fechamento e as pendencias; a evidencia atravessa a
     * apresentacao sem ser lida por ela.
     */
    private fun nota(pontos: Int, pendentes: List<PendingQuestion> = emptyList()): ObjectiveScore {
        val acertos = if (pontos > 0) {
            listOf(
                QuestionOutcome(
                    questionId = "q00",
                    answer = QuestionAnswer.Marcada("q00", "A"),
                    worth = pontos,
                    earned = pontos,
                ),
            )
        } else {
            emptyList()
        }
        val emRevisao = pendentes.map {
            QuestionOutcome(
                questionId = it.questionId,
                answer = QuestionAnswer.Indecisa(it.questionId, listOf("A")),
                worth = it.points,
                earned = 0,
            )
        }

        return ObjectiveScore(
            packageHash = "hash-de-teste",
            variantId = "v1",
            points = pontos,
            maxScore = 40,
            pending = pendentes,
            outcomes = acertos + emRevisao,
        )
    }

    @Test
    fun `nota sem pendencia e apresentada como fechada`() {
        val apresentada = apresentar(nota(pontos = 40))

        assertEquals("40 de 40", apresentada.pontuacao)
        assertTrue(apresentada.fechada)
        assertTrue(apresentada.pendencias.isEmpty())
    }

    @Test
    fun `nota com pendencia nao e apresentada como fechada, e diz o que esta em disputa`() {
        val apresentada = apresentar(
            nota(
                pontos = 30,
                pendentes = listOf(
                    PendingQuestion("q07", PendingReason.MULTIPLA_MARCACAO, points = 1),
                    PendingQuestion("q19", PendingReason.INDECISA, points = 1),
                ),
            ),
        )

        assertFalse(apresentada.fechada, "nota com pendencia nao pode ser apresentada como fechada")
        assertEquals(2, apresentada.pendencias.size)
        assertTrue(
            apresentada.resumo.contains("2") && apresentada.resumo.contains("disputa"),
            "o resumo tem de dizer quantas questoes e quanto esta em jogo: ${apresentada.resumo}",
        )
        // Cada pendencia nomeia a questao e o motivo — "indecisa" sozinho nao diz o que revisar.
        assertTrue(apresentada.pendencias[0].contains("q07"))
        assertTrue(apresentada.pendencias[0].contains("mais de uma alternativa marcada"))
        assertTrue(apresentada.pendencias[1].contains("q19"))
        assertTrue(apresentada.pendencias[1].contains("fraca"))
    }

    @Test
    fun `a pontuacao apresentada e sempre a apurada, e nao a apurada mais a disputa`() {
        // Somar o que esta em disputa daria 40 de 40 numa folha que ninguem revisou ainda.
        val apresentada = apresentar(
            nota(pontos = 38, pendentes = listOf(PendingQuestion("q40", PendingReason.INDECISA, 2))),
        )

        assertEquals("38 de 40", apresentada.pontuacao)
    }
}
