package com.platos.domain.layout

import com.platos.domain.exam.BlockFormula
import com.platos.domain.exam.ExamDefinition
import com.platos.domain.exam.Question
import com.platos.domain.exam.QuestionAsset
import com.platos.domain.exam.UnsupportedContentException
import com.platos.domain.geometry.Um
import com.platos.domain.text.EmbeddedFont
import com.platos.domain.text.TextMeasurer
import com.platos.domain.text.TextStyle
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Cobre os cenarios de formula em bloco das specs de `layout-engine`.
 *
 * Roda em `commonTest`, entao JVM, Node e Android afirmam o mesmo numero. E o que torna a coluna
 * de evidencia mais forte do que parece: se um alvo arredondasse a grade de outro jeito, a
 * divergencia aparece aqui e nao na folha impressa.
 */
class BlockFormulaTest {

    private val measurer = TextMeasurer(EmbeddedFont.program)
    private val engine = LayoutEngine()

    /** Uma fórmula de altura fora da grade de proposito: 7 mm nao e multiplo de 3 mm. */
    private fun formula(
        reference: String = "f-teste",
        width: Um = Um.mm(40),
        height: Um = Um.mm(7),
    ) = BlockFormula(reference = reference, width = width.raw, height = height.raw)

    private fun questao(
        id: String,
        formula: BlockFormula? = null,
        statement: String = "Enunciado da questao $id, com texto suficiente para ocupar linhas.",
    ) = Question(
        id = id,
        statement = statement,
        options = listOf("primeira", "segunda", "terceira", "quarta"),
        formula = formula,
    )

    private fun prova(vararg questions: Question) = ExamDefinition(
        id = "prova-teste",
        title = "Prova de teste",
        questions = questions.toList(),
    )

    private fun provaDe(count: Int, formulaEm: Set<Int> = emptySet()) = prova(
        *(1..count).map { questao("q$it", if (it in formulaEm) formula() else null) }
            .toTypedArray(),
    )

    private fun imagemDe(map: LayoutMap, questionId: String): DrawImage? =
        map.pages.flatMap { it.primitives }
            .filterIsInstance<DrawImage>()
            .firstOrNull { it.id == "q$questionId-f" }

    // --- Fórmula em bloco é aceita / Fórmula reserva espaço próprio ---

    @Test
    fun `formula em bloco e aceita e o mapa inclui a caixa dela`() {
        val map = engine.layout(provaDe(8, formulaEm = setOf(3)))
        val imagem = assertNotNull(imagemDe(map, "q3"), "o mapa nao trouxe a caixa da formula")
        assertEquals("f-teste", imagem.reference)
        assertEquals(Um.mm(40).raw, imagem.width)
        assertEquals(Um.mm(7).raw, imagem.height)
        assertNull(imagemDe(map, "q2"), "questao sem formula nao pode ganhar caixa")
    }

    @Test
    fun `formula faz o bloco crescer em multiplos da grade`() {
        val builder = QuestionBlockBuilder(measurer)
        val sem = builder.build(questao("q1"), 1)
        val com = builder.build(questao("q1", formula()), 1)

        assertTrue(com.block.height > sem.block.height, "a formula precisa reservar espaco proprio")
        val crescimento = com.block.height - sem.block.height
        assertTrue(
            crescimento.isMultipleOf(LayoutProfile.DEFAULT.grid),
            "o crescimento do bloco saiu fora da grade: $crescimento",
        )
        assertTrue(com.block.height.isMultipleOf(LayoutProfile.DEFAULT.grid))
    }

    // --- Fórmula não é reescalada ---

    @Test
    fun `os dois vaos sao derivados e o de baixo e maior que o de cima`() {
        val style = TextStyle.BODY
        val content = QuestionBlockBuilder(measurer)
            .build(questao("q1", formula(height = Um.mm(7))), 1)
        val formula = assertNotNull(content.formula)

        // Nenhum dos dois e valor proprio: sao multiplos da mesma base, a transicao que a folha
        // ja faz entre o fim do enunciado e a primeira alternativa. Se alguem trocar qualquer um
        // deles por uma constante solta, isto cai.
        val base = style.lineHeight + QuestionBlockBuilder.SPACE_AFTER_STATEMENT
        assertEquals(base, QuestionBlockBuilder.textTransition(style))
        assertEquals((base * 45).divFloor(100), formula.spaceAbove)
        assertEquals((base * 4).divFloor(3), formula.spaceBelow)

        // O ponto semantico da fatia: a formula pertence ao enunciado, entao le colada nele.
        assertTrue(
            formula.spaceBelow > formula.spaceAbove * 2,
            "o vao de baixo precisa ser mais que o dobro do de cima para a proximidade nao mentir",
        )
        // Os dois vaos respondem a criterios diferentes, entao mexer num nao pode mover o outro.
        assertEquals(
            formula.spaceAbove,
            QuestionBlockBuilder.spaceAboveFormula(style),
            "o vao de cima nao pode depender do de baixo",
        )

        assertEquals(Um.mm(7), formula.height, "a altura declarada nao pode ser deformada")
        assertEquals(Um.mm(40), formula.width, "a largura declarada nao pode ser deformada")
    }

    @Test
    fun `a formula nao arredonda a grade, quem arredonda e o bloco`() {
        // Arredondar tambem a formula era redundante — o bloco ja cai na grade — e o residuo desse
        // arredondamento caia todo abaixo dela, fazendo o vao inferior variar com a altura.
        val alturas = listOf(Um.mm(7), Um(7_100), Um(8_999), Um.mm(12))
        val vaos = alturas.map { altura ->
            val content = QuestionBlockBuilder(measurer).build(questao("q1", formula(height = altura)), 1)
            val formula = assertNotNull(content.formula)
            assertTrue(content.block.height.isMultipleOf(LayoutProfile.DEFAULT.grid), "o bloco precisa cair na grade")
            formula.spaceAbove to formula.spaceBelow
        }
        assertEquals(1, vaos.toSet().size, "os vaos nao podem variar com a altura da formula")
    }

    @Test
    fun `a caixa desenhada tem exatamente as dimensoes declaradas`() {
        val map = engine.layout(provaDe(6, formulaEm = setOf(1)))
        val imagem = assertNotNull(imagemDe(map, "q1"))
        // Se o engine reescalasse para caber na grade, estes dois numeros mudariam.
        assertEquals(Um.mm(40).raw, imagem.width)
        assertEquals(Um.mm(7).raw, imagem.height)
    }

    // --- Fórmula mais larga que a coluna ---

    @Test
    fun `formula mais larga que a coluna impede a emissao do mapa`() {
        val larga = formula(width = QuestionBlockBuilder.textWidth(LayoutProfile.DEFAULT) + Um(1))
        val erro = assertFailsWith<LayoutException> {
            engine.layout(provaDe(4).let { prova ->
                prova.copy(
                    questions = prova.questions.mapIndexed { index, q ->
                        if (index == 0) q.copy(formula = larga) else q
                    },
                )
            })
        }
        assertContains(erro.message!!, "f-teste")
        assertContains(erro.message!!, "nao cabe na coluna")
    }

    @Test
    fun `formula com exatamente a largura da coluna e aceita`() {
        val justa = formula(width = QuestionBlockBuilder.textWidth(LayoutProfile.DEFAULT))
        val map = engine.layout(
            provaDe(4).let { prova ->
                prova.copy(
                    questions = prova.questions.mapIndexed { index, q ->
                        if (index == 0) q.copy(formula = justa) else q
                    },
                )
            },
        )
        val imagem = assertNotNull(imagemDe(map, "q1"))
        assertEquals(QuestionBlockBuilder.textWidth(LayoutProfile.DEFAULT).raw, imagem.width)
    }

    // --- Layout não depende do conteúdo matemático ---

    @Test
    fun `formulas de conteudos diferentes e dimensoes iguais dao blocos iguais`() {
        val builder = QuestionBlockBuilder(measurer)
        val bhaskara = builder.build(questao("q1", formula(reference = "f-bhaskara")), 1)
        val matriz = builder.build(questao("q1", formula(reference = "f-matriz3")), 1)

        assertEquals(bhaskara.block.height, matriz.block.height)
        assertEquals(bhaskara.formula!!.spaceAbove, matriz.formula!!.spaceAbove)
        assertEquals(bhaskara.formula!!.spaceBelow, matriz.formula!!.spaceBelow)
    }

    @Test
    fun `trocar so a referencia move apenas a referencia no mapa`() {
        fun mapaCom(reference: String) = engine.layout(
            provaDe(6).let { prova ->
                prova.copy(
                    questions = prova.questions.mapIndexed { index, q ->
                        if (index == 2) q.copy(formula = formula(reference = reference)) else q
                    },
                )
            },
        )

        val a = mapaCom("f-bhaskara")
        val b = mapaCom("f-matriz3")
        // O unico byte que pode mudar e a referencia; posicao e dimensao sao funcao das dimensoes
        // declaradas, e elas sao iguais nos dois.
        assertEquals(
            a.toCanonicalJson().replace("f-bhaskara", "REF"),
            b.toCanonicalJson().replace("f-matriz3", "REF"),
        )
    }

    // --- Ordem dentro do bloco ---

    @Test
    fun `formula fica abaixo do enunciado e acima da primeira alternativa`() {
        val map = engine.layout(provaDe(6, formulaEm = setOf(2)))
        val primitivas = map.pages.flatMap { it.primitives }
        val imagem = assertNotNull(imagemDe(map, "q2"))

        val enunciado = primitivas.filterIsInstance<DrawText>()
            .filter { it.id.startsWith("qq2-s") }
        val alternativas = primitivas.filterIsInstance<DrawText>()
            .filter { it.id.startsWith("qq2-o") }

        val ultimaLinha = enunciado.maxOf { it.baseline }
        val primeiraAlternativa = alternativas.minOf { it.baseline }

        assertTrue(
            imagem.y >= ultimaLinha,
            "a formula (y=${imagem.y}) comecou acima da ultima linha do enunciado ($ultimaLinha)",
        )
        assertTrue(
            imagem.y + imagem.height <= primeiraAlternativa,
            "a formula terminou depois da primeira alternativa ($primeiraAlternativa)",
        )
    }

    // --- Fórmula não se separa do enunciado ---

    @Test
    fun `enunciado formula e alternativas ficam na mesma coluna e pagina`() {
        // Formula alta em toda questao, para forcar varias quebras de coluna.
        val alta = formula(height = Um.mm(30))
        val prova = prova(
            *(1..24).map { questao("q$it", alta) }.toTypedArray(),
        )
        val map = engine.layout(prova)

        for (questao in prova.questions) {
            val pagina = map.pages.firstOrNull { pagina ->
                pagina.primitives.any { it.id == "q${questao.id}-f" }
            }
            assertNotNull(pagina, "a formula de `${questao.id}` sumiu do mapa")

            val naMesmaPagina = pagina.primitives.filter {
                it.id.startsWith("q${questao.id}-")
            }
            assertTrue(
                naMesmaPagina.any { it.id == "q${questao.id}-s0" },
                "a formula de `${questao.id}` ficou em pagina diferente do enunciado",
            )
            assertTrue(
                naMesmaPagina.any { it.id == "q${questao.id}-oA" },
                "a formula de `${questao.id}` ficou em pagina diferente das alternativas",
            )

            // Mesma coluna: enunciado, formula e alternativas partilham a coordenada horizontal
            // do texto, e nenhum bloco e partido entre colunas.
            val x = naMesmaPagina.filterIsInstance<DrawText>()
                .filter { it.id == "q${questao.id}-s0" }
                .map { it.x }
            val imagem = naMesmaPagina.filterIsInstance<DrawImage>().single()
            assertEquals(x.single(), imagem.x, "a formula de `${questao.id}` mudou de coluna")
        }
    }

    // --- Entrada não suportada ---

    @Test
    fun `formula em linha continua recusada com mensagem propria`() {
        val comInline = prova(
            questao("q1").copy(
                assets = listOf(QuestionAsset(kind = "inline_formula", reference = "f-x")),
            ),
            questao("q2"),
        )
        val erro = assertFailsWith<UnsupportedContentException> { engine.layout(comInline) }
        assertContains(erro.message!!, "formula em linha")
        assertContains(erro.message!!, "q1")
    }

    @Test
    fun `imagem de enunciado continua recusada`() {
        val comImagem = prova(
            questao("q1").copy(
                assets = listOf(QuestionAsset(kind = "image", reference = "img-1")),
            ),
            questao("q2"),
        )
        val erro = assertFailsWith<UnsupportedContentException> { engine.layout(comImagem) }
        assertContains(erro.message!!, "q1")
    }

    @Test
    fun `formula sem referencia e recusada`() {
        val erro = assertFailsWith<UnsupportedContentException> {
            engine.layout(prova(questao("q1", formula(reference = "")), questao("q2")))
        }
        assertContains(erro.message!!, "sem referencia")
    }

    @Test
    fun `formula com dimensao nao positiva e recusada`() {
        for (invalida in listOf(formula(width = Um.ZERO), formula(height = Um.ZERO))) {
            val erro = assertFailsWith<UnsupportedContentException> {
                engine.layout(prova(questao("q1", invalida), questao("q2")))
            }
            assertContains(erro.message!!, "nao positiva")
        }
        // Negativo tambem: uma caixa de area negativa nao pode virar caixa de area zero em
        // silencio.
        val negativa = BlockFormula(reference = "f-teste", width = -1, height = Um.mm(7).raw)
        assertFailsWith<UnsupportedContentException> {
            engine.layout(prova(questao("q1", negativa), questao("q2")))
        }
    }

    @Test
    fun `mapa com formula continua valido`() {
        val map = engine.layout(provaDe(10, formulaEm = setOf(1, 4, 9)))
        assertEquals(ValidationResult.Valid, map.validate())
    }
}
