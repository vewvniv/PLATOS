package com.platos.domain.capture

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ArucoDictionaryTest {

    private fun render(id: Int): List<String> =
        ArucoDictionary.modulesOf(id).map { row -> row.joinToString("") { if (it) "#" else "." } }

    @Test
    fun `marcador tem sete modulos de lado com borda preta`() {
        val marker = ArucoDictionary.modulesOf(0)
        assertEquals(7, marker.size)
        assertTrue(marker.all { it.size == 7 })

        val rendered = render(0)
        assertEquals("#######", rendered.first())
        assertEquals("#######", rendered.last())
        assertTrue(rendered.all { it.first() == '#' && it.last() == '#' })
    }

    @Test
    fun `dados do marcador zero batem com o dicionario do OpenCV`() {
        // Bytes de origem no OpenCV: {162, 217, 94, 0}. Como 162 e 0b10100010, os cinco primeiros
        // bits de dados sao 1,0,1,0,0 — e bit ligado e modulo *branco*, entao a primeira linha de
        // dados desenhada e `.#.##`. A borda preta de um modulo envolve as cinco linhas.
        assertEquals(
            listOf(
                "#######",
                "#.#.###",
                "##.#..#",
                "##..###",
                "#.#.#.#",
                "#...###",
                "#######",
            ),
            render(0),
        )
    }

    @Test
    fun `todos os cem marcadores existem e sao distintos`() {
        val seen = mutableSetOf<List<String>>()
        for (id in 0 until ArucoDictionary.SIZE) {
            val marker = render(id)
            assertEquals(7, marker.size, "marcador $id malformado")
            assertTrue(seen.add(marker), "marcador $id repete um padrao anterior")
        }
        assertEquals(ArucoDictionary.SIZE, seen.size)
    }

    @Test
    fun `identificador fora do dicionario e recusado`() {
        assertFailsWith<IllegalArgumentException> { ArucoDictionary.modulesOf(-1) }
        assertFailsWith<IllegalArgumentException> { ArucoDictionary.modulesOf(100) }
    }
}
