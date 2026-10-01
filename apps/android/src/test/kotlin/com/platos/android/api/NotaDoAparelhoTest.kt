package com.platos.android.api

import com.platos.android.outbox.NotaPendente
import com.platos.domain.capture.QuestionAnswer
import com.platos.domain.scoring.DiscursivaCorrigida
import com.platos.domain.scoring.NotaDoProfessor
import com.platos.domain.scoring.Pontos
import com.platos.domain.scoring.QuestionOutcome
import java.time.Instant
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

/**
 * O corpo da nota que o aparelho **monta** (`slice-5c-3-a-nota-no-aparelho`), contra o literal que o servidor le
 * (`GradedResultContractTest`, e `GradedResultDtoTest` deste lado). Os tres sao o mesmo texto: se o aparelho
 * mudar o que envia, ou o servidor o que aceita, um deles cai (`tools/parity/fio.mjs`).
 */
class NotaDoAparelhoTest {

    private val literal = """
        {"capture_id":"cap-nota-1","completes_capture_id":"cap-parcial-1","student_token":"aluno-1",
        "package_hash":"${"a".repeat(64)}","variant_id":"v1","origin":"teacher","path":"image",
        "points":"2.75","max_score":4,"closed":true,"captured_at":"2026-09-17T12:00:00Z",
        "observations":[{"item_id":"q01","answer_kind":"marcada","answer_options":["A"],"worth":1,"earned":1}],
        "essay_grades":[{"item_id":"d1","earned":"1.75"}]}
    """.trimIndent().replace("\n", "")

    private fun nota() = NotaDoProfessor(
        packageHash = "a".repeat(64),
        variantId = "v1",
        objectivePoints = 1,
        objectiveMaxScore = 1,
        maxScore = 4,
        pending = emptyList(),
        outcomes = listOf(QuestionOutcome("q01", QuestionAnswer.Marcada("q01", "A"), worth = 1, earned = 1)),
        essays = listOf(DiscursivaCorrigida("d1", worth = 3, earned = Pontos.parse("1.75"))),
    )

    private fun pendente(token: String? = "aluno-1") = NotaPendente(
        captureId = "cap-nota-1",
        completaCaptura = "cap-parcial-1",
        organizacao = "org-1",
        prova = "prova-r",
        studentToken = token,
        apuradoEm = Instant.parse("2026-09-17T12:00:00Z").toEpochMilli(),
        nota = nota(),
    )

    @Test
    fun `o corpo e o literal que o servidor le`() {
        assertEquals(literal, pendente().corpoDoEnvio())
    }

    @Test
    fun `a folha avulsa manda student_token nulo, e nao omite o campo`() {
        val corpo = pendente(token = null).corpoDoEnvio()

        assertEquals(true, corpo.contains("\"student_token\":null"), corpo)
    }

    @Test
    fun `a pontuacao viaja como texto decimal, nunca como numero`() {
        val corpo = pendente().corpoDoEnvio()

        assertEquals(true, corpo.contains("\"points\":\"2.75\""), corpo)
        assertEquals(true, corpo.contains("\"earned\":\"1.75\""), corpo)
    }

    @Test
    fun `o corpo nao leva imagem, arquivo, nome, turma nem matricula`() {
        val corpo = pendente().corpoDoEnvio()

        for (proibido in listOf("arquivo", ".png", "nome", "turma", "matricula", "imagem")) {
            assertFalse(corpo.contains(proibido), "o corpo da nota traz '$proibido': $corpo")
        }
    }
}
