package com.platos.android.api

import com.platos.android.outbox.ResultadoPendente
import com.platos.domain.capture.QuestionAnswer
import com.platos.domain.scoring.ObjectiveScore
import com.platos.domain.scoring.PendingQuestion
import com.platos.domain.scoring.PendingReason
import com.platos.domain.scoring.QuestionOutcome
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * O corpo que sobe, prendido contra o literal que o servidor aceita.
 *
 * **O literal e escrito a mao, e nao gerado pelo mesmo serializador.** Comparar o que
 * `corpoDoEnvio` produz contra `Json.encodeToString` do mesmo DTO poria o mesmo codigo dos dois
 * lados da igualdade: renomear `capture_id` para `captureId` continuaria verde aqui, e a rota
 * recusaria todo envio no aparelho de verdade. E a mesma razao pela qual `ObtencaoDeRosterTest`
 * prende o literal do roster, e nao o objeto.
 *
 * O outro lado do par esta em `apps/api/.../ResultRouteTest.corpo()`, que monta o JSON a mao e o
 * envia para a rota real. Os dois literais precisam concordar; e por isso que eles sao **literais**.
 */
class ResultadoDtoTest {

    private val json = Json

    private fun pendente(
        token: String? = "aluno-1",
        pendencias: List<PendingQuestion> = emptyList(),
        outcomes: List<QuestionOutcome>,
        pontos: Int,
    ) = ResultadoPendente(
        captureId = "cap-abc",
        organizacao = "org-1",
        prova = "prova-r",
        studentToken = token,
        // 2026-09-17T12:00:00Z em milissegundos de epoch. Fixo: relogio no teste faria o corpo
        // esperado mudar a cada execucao, e o que este arquivo prende e a **forma**.
        apuradoEm = 1_789_646_400_000L,
        nota = ObjectiveScore(
            packageHash = "a".repeat(64),
            variantId = "v1",
            points = pontos,
            maxScore = 2,
            pending = pendencias,
            outcomes = outcomes,
        ),
    )

    @Test
    fun `o corpo tem os nomes de campo que a rota espera`() {
        val corpo = pendente(
            pontos = 1,
            outcomes = listOf(
                QuestionOutcome("q01", QuestionAnswer.Marcada("q01", "A"), worth = 1, earned = 1),
                QuestionOutcome("q02", QuestionAnswer.EmBranco("q02"), worth = 1, earned = 0),
            ),
        ).corpoDoEnvio()

        val esperado = """
            {"capture_id":"cap-abc","student_token":"aluno-1","package_hash":"${"a".repeat(64)}","variant_id":"v1","points":1,"max_score":2,"closed":true,"captured_at":"2026-09-17T12:00:00Z","observations":[{"item_id":"q01","answer_kind":"marcada","answer_options":["A"],"worth":1,"earned":1},{"item_id":"q02","answer_kind":"em_branco","answer_options":[],"worth":1,"earned":0}]}
        """.trimIndent()

        assertEquals(esperado, corpo)
    }

    /**
     * A folha avulsa manda `null` **explicito**, e nao omite o campo.
     *
     * Omitir dependeria de o servidor ter default para o campo ausente — coisa que ele pode ter hoje
     * e deixar de ter amanha, sem que nada aqui quebre ate alguem escanear uma folha avulsa em
     * producao.
     */
    @Test
    fun `folha avulsa manda student_token nulo, e nao omite o campo`() {
        val corpo = pendente(
            token = null,
            pontos = 0,
            outcomes = listOf(
                QuestionOutcome("q01", QuestionAnswer.EmBranco("q01"), worth = 1, earned = 0),
            ),
        ).corpoDoEnvio()

        assertTrue(
            corpo.contains(""""student_token":null"""),
            "o campo precisa viajar como nulo explicito: $corpo",
        )
    }

    @Test
    fun `pendencia viaja com o tipo dela e a nota nao vai fechada`() {
        val corpo = pendente(
            pontos = 1,
            pendencias = listOf(PendingQuestion("q02", PendingReason.MULTIPLA_MARCACAO, 1)),
            outcomes = listOf(
                QuestionOutcome("q01", QuestionAnswer.Marcada("q01", "A"), worth = 1, earned = 1),
                QuestionOutcome(
                    "q02",
                    QuestionAnswer.MultiplaMarcacao("q02", listOf("A", "C")),
                    worth = 1,
                    earned = 0,
                ),
            ),
        ).corpoDoEnvio()

        assertTrue(corpo.contains(""""answer_kind":"multipla_marcacao""""), corpo)
        assertTrue(corpo.contains(""""answer_options":["A","C"]"""), "as duas marcadas, e nao a vencedora: $corpo")
        assertEquals(
            "false",
            (json.parseToJsonElement(corpo) as JsonObject).getValue("closed").jsonPrimitive.content,
            "nota com pendencia nao pode subir declarada fechada",
        )
    }

    /**
     * Nada de dado pessoal alem do token, e nada de habilidade.
     *
     * A assercao e sobre o **conjunto de chaves**, e nao sobre a ausencia de uma palavra: procurar
     * por "nome" no texto passaria por um campo chamado `display` que carregasse a mesma coisa.
     */
    @Test
    fun `o corpo nao leva nome, turma, matricula nem habilidade`() {
        val corpo = pendente(
            pontos = 1,
            outcomes = listOf(
                QuestionOutcome("q01", QuestionAnswer.Marcada("q01", "A"), worth = 1, earned = 1),
                QuestionOutcome("q02", QuestionAnswer.EmBranco("q02"), worth = 1, earned = 0),
            ),
        ).corpoDoEnvio()

        val raiz = json.parseToJsonElement(corpo) as JsonObject
        assertEquals(
            setOf(
                "capture_id", "student_token", "package_hash", "variant_id",
                "points", "max_score", "closed", "captured_at", "observations",
            ),
            raiz.keys,
            "campo a mais no corpo e dado a mais saindo do aparelho",
        )

        val observacao = (raiz.getValue("observations") as kotlinx.serialization.json.JsonArray)
            .first() as JsonObject
        assertEquals(
            setOf("item_id", "answer_kind", "answer_options", "worth", "earned"),
            observacao.keys,
            "a evidencia nao carrega habilidade: ela e derivada do pacote, no servidor",
        )
    }
}
