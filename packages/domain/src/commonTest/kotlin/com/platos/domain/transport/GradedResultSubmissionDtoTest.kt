package com.platos.domain.transport

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * O corpo da nota do professor, como o literal que o fio carrega (`slice-5c-2-a-nota-do-professor`).
 *
 * O literal e escrito a mao nos tres lados (dominio, servidor, aparelho): `tools/parity/fio.mjs` reprova
 * no CI o tipo de `transport` que nao o tenha nos DOIS lados do fio.
 */
class GradedResultSubmissionDtoTest {

    private val corpo = """
        {"capture_id":"cap-nota-1","completes_capture_id":"cap-parcial-1","student_token":"aluno-1",
        "package_hash":"${"a".repeat(64)}","variant_id":"v1","origin":"teacher","path":"image",
        "points":"2.75","max_score":4,"closed":true,"captured_at":"2026-09-17T12:00:00Z",
        "observations":[{"item_id":"q01","answer_kind":"marcada","answer_options":["A"],"worth":1,"earned":1}],
        "essay_grades":[{"item_id":"d1","earned":"1.75"}]}
    """.trimIndent().replace("\n", "")

    @Test
    fun `o literal decodifica, e a pontuacao viaja como string`() {
        val dto = Json.decodeFromString(GradedResultSubmissionDto.serializer(), corpo)

        assertEquals("cap-parcial-1", dto.completesCaptureId)
        assertEquals("teacher", dto.origin)
        assertEquals("image", dto.path)
        assertEquals("2.75", dto.points)
        assertEquals(listOf(EssayGradeDto("d1", "1.75")), dto.essayGrades)
    }

    @Test
    fun `ida e volta preserva o corpo`() {
        val dto = Json.decodeFromString(GradedResultSubmissionDto.serializer(), corpo)

        assertEquals(dto, Json.decodeFromString(GradedResultSubmissionDto.serializer(), Json.encodeToString(GradedResultSubmissionDto.serializer(), dto)))
    }

    @Test
    fun `answer_kind passa a ter cinco valores, e o novo e o ultimo`() {
        assertEquals(5, AnswerKind.TODOS.size)
        assertEquals("discursiva_corrigida", AnswerKind.TODOS.last())
        assertEquals(AnswerKind.DISCURSIVA_CORRIGIDA, AnswerKind.TODOS.last())
    }
}
