package com.platos.domain.exam

import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ExamDefinitionTest {

    private fun objetiva(id: String) = Question(
        id = id,
        statement = "Qual e o resultado de 2 + 2?",
        options = listOf("3", "4", "5"),
    )

    private fun prova(vararg questions: Question) =
        ExamDefinition(id = "prova-1", title = "Prova", questions = questions.toList())

    @Test
    fun `prova objetiva bem formada e aceita`() {
        prova(objetiva("q1"), objetiva("q2")).requireSupported()
    }

    @Test
    fun `questao discursiva e recusada com erro identificavel`() {
        val falha = assertFailsWith<UnsupportedContentException> {
            prova(objetiva("q1"), objetiva("q2").copy(id = "q2", kind = QuestionKind.ESSAY))
                .requireSupported()
        }
        assertTrue(falha.message!!.contains("q2"), falha.message!!)
        assertTrue(falha.message!!.contains("objetivas"), falha.message!!)
    }

    @Test
    fun `recurso nao suportado no enunciado e recusado`() {
        val comImagem = objetiva("q1").copy(
            assets = listOf(QuestionAsset(kind = "image", reference = "grafico.png")),
        )
        val falha = assertFailsWith<UnsupportedContentException> {
            prova(comImagem).requireSupported()
        }
        assertTrue(falha.message!!.contains("image"), falha.message!!)

        val comFormula = objetiva("q1").copy(
            assets = listOf(QuestionAsset(kind = "formula", reference = "x^2")),
        )
        val outraFalha = assertFailsWith<UnsupportedContentException> {
            prova(comFormula).requireSupported()
        }
        assertTrue(outraFalha.message!!.contains("formula"), outraFalha.message!!)
    }

    @Test
    fun `identificador de questao repetido e recusado`() {
        val falha = assertFailsWith<UnsupportedContentException> {
            prova(objetiva("q1"), objetiva("q1")).requireSupported()
        }
        assertTrue(falha.message!!.contains("q1"), falha.message!!)
    }

    @Test
    fun `numero de alternativas fora da faixa e recusado`() {
        assertFailsWith<UnsupportedContentException> {
            prova(objetiva("q1").copy(options = listOf("unica"))).requireSupported()
        }
        assertFailsWith<UnsupportedContentException> {
            prova(objetiva("q1").copy(options = List(6) { "op$it" })).requireSupported()
        }
    }

    @Test
    fun `enunciado vazio e recusado`() {
        assertFailsWith<UnsupportedContentException> {
            prova(objetiva("q1").copy(statement = "   ")).requireSupported()
        }
    }

    @Test
    fun `prova sem questoes e recusada`() {
        assertFailsWith<UnsupportedContentException> { prova().requireSupported() }
    }
}
