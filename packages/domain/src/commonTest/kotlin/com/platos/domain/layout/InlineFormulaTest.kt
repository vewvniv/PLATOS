package com.platos.domain.layout

import com.platos.domain.exam.ExamDefinition
import com.platos.domain.exam.InlineFormula
import com.platos.domain.exam.Question
import com.platos.domain.geometry.Um
import com.platos.domain.text.EmbeddedFont
import com.platos.domain.text.LineRun
import com.platos.domain.text.TextMeasurer
import com.platos.domain.text.TextPiece
import com.platos.domain.text.TextStyle
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Cobre os cenarios de formula **em linha** das specs de `layout-engine`.
 *
 * Roda em `commonTest`: JVM, Node e Android afirmam a mesma quebra de linha. E o codigo cuja
 * identidade entre alvos a fatia 1 comprou, e esta fatia mexe dentro dele.
 */
class InlineFormulaTest {

    private val measurer = TextMeasurer(EmbeddedFont.program)
    private val style = TextStyle.BODY
    private val profile = LayoutProfile.DEFAULT
    private val engine = LayoutEngine()

    private fun caixa(
        reference: String = "f-eq1",
        width: Um = Um.mm(12),
        height: Um = Um.mm(5),
        baselineOffset: Um = Um.mm(1),
    ) = TextPiece.Box(reference, width, height, baselineOffset)

    private fun formula(
        reference: String = "f-eq1",
        width: Int = 12_000,
        height: Int = 5_000,
        baselineOffset: Int = 1_000,
    ) = InlineFormula(reference, width, height, baselineOffset)

    private fun questao(
        id: String,
        statement: String,
        inline: List<InlineFormula> = emptyList(),
    ) = Question(
        id = id,
        statement = statement,
        options = listOf("primeira", "segunda", "terceira", "quarta"),
        inline = inline,
    )

    private fun prova(vararg questions: Question) =
        ExamDefinition(id = "prova-teste", title = "Prova", questions = questions.toList())

    // --- Fórmula em linha ocupa espaço no meio do texto ---

    @Test
    fun `a caixa ocupa largura entre as palavras vizinhas`() {
        val semCaixa = measurer.measure(
            listOf(TextPiece.Words("Qual e o valor de x?")),
            style,
            Um.mm(200),
        )
        val comCaixa = measurer.measure(
            listOf(TextPiece.Words("Qual e o valor de "), caixa(), TextPiece.Words(" x?")),
            style,
            Um.mm(200),
        )

        assertEquals(1, comCaixa.lines.size)
        assertTrue(
            comCaixa.widest > semCaixa.widest,
            "a caixa precisa somar largura: ${comCaixa.widest} contra ${semCaixa.widest}",
        )
    }

    @Test
    fun `a quebra de linha considera a largura da caixa`() {
        val pedacos = listOf(TextPiece.Words("Calcule"), caixa(width = Um.mm(60)), TextPiece.Words("agora"))
        val estreito = measurer.measure(pedacos, style, Um.mm(70))
        val largo = measurer.measure(pedacos, style, Um.mm(200))

        assertTrue(estreito.lines.size > largo.lines.size, "a caixa precisa forcar quebra")
        assertEquals(1, largo.lines.size)
    }

    // --- Espaço na fronteira entre texto e caixa ---

    @Test
    fun `o espaco antes e depois da caixa sobrevive`() {
        // Achado na folha IMPRESSA: saia "Quanto vale12 + 15ao todo?", com texto e formula colados.
        // A causa era `split(' ')` comendo o espaco da fronteira — dentro do trecho os espacos
        // voltam porque as palavras sao rejuntadas com " ", mas na borda com a caixa nao ha juncao.
        //
        // Nenhum teste pegava isso porque todos mediam largura, altura e quebra, e o espaco de
        // fronteira nao muda nenhuma das tres o bastante para reprovar. Este afirma a posicao.
        val espaco = measurer.width(" ", style)
        val medido = measurer.measure(
            listOf(TextPiece.Words("Quanto vale "), caixa(), TextPiece.Words(" ao todo?")),
            style,
            Um.mm(200),
        )

        val linha = medido.lines.single()
        val antes = linha.runs[0] as LineRun.Text
        val box = linha.runs[1] as LineRun.Box
        val depois = linha.runs[2] as LineRun.Text

        assertEquals("Quanto vale", antes.text, "o espaco nao pode ficar dentro do texto desenhado")
        assertEquals("ao todo?", depois.text)
        assertEquals(
            antes.x + antes.width + espaco,
            box.x,
            "falta o espaco entre o texto e a formula",
        )
        assertEquals(
            box.x + box.width + espaco,
            depois.x,
            "falta o espaco entre a formula e o texto seguinte",
        )
    }

    @Test
    fun `sem espaco no enunciado a caixa encosta no texto`() {
        // O par que impede o conserto de virar "sempre poe espaco": quando o enunciado NAO tem
        // espaco, a caixa continua encostada. Sem isto, `(x{{f}})` ganharia espaco que ninguem pediu.
        val medido = measurer.measure(
            listOf(TextPiece.Words("valor("), caixa(), TextPiece.Words(")")),
            style,
            Um.mm(200),
        )
        val linha = medido.lines.single()
        val antes = linha.runs[0] as LineRun.Text
        val box = linha.runs[1] as LineRun.Box
        val depois = linha.runs[2] as LineRun.Text
        assertEquals(antes.x + antes.width, box.x)
        assertEquals(box.x + box.width, depois.x)
    }

    // --- Fórmula não é partida entre linhas ---

    @Test
    fun `a caixa nunca aparece em duas linhas`() {
        val medido = measurer.measure(
            listOf(
                TextPiece.Words("Texto que ocupa quase a linha inteira aqui"),
                caixa(width = Um.mm(40)),
                TextPiece.Words("e segue depois"),
            ),
            style,
            Um.mm(70),
        )

        val ocorrencias = medido.lines.sumOf { linha ->
            linha.runs.count { it is LineRun.Box && it.reference == "f-eq1" }
        }
        assertEquals(1, ocorrencias, "a caixa e indivisivel e aparece uma vez so")

        val linhaDaCaixa = medido.lines.single { linha -> linha.runs.any { it is LineRun.Box } }
        val box = linhaDaCaixa.runs.filterIsInstance<LineRun.Box>().single()
        assertEquals(Um.mm(40), box.width, "a caixa precisa manter a largura declarada")
    }

    // --- Alinhamento à linha de base ---

    @Test
    fun `o deslocamento declarado decide o quanto a caixa desce`() {
        val semDescida = measurer.measure(listOf(caixa(baselineOffset = Um.ZERO)), style, Um.mm(200))
        val comDescida = measurer.measure(listOf(caixa(baselineOffset = Um.mm(2))), style, Um.mm(200))

        assertEquals(Um.ZERO, semDescida.lines.single().descent)
        assertEquals(Um.mm(2), comDescida.lines.single().descent)
        
        // Descer a caixa reduz o que ela tem acima da linha de base e aumenta o que tem abaixo:
        // a altura total da caixa nao muda, mas a linha cresce para acomodar a descida.
        assertTrue(comDescida.lines.single().height > semDescida.lines.single().height)
    }

    // --- Linha com fórmula cresce; as demais mantêm a entrelinha ---

    @Test
    fun `so a linha da formula cresce`() {
        val medido = measurer.measure(
            listOf(
                TextPiece.Words("Primeira linha ocupando bastante espaco nesta largura estreita"),
                caixa(height = Um.mm(12), baselineOffset = Um.mm(4)),
            ),
            style,
            Um.mm(60),
        )

        val comCaixa = medido.lines.single { linha -> linha.runs.any { it is LineRun.Box } }
        val semCaixa = medido.lines.filter { linha -> linha.runs.none { it is LineRun.Box } }

        assertTrue(semCaixa.isNotEmpty(), "o caso precisa de linha sem formula para comparar")
        for (linha in semCaixa) {
            assertEquals(style.lineHeight, linha.height, "linha sem formula nao pode mudar de ritmo")
        }
        assertTrue(
            comCaixa.height > style.lineHeight,
            "a linha da formula precisa crescer: ${comCaixa.height}",
        )
    }

    @Test
    fun `altura do paragrafo e a soma das alturas das linhas`() {
        val medido = measurer.measure(
            listOf(
                TextPiece.Words("Uma linha de texto comum aqui para ocupar espaco"),
                caixa(height = Um.mm(12), baselineOffset = Um.mm(4)),
            ),
            style,
            Um.mm(60),
        )
        assertEquals(medido.lines.fold(Um.ZERO) { t, l -> t + l.height }, medido.height)
        assertTrue(
            medido.height > style.lineHeight * medido.lines.size,
            "entrelinha x numero de linhas subestimaria o paragrafo com formula",
        )
    }

    @Test
    fun `texto sem formula mede exatamente entrelinha vezes linhas`() {
        // A garantia que mantem o golden: sem caixa, a soma volta a ser a multiplicacao.
        val medido = measurer.measure(
            listOf(TextPiece.Words("Um texto comum que ocupa mais de uma linha nesta largura")),
            style,
            Um.mm(40),
        )
        assertTrue(medido.lines.size > 1)
        assertEquals(style.lineHeight * medido.lines.size, medido.height)
    }

    // --- Fórmula em linha alta demais ---

    @Test
    fun `formula em linha acima do teto impede a emissao do mapa`() {
        val alta = formula(height = profile.inlineHeightCeiling.raw + 1, baselineOffset = 0)
        val erro = assertFailsWith<LayoutException> {
            engine.layout(prova(questao("q1", "Considere {{f-eq1}} e responda.", listOf(alta))))
        }
        assertContains(erro.message!!, "f-eq1")
        assertContains(erro.message!!, "teto")
        assertContains(erro.message!!, "bloco")
    }

    @Test
    fun `formula com exatamente a altura do teto e aceita`() {
        // Sem esta borda, um `>=` no lugar do `>` recusaria o caso limite e ninguem notaria.
        val justa = formula(height = profile.inlineHeightCeiling.raw, baselineOffset = 0)
        engine.layout(prova(questao("q1", "Considere {{f-eq1}} e responda.", listOf(justa))))
    }

    // --- Layout não depende do conteúdo matemático em linha ---

    @Test
    fun `formulas de referencias diferentes e dimensoes iguais dao a mesma geometria`() {
        fun mapaCom(reference: String) = engine.layout(
            prova(
                questao(
                    "q1",
                    "Considere {{$reference}} e responda.",
                    listOf(formula(reference = reference)),
                ),
            ),
        ).toCanonicalJson()

        val a = mapaCom("f-alfa")
        val b = mapaCom("f-beta")
        assertEquals(a.replace("f-alfa", "REF"), b.replace("f-beta", "REF"))
    }

    // --- Grade continua no bloco ---

    @Test
    fun `o bloco continua caindo na grade mesmo com linha alta`() {
        val alta = formula(height = 9_100, baselineOffset = 3_100)
        val content = QuestionBlockBuilder(measurer, profile).build(
            questao("q1", "Considere {{f-eq1}} e responda com atencao.", listOf(alta)),
            1,
        )
        assertTrue(content.block.height.isMultipleOf(profile.grid))
        val linhaAlta = content.statement.lines.single { l -> l.runs.any { it is LineRun.Box } }
        assertTrue(
            !linhaAlta.height.isMultipleOf(profile.grid),
            "a linha nao deve ser arredondada; quem arredonda e o bloco",
        )
    }

    @Test
    fun `o mapa declara a caixa da formula em linha`() {
        val mapa = engine.layout(
            prova(questao("q1", "Considere {{f-eq1}} e responda.", listOf(formula()))),
        )
        val imagem = mapa.pages.flatMap { it.primitives }
            .filterIsInstance<DrawImage>()
            .singleOrNull { it.reference == "f-eq1" }
        assertNotNull(imagem, "a formula em linha precisa aparecer como imagem no mapa")
        assertEquals(12_000, imagem.width)
        assertEquals(5_000, imagem.height)
    }
}
