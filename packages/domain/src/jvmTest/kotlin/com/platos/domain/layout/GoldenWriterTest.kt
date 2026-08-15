package com.platos.domain.layout

import com.platos.domain.exam.ExamDefinition
import com.platos.domain.fixtures.Fixtures
import kotlinx.serialization.json.Json
import java.io.File
import kotlin.test.Test

/**
 * Regrava o golden do `LayoutMap` quando pedido explicitamente:
 * `./gradlew :packages:domain:jvmTest -Dplatos.golden.write=true`.
 *
 * Fica atras de uma flag de proposito. Um golden que se regrava sozinho nao detecta nada — ele
 * simplesmente concorda com o que quer que o engine tenha produzido hoje, que e o oposto do que
 * D-1.9 quer.
 */
class GoldenWriterTest {

    @Test
    fun `regrava o golden apenas quando solicitado`() {
        if (System.getProperty("platos.golden.write") != "true") return
        val destination = File(System.getProperty("platos.golden.path"))

        val exam = Json { ignoreUnknownKeys = false }
            .decodeFromString(ExamDefinition.serializer(), Fixtures.PROVA_REFERENCIA_JSON)
        val map = LayoutEngine().layout(exam)

        destination.writeText(map.toCanonicalJson())
        println("golden regravado: ${destination.absolutePath} (${destination.length()} bytes)")
    }
}
