package com.platos.domain.transport

import com.platos.domain.capture.QuestionAnswer
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Os quatro pares, afirmados literalmente.
 *
 * **O literal e escrito a mao aqui, e nao derivado do `when` que ele julga.** Afirmar
 * `Marcada.answerKind() == AnswerKind.MARCADA` poria o mesmo codigo dos dois lados da igualdade e
 * aprovaria qualquer valor que a constante viesse a ter. E a mesma razao pela qual
 * `ResultadoDtoTest` prende o JSON literal em vez do objeto (P4).
 *
 * E e este arquivo que `tools/parity/answer-kind.mjs` compara com o `check` da migration: o valor
 * que o conferidor le do dominio e o que esta declarado em `AnswerKind`, e o que prende `AnswerKind`
 * a semantica de `QuestionAnswer` e o mapa afirmado abaixo.
 */
class AnswerKindTest {

    @Test
    fun `o tipo da resposta e o que o check da migration admite`() {
        assertEquals("marcada", QuestionAnswer.Marcada("q01", "A").answerKind())
        assertEquals("em_branco", QuestionAnswer.EmBranco("q01").answerKind())
        assertEquals(
            "multipla_marcacao",
            QuestionAnswer.MultiplaMarcacao("q01", listOf("A", "C")).answerKind(),
        )
        assertEquals("indecisa", QuestionAnswer.Indecisa("q01", listOf("A", "B")).answerKind())
    }

    @Test
    fun `os quatro valores estao declarados, e sao quatro`() {
        assertEquals(
            listOf("marcada", "em_branco", "multipla_marcacao", "indecisa"),
            AnswerKind.TODOS,
            "a ordem e a do check da migration, que e a ordem que o conferidor compara",
        )
    }

    /**
     * Todas as envolvidas, e nunca a "vencedora": desempatar transformaria rasura em resposta.
     */
    @Test
    fun `as alternativas sao todas as envolvidas`() {
        assertEquals(listOf("A"), QuestionAnswer.Marcada("q01", "A").answerOptions())
        assertEquals(emptyList(), QuestionAnswer.EmBranco("q01").answerOptions())
        assertEquals(
            listOf("A", "C"),
            QuestionAnswer.MultiplaMarcacao("q01", listOf("A", "C")).answerOptions(),
        )
        assertEquals(
            listOf("A", "B"),
            QuestionAnswer.Indecisa("q01", listOf("A", "B")).answerOptions(),
        )
    }
}
