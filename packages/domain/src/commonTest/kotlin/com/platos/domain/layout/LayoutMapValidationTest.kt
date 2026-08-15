package com.platos.domain.layout

import com.platos.domain.exam.ExamDefinition
import com.platos.domain.exam.Question
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LayoutMapValidationTest {

    private val exam = ExamDefinition(
        id = "prova-validacao",
        title = "Prova",
        questions = (1..8).map {
            Question(
                id = "q$it",
                statement = "Enunciado da questao $it com algum texto.",
                options = listOf("a", "b", "c", "d"),
            )
        },
    )

    private val valid = LayoutEngine().layout(exam)

    private fun problemsOf(map: LayoutMap): List<String> {
        val result = map.validate()
        assertTrue(result is ValidationResult.Invalid, "esperava mapa invalido")
        return result.problems
    }

    @Test
    fun `mapa produzido pelo engine e aceito sem efeito colateral`() {
        val antes = valid.toCanonicalJson()
        assertEquals(ValidationResult.Valid, valid.validate())
        assertEquals(antes, valid.toCanonicalJson(), "a validacao alterou o mapa")
    }

    @Test
    fun `identificador de elemento repetido e apontado`() {
        val page = valid.pages.first()
        val duplicated = page.copy(primitives = page.primitives + page.primitives.first())
        val problems = problemsOf(valid.copy(pages = listOf(duplicated) + valid.pages.drop(1)))

        assertTrue(
            problems.any { it.contains("repetido") && it.contains(page.primitives.first().id) },
            problems.toString(),
        )
    }

    @Test
    fun `regioes sobrepostas sao apontadas`() {
        val region = valid.regions.single()
        val overlapping = region.copy(index = 1, markerIds = listOf(4, 5, 6, 7))
        val problems = problemsOf(valid.copy(regions = listOf(region, overlapping)))

        assertTrue(problems.any { it.contains("se sobrepoem") }, problems.toString())
    }

    @Test
    fun `coordenada fora da faixa e apontada`() {
        val region = valid.regions.single()
        val broken = region.copy(
            bubbles = region.bubbles.mapIndexed { index, bubble ->
                if (index == 0) bubble.copy(v = 1_000_001) else bubble
            },
        )
        val problems = problemsOf(valid.copy(regions = listOf(broken)))

        assertTrue(
            problems.any { it.contains("fora do intervalo unitario") && it.contains("1000001") },
            problems.toString(),
        )
    }

    @Test
    fun `versao ausente e apontada`() {
        assertTrue(
            problemsOf(valid.copy(layoutEngineVersion = 0))
                .any { it.contains("layout_engine_version") },
        )
        assertTrue(
            problemsOf(valid.copy(minRendererVersion = 0))
                .any { it.contains("min_renderer_version") },
        )
    }

    @Test
    fun `marcadores fora da convencao 4k sao apontados`() {
        val region = valid.regions.single()
        val problems = problemsOf(
            valid.copy(regions = listOf(region.copy(markerIds = listOf(7, 8, 9, 10)))),
        )
        assertTrue(problems.any { it.contains("deveria usar os marcadores") }, problems.toString())
    }

    @Test
    fun `regiao apontando para pagina inexistente e apontada`() {
        val region = valid.regions.single()
        val problems = problemsOf(valid.copy(regions = listOf(region.copy(page = 99))))
        assertTrue(problems.any { it.contains("que nao existe") }, problems.toString())
    }

    @Test
    fun `todos os problemas sao reportados de uma vez`() {
        val problems = problemsOf(
            valid.copy(layoutEngineVersion = 0, minRendererVersion = 0, fontSha256 = ""),
        )
        assertTrue(problems.size >= 3, "esperava varios problemas, veio $problems")
    }
}
