package com.platos.domain.transport

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertFalse

/**
 * O campo `partial`, aditivo (`slice-5b-4-envio-da-parcial`, design decisão 3).
 *
 * Um corpo emitido antes desta mudança não tem o campo. Decodificá-lo precisa continuar
 * funcionando, e o valor decodificado precisa ser o mesmo que o corpo antigo sempre significou:
 * "isto não é uma parcial".
 */
class ResultSubmissionDtoTest {

    @Test
    fun `corpo sem o campo partial decodifica como partial = false`() {
        val corpoAntigo = """
            {"capture_id":"cap-1","student_token":"tok-a","package_hash":"${"a".repeat(64)}",
            "variant_id":"v1","points":1,"max_score":2,"closed":true,
            "captured_at":"2026-09-17T12:00:00Z","observations":[]}
        """.trimIndent().replace("\n", "")

        val dto = Json.decodeFromString(ResultSubmissionDto.serializer(), corpoAntigo)

        assertFalse(dto.partial, "um corpo sem o campo precisa decodificar como nao-parcial")
    }
}
