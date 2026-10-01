package com.platos.api.http

import com.platos.domain.transport.EssayGradeDto
import com.platos.domain.transport.GradedResultSubmissionDto
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * O corpo da nota do professor, prendido contra um literal escrito a mao **deste lado do fio**.
 *
 * Desserializar o corpo com o proprio tipo nao prende nada (P4): o que prende o nome de um campo e o JSON
 * com esse nome digitado. O outro lado do par esta em
 * `apps/android/.../GradedResultDtoTest`, e `tools/parity/fio.mjs` exige os dois.
 */
class GradedResultContractTest {

    private val corpo = """
        {"capture_id":"cap-nota-1","completes_capture_id":"cap-parcial-1","student_token":"aluno-1",
        "package_hash":"${"a".repeat(64)}","variant_id":"v1","origin":"teacher","path":"image",
        "points":"2.75","max_score":4,"closed":true,"captured_at":"2026-09-17T12:00:00Z",
        "observations":[{"item_id":"q01","answer_kind":"marcada","answer_options":["A"],"worth":1,"earned":1}],
        "essay_grades":[{"item_id":"d1","earned":"1.75"}]}
    """.trimIndent().replace("\n", "")

    @Test
    fun `o servidor entende o literal da nota do professor`() {
        val dto = Json { ignoreUnknownKeys = true }.decodeFromString<GradedResultSubmissionDto>(corpo)

        assertEquals("cap-nota-1", dto.captureId)
        assertEquals("cap-parcial-1", dto.completesCaptureId)
        assertEquals("2.75", dto.points)
        assertEquals(listOf(EssayGradeDto("d1", "1.75")), dto.essayGrades)
    }
}
