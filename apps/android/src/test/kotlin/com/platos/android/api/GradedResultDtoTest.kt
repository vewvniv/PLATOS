package com.platos.android.api

import com.platos.domain.transport.EssayGradeDto
import com.platos.domain.transport.GradedResultSubmissionDto
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * O corpo da nota do professor, prendido contra um literal escrito a mao **deste lado do fio**.
 *
 * O aparelho ainda nao envia a nota do professor (e a 5c-3). Este teste existe porque o contrato mora em
 * `packages/domain`, o aparelho compila contra ele, e `tools/parity/fio.mjs` reprova no CI todo tipo de
 * `transport` sem literal nos dois lados. O par esta em
 * `apps/api/.../GradedResultContractTest`.
 */
class GradedResultDtoTest {

    private val corpo = """
        {"capture_id":"cap-nota-1","completes_capture_id":"cap-parcial-1","student_token":"aluno-1",
        "package_hash":"${"a".repeat(64)}","variant_id":"v1","origin":"teacher","path":"image",
        "points":"2.75","max_score":4,"closed":true,"captured_at":"2026-09-17T12:00:00Z",
        "observations":[{"item_id":"q01","answer_kind":"marcada","answer_options":["A"],"worth":1,"earned":1}],
        "essay_grades":[{"item_id":"d1","earned":"1.75"}]}
    """.trimIndent().replace("\n", "")

    @Test
    fun `o aparelho entende o literal da nota do professor`() {
        val dto = Json.decodeFromString(GradedResultSubmissionDto.serializer(), corpo)

        assertEquals("cap-nota-1", dto.captureId)
        assertEquals("2.75", dto.points)
        assertEquals(listOf(EssayGradeDto("d1", "1.75")), dto.essayGrades)
    }
}
