package com.platos.domain.layout

import com.platos.domain.exam.ExamDefinition
import com.platos.domain.fixtures.Fixtures
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Cobre "`LayoutMap` deterministico e versionado" e a paridade de calculo entre alvos.
 *
 * Roda em `commonTest`, entao JVM, Node e Android executam esta mesma comparacao contra o *mesmo*
 * golden versionado. Se algum alvo calculasse um micrometro diferente — por medicao de texto, por
 * arredondamento, por ordem de iteracao — a igualdade byte a byte quebraria naquele alvo e so
 * nele. E esta a forma concreta da garantia que a fatia inteira existe para dar.
 *
 * Para regravar o golden depois de uma mudanca deliberada de geometria:
 * `./gradlew :packages:domain:jvmTest -Dplatos.golden.write=true`
 */
class GoldenLayoutTest {

    private val exam: ExamDefinition = Json.decodeFromString(
        ExamDefinition.serializer(),
        Fixtures.PROVA_REFERENCIA_JSON,
    )

    @Test
    fun `mapa da fixture bate byte a byte com o golden`() {
        val produced = LayoutEngine().layout(exam).toCanonicalJson()
        val golden = Fixtures.PROVA_REFERENCIA_LAYOUT_JSON.trim()

        if (produced != golden) {
            val position = produced.zip(golden).indexOfFirst { (a, b) -> a != b }
            val at = if (position < 0) minOf(produced.length, golden.length) else position
            val window = 80
            fail@ run {
                assertEquals(
                    golden.substring(maxOf(0, at - window), minOf(golden.length, at + window)),
                    produced.substring(maxOf(0, at - window), minOf(produced.length, at + window)),
                    "o mapa divergiu do golden a partir do caractere $at",
                )
            }
        }
        assertEquals(golden, produced)
    }

    @Test
    fun `golden e um mapa valido`() {
        val map = LayoutEngine().layout(exam)
        assertEquals(ValidationResult.Valid, map.validate())
    }

    @Test
    fun `fixture de referencia atravessa mais de uma pagina`() {
        val map = LayoutEngine().layout(exam)
        assertTrue(map.pages.size > 1, "a fixture precisa exercitar a paginacao")
        assertEquals(28, exam.questions.size)
        assertEquals(28 * 4, map.regions.single().bubbles.size)
    }

    @Test
    fun `recalcular a fixture da o mesmo mapa`() {
        assertEquals(
            LayoutEngine().layout(exam).toCanonicalJson(),
            LayoutEngine().layout(exam).toCanonicalJson(),
        )
    }
}
