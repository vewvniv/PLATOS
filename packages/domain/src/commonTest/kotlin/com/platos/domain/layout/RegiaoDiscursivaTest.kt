package com.platos.domain.layout

import com.platos.domain.exam.ExamDefinition
import com.platos.domain.exam.Question
import com.platos.domain.exam.QuestionKind
import com.platos.domain.exam.Rubric
import com.platos.domain.exam.RubricCriterion
import com.platos.domain.exam.RubricDescriptor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A folha com discursiva (`slice-5a-regiao-discursiva`): o gabarito, o bloco e a regiao discursivos.
 *
 * Cada cenario daqui corresponde a um cenario das specs de `layout-engine` da mudanca, e o nome diz
 * qual.
 */
class RegiaoDiscursivaTest {

    private val engine = LayoutEngine()

    private fun objetiva(id: String) = Question(
        id = id,
        statement = "Enunciado da questao $id, com texto suficiente para ocupar linhas.",
        options = listOf("primeira", "segunda", "terceira", "quarta"),
    )

    private fun discursiva(id: String, vararg linhas: Int = intArrayOf(3, 2)) = Question(
        id = id,
        kind = QuestionKind.ESSAY,
        statement = "Explique, com as suas palavras, o raciocinio da questao $id.",
        points = linhas.size,
        rubric = Rubric(
            linhas.mapIndexed { i, n ->
                RubricCriterion(
                    id = "c${i + 1}",
                    description = "Criterio ${i + 1}",
                    points = 1,
                    expectedLines = n,
                    descriptors = listOf(RubricDescriptor(1, "atende"), RubricDescriptor(0, "nao atende")),
                )
            },
        ),
    )

    private fun prova(vararg questoes: Question) =
        ExamDefinition(id = "prova-discursiva-teste", title = "Prova", questions = questoes.toList())

    // --- 3.2: o gabarito so com objetivas ---

    /** Cenario "Gabarito so com objetivas", e a decisao 7 do design: numero da questao na prova. */
    @Test
    fun `o gabarito tem so as objetivas, numeradas pela posicao na prova`() {
        val map = engine.layout(
            prova(objetiva("q1"), objetiva("q2"), discursiva("q3"), objetiva("q4"), objetiva("q5")),
        )
        val gabarito = map.regions.single { it.index == 0 }

        assertEquals(
            setOf("q1", "q2", "q4", "q5"),
            gabarito.bubbles.map { it.questionId }.toSet(),
            "o gabarito tem bolha de discursiva, ou perdeu uma objetiva",
        )

        // O numero impresso ao lado de cada linha do gabarito e o da questao na prova.
        val numeros = map.pages[0].primitives
            .filterIsInstance<DrawText>()
            .filter { it.id.startsWith("r0-n") }
            .associate { it.id.removePrefix("r0-n") to it.text }
        assertEquals(mapOf("q1" to "1", "q2" to "2", "q4" to "4", "q5" to "5"), numeros)
    }

    @Test
    fun `sem discursiva o gabarito continua numerando em sequencia`() {
        // Guarda do caminho de sempre: a prova so objetiva nao pode ter mudado de numeracao.
        val map = engine.layout(prova(objetiva("q1"), objetiva("q2"), objetiva("q3")))
        val numeros = map.pages[0].primitives
            .filterIsInstance<DrawText>()
            .filter { it.id.startsWith("r0-n") }
            .map { it.text }
        assertEquals(listOf("1", "2", "3"), numeros)
        assertTrue(map.regions.single().bubbles.isNotEmpty())
    }
}
