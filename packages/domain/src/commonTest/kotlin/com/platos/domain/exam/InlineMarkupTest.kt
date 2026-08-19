package com.platos.domain.exam

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * Cobre o marcador de formula em linha e as recusas de D-1.6.2.
 *
 * Roda em `commonTest`, entao JVM, Node e Android afirmam o mesmo. Um parser que se comportasse
 * diferente entre alvos produziria enunciados diferentes na mesma prova, que e exatamente a
 * divergencia que a fatia 1 comprou o direito de nao ter.
 */
class InlineMarkupTest {

    private fun formula(
        reference: String = "f-eq1",
        width: Int = 12_000,
        height: Int = 5_000,
        baselineOffset: Int = 1_000,
    ) = InlineFormula(reference, width, height, baselineOffset)

    private fun questao(
        statement: String,
        inline: Map<String, InlineFormula> = emptyMap(),
    ) = Question(
        id = "q1",
        statement = statement,
        options = listOf("primeira", "segunda"),
        inline = inline,
    )

    // --- gramática ---

    @Test
    fun `marcador separa texto de referencia`() {
        val segmentos = parseStatement("Qual e o valor de {{f-eq1}} quando y = 2?")
        assertEquals(
            listOf(
                StatementSegment.Text("Qual e o valor de "),
                StatementSegment.Formula("f-eq1"),
                StatementSegment.Text(" quando y = 2?"),
            ),
            segmentos,
        )
    }

    @Test
    fun `marcador no inicio e no fim do enunciado`() {
        assertEquals(
            listOf(StatementSegment.Formula("f-a"), StatementSegment.Text(" e o resultado.")),
            parseStatement("{{f-a}} e o resultado."),
        )
        assertEquals(
            listOf(StatementSegment.Text("O resultado e "), StatementSegment.Formula("f-b")),
            parseStatement("O resultado e {{f-b}}"),
        )
    }

    @Test
    fun `dois marcadores no mesmo enunciado`() {
        assertEquals(listOf("f-a", "f-b"), referencedInlineFormulas("Compare {{f-a}} com {{f-b}}."))
    }

    @Test
    fun `a mesma formula citada duas vezes aparece duas vezes`() {
        // O mapa tem um recurso; o enunciado tem duas ocorrencias. As duas precisam ser desenhadas.
        assertEquals(listOf("f-a", "f-a"), referencedInlineFormulas("De {{f-a}} conclui-se {{f-a}}."))
    }

    @Test
    fun `enunciado sem marcador e um unico trecho de texto`() {
        assertEquals(
            listOf(StatementSegment.Text("Qual e o minimo multiplo comum entre 4 e 6?")),
            parseStatement("Qual e o minimo multiplo comum entre 4 e 6?"),
        )
    }

    // --- o risco residual registrado no design: chave como texto legítimo ---

    @Test
    fun `chave escapada vira texto e nao marcador`() {
        val segmentos = parseStatement("Em Kotlin, \\{{ abre um bloco de template.")
        assertEquals(listOf(StatementSegment.Text("Em Kotlin, {{ abre um bloco de template.")), segmentos)
        assertEquals(emptyList(), referencedInlineFormulas("Escreva \\{{x}} para interpolar."))
    }

    @Test
    fun `barra fora do escape continua sendo texto`() {
        assertEquals(
            listOf(StatementSegment.Text("A divisao 6\\2 nao existe assim.")),
            parseStatement("A divisao 6\\2 nao existe assim."),
        )
    }

    // --- recusas de gramática ---

    @Test
    fun `chave nao fechada e recusada`() {
        val erro = assertFailsWith<StatementMarkupException> {
            parseStatement("Qual e o valor de {{f-eq1 quando y = 2?")
        }
        assertContains(erro.message!!, "sem `}}`")
    }

    @Test
    fun `chave aninhada e recusada`() {
        assertFailsWith<StatementMarkupException> { parseStatement("Valor de {{f-{{a}}}}.") }
    }

    @Test
    fun `referencia com caractere fora do vocabulario e recusada`() {
        assertFailsWith<StatementMarkupException> { parseStatement("Valor de {{F-EQ1}}.") }
        assertFailsWith<StatementMarkupException> { parseStatement("Valor de {{f eq1}}.") }
        assertFailsWith<StatementMarkupException> { parseStatement("Valor de {{}}.") }
        assertFailsWith<StatementMarkupException> { parseStatement("Valor de {{-a}}.") }
    }

    // --- recusas de resolução ---

    @Test
    fun `referencia nao declarada impede a emissao do mapa`() {
        val erro = assertFailsWith<UnsupportedContentException> {
            questao("Valor de {{f-eq1}}.").requireInlineFormulasResolved()
        }
        assertContains(erro.message!!, "f-eq1")
        assertContains(erro.message!!, "nao declarada")
    }

    @Test
    fun `recurso declarado e nao citado e recusado`() {
        val erro = assertFailsWith<UnsupportedContentException> {
            questao("Sem formula nenhuma.", mapOf("f-eq1" to formula())).requireInlineFormulasResolved()
        }
        assertContains(erro.message!!, "nao cita")
    }

    @Test
    fun `chave do mapa divergente da referencia declarada e recusada`() {
        val erro = assertFailsWith<UnsupportedContentException> {
            questao("Valor de {{f-eq1}}.", mapOf("f-eq1" to formula(reference = "f-outra")))
                .requireInlineFormulasResolved()
        }
        assertContains(erro.message!!, "coincidir")
    }

    @Test
    fun `dimensao nao positiva e recusada`() {
        assertFailsWith<UnsupportedContentException> {
            questao("Valor de {{f-eq1}}.", mapOf("f-eq1" to formula(width = 0)))
                .requireInlineFormulasResolved()
        }
        assertFailsWith<UnsupportedContentException> {
            questao("Valor de {{f-eq1}}.", mapOf("f-eq1" to formula(height = -1)))
                .requireInlineFormulasResolved()
        }
    }

    @Test
    fun `deslocamento fora da caixa e recusado`() {
        val erro = assertFailsWith<UnsupportedContentException> {
            questao("Valor de {{f-eq1}}.", mapOf("f-eq1" to formula(height = 5_000, baselineOffset = 5_001)))
                .requireInlineFormulasResolved()
        }
        assertContains(erro.message!!, "fora da caixa")
        assertFailsWith<UnsupportedContentException> {
            questao("Valor de {{f-eq1}}.", mapOf("f-eq1" to formula(baselineOffset = -1)))
                .requireInlineFormulasResolved()
        }
    }

    @Test
    fun `deslocamento nas bordas exatas e aceito`() {
        // Sem estes dois, um `<` no lugar de `<=` passaria despercebido.
        questao("Valor de {{f-eq1}}.", mapOf("f-eq1" to formula(height = 5_000, baselineOffset = 0)))
            .requireInlineFormulasResolved()
        questao("Valor de {{f-eq1}}.", mapOf("f-eq1" to formula(height = 5_000, baselineOffset = 5_000)))
            .requireInlineFormulasResolved()
    }

    @Test
    fun `questao com formula em linha declarada e citada e aceita`() {
        questao("Valor de {{f-eq1}} para y = 2.", mapOf("f-eq1" to formula()))
            .requireInlineFormulasResolved()
    }
}
