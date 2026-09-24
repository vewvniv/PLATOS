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

    /**
     * A prova com discursiva (`slice-5a-regiao-discursiva`, tarefa 3.5): o mesmo byte a byte, nos
     * mesmos tres alvos. E daqui que "calculo em plataformas diferentes da o mesmo mapa" passa a
     * valer tambem para a regiao discursiva — moldura, pauta, QR por regiao e a area de resposta.
     */
    @Test
    fun `mapa da prova com discursiva bate byte a byte com o golden`() {
        val discursiva = Json.decodeFromString(ExamDefinition.serializer(), Fixtures.PROVA_DISCURSIVA_JSON)
        val produced = LayoutEngine().layout(discursiva).toCanonicalJson()
        val golden = Fixtures.PROVA_DISCURSIVA_LAYOUT_JSON.trim()
        val at = produced.zip(golden).indexOfFirst { (a, b) -> a != b }
        assertEquals(golden, produced, "o mapa com discursiva divergiu do golden a partir do caractere $at")
        assertEquals(ValidationResult.Valid, LayoutEngine().layout(discursiva).validate())
        // Guarda de vacuidade: o golden tem de ter as duas regioes discursivas, uma delas fora da
        // pagina 0 — senao "byte a byte" estaria comparando uma prova que nao exercita a regiao.
        val map = LayoutEngine().layout(discursiva)
        assertEquals(listOf(0, 1, 2), map.regions.map { it.index })
        assertTrue(map.regions.any { it.questionId != null && it.page > 0 }, "nenhuma regiao discursiva fora da pagina 0")
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
        assertEquals(40, exam.questions.size)
        assertEquals(40 * 4, map.regions.single().bubbles.size)
    }

    /**
     * A fixture precisa exercitar formula, e nao so texto (D-1.5.6).
     *
     * As doze cobrem a educacao basica inteira: aritmetica, fracao, raiz, potencia com subscrito,
     * trigonometria, logaritmo, vetor com modulo, somatorio, matriz 2x2 e 3x3, e sistema linear.
     * As verticais nao sao enfeite — matriz 3x3 e `cases` sao as formulas mais altas do curriculo,
     * e sao elas que exercitam de verdade o arredondamento a grade.
     */
    @Test
    fun `fixture de referencia traz formula em bloco`() {
        val map = LayoutEngine().layout(exam)
        // Formula em bloco tem identificador `q<questao>-f`; em linha, `q<questao>-si<linha>`.
        // Separar os dois e o que impede este teste de virar uma contagem sem sentido agora que a
        // fixture tem as duas formas.
        val imagens = map.pages.flatMap { it.primitives }
            .filterIsInstance<DrawImage>()
            .filter { it.id.endsWith("-f") }

        assertEquals(12, imagens.size, "a folha de referencia precisa ter matematica")
        assertEquals(
            exam.questions.count { it.formula != null },
            imagens.size,
            "toda questao com formula precisa ter a caixa dela no mapa",
        )
        assertEquals(
            imagens.size,
            imagens.map { it.reference }.distinct().size,
            "as formulas da fixture precisam ser distintas entre si",
        )
        assertTrue(
            imagens.any { it.reference == "f-matriz3" } && imagens.any { it.reference == "f-sistema" },
            "as estruturas verticais mais altas precisam estar na fixture",
        )
    }

    @Test
    fun `fixture de referencia traz formula em linha`() {
        val map = LayoutEngine().layout(exam)
        val emLinha = map.pages.flatMap { it.primitives }
            .filterIsInstance<DrawImage>()
            .filter { it.id.contains("-si") }

        // Sete questoes da fixture descreviam matematica em texto puro — era o caso predominante
        // que a fatia 1.5 nao validava. Nove ocorrencias porque duas questoes citam duas formulas.
        assertEquals(9, emLinha.size, "o caso predominante das exatas precisa estar na folha")
        assertEquals(
            exam.questions.count { it.inline.isNotEmpty() },
            7,
            "as sete questoes que descreviam matematica em texto precisam usar formula em linha",
        )
        assertTrue(
            emLinha.all { it.reference.startsWith("i-") },
            "formula em linha usa as referencias `i-`; em bloco usa `f-`",
        )
    }

    @Test
    fun `recalcular a fixture da o mesmo mapa`() {
        assertEquals(
            LayoutEngine().layout(exam).toCanonicalJson(),
            LayoutEngine().layout(exam).toCanonicalJson(),
        )
    }
}
