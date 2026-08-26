package com.platos.domain.capture

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A resposta de cada questao, montada a partir de vereditos.
 *
 * Nenhum teste aqui passa por imagem, por limiar ou por cobertura: o que entra sao vereditos
 * escritos a mao. E deliberado — a classificacao e a unica coisa que estes testes podem quebrar,
 * entao quando um deles fica vermelho nao ha um segundo suspeito.
 */
class AnswerSheetTest {

    private fun bolha(questao: String, alternativa: String, veredito: BubbleVerdict) =
        BubbleJudgement(OmrMeasurement(questao, alternativa, 0), veredito)

    private fun bolha(questao: String, alternativa: String, cobertura: Int, veredito: BubbleVerdict) =
        BubbleJudgement(OmrMeasurement(questao, alternativa, cobertura), veredito)

    @Test
    fun `uma alternativa marcada vira a resposta`() {
        val respostas = AnswerSheet.of(
            listOf(
                bolha("q01", "A", BubbleVerdict.VAZIA),
                bolha("q01", "B", BubbleVerdict.MARCADA),
                bolha("q01", "C", BubbleVerdict.VAZIA),
                bolha("q01", "D", BubbleVerdict.VAZIA),
            ),
        )

        assertEquals(listOf(QuestionAnswer.Marcada("q01", "B")), respostas)
    }

    @Test
    fun `nenhuma marcada e nenhuma indecisa vira em branco`() {
        val respostas = AnswerSheet.of(
            listOf("A", "B", "C", "D").map { bolha("q01", it, BubbleVerdict.VAZIA) },
        )

        assertEquals(listOf(QuestionAnswer.EmBranco("q01")), respostas)
    }

    @Test
    fun `duas marcadas viram multipla marcacao, nomeando as duas`() {
        val respostas = AnswerSheet.of(
            listOf(
                bolha("q01", "A", BubbleVerdict.MARCADA),
                bolha("q01", "B", BubbleVerdict.VAZIA),
                bolha("q01", "C", BubbleVerdict.MARCADA),
                bolha("q01", "D", BubbleVerdict.VAZIA),
            ),
        )

        assertEquals(listOf(QuestionAnswer.MultiplaMarcacao("q01", listOf("A", "C"))), respostas)
    }

    @Test
    fun `multipla marcacao nao e desempatada por cobertura`() {
        // O caso que a spec proibe explicitamente. As coberturas sao bem diferentes — 980 contra
        // 505 — e e exatamente isso que torna o desempate tentador: "obviamente o aluno quis o A".
        // A folha tem decoracao dentro da bolha, e esta fatia existe porque ainda nao sabemos como
        // ela se comporta sob camera. Escolher aqui e converter defeito nosso em nota do aluno.
        val respostas = AnswerSheet.of(
            listOf(
                bolha("q01", "A", 980, BubbleVerdict.MARCADA),
                bolha("q01", "B", 505, BubbleVerdict.MARCADA),
                bolha("q01", "C", 12, BubbleVerdict.VAZIA),
                bolha("q01", "D", 8, BubbleVerdict.VAZIA),
            ),
        )

        val resposta = respostas.single()
        assertTrue(
            resposta is QuestionAnswer.MultiplaMarcacao,
            "cobertura de 980 contra 505 nao pode virar resposta: $resposta",
        )
        assertEquals(listOf("A", "B"), resposta.options)
    }

    @Test
    fun `bolha indecisa sem nenhuma marcada vira indecisa, e nao em branco`() {
        val respostas = AnswerSheet.of(
            listOf(
                bolha("q01", "A", BubbleVerdict.VAZIA),
                bolha("q01", "B", BubbleVerdict.INDECISA),
                bolha("q01", "C", BubbleVerdict.VAZIA),
                bolha("q01", "D", BubbleVerdict.VAZIA),
            ),
        )

        assertEquals(listOf(QuestionAnswer.Indecisa("q01", listOf("B"))), respostas)
    }

    @Test
    fun `bolha indecisa ao lado de uma marcada nao impede a resposta`() {
        // Rasura tipica: o aluno marcou A, apagou, marcou B. O apagado sobra como indecisa, e a
        // marcada continua sendo a resposta — a duvida so decide quando nao ha nenhuma marcada.
        val respostas = AnswerSheet.of(
            listOf(
                bolha("q01", "A", BubbleVerdict.INDECISA),
                bolha("q01", "B", BubbleVerdict.MARCADA),
                bolha("q01", "C", BubbleVerdict.VAZIA),
                bolha("q01", "D", BubbleVerdict.VAZIA),
            ),
        )

        assertEquals(listOf(QuestionAnswer.Marcada("q01", "B")), respostas)
    }

    @Test
    fun `cada questao declarada aparece exatamente uma vez, na ordem do mapa`() {
        val bolhas = listOf("q01", "q02", "q03").flatMap { questao ->
            listOf("A", "B", "C", "D").map { bolha(questao, it, BubbleVerdict.VAZIA) }
        }

        val respostas = AnswerSheet.of(bolhas)

        assertEquals(listOf("q01", "q02", "q03"), respostas.map { it.questionId })
        assertEquals(3, respostas.distinctBy { it.questionId }.size)
    }

    @Test
    fun `os quatro casos convivem na mesma folha`() {
        // Uma folha real tem os quatro ao mesmo tempo, e o agrupamento e onde eles poderiam
        // vazar de uma questao para a vizinha.
        val respostas = AnswerSheet.of(
            listOf(
                bolha("q01", "A", BubbleVerdict.MARCADA),
                bolha("q01", "B", BubbleVerdict.VAZIA),
                bolha("q02", "A", BubbleVerdict.VAZIA),
                bolha("q02", "B", BubbleVerdict.VAZIA),
                bolha("q03", "A", BubbleVerdict.MARCADA),
                bolha("q03", "B", BubbleVerdict.MARCADA),
                bolha("q04", "A", BubbleVerdict.INDECISA),
                bolha("q04", "B", BubbleVerdict.VAZIA),
            ),
        )

        assertEquals(
            listOf(
                QuestionAnswer.Marcada("q01", "A"),
                QuestionAnswer.EmBranco("q02"),
                QuestionAnswer.MultiplaMarcacao("q03", listOf("A", "B")),
                QuestionAnswer.Indecisa("q04", listOf("A")),
            ),
            respostas,
        )
    }
}
