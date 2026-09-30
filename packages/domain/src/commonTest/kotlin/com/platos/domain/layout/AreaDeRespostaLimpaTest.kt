package com.platos.domain.layout

import com.platos.domain.exam.AnswerWidth
import com.platos.domain.exam.ExamDefinition
import com.platos.domain.exam.Question
import com.platos.domain.exam.QuestionKind
import com.platos.domain.exam.Rubric
import com.platos.domain.exam.RubricCriterion
import com.platos.domain.exam.RubricDescriptor
import com.platos.domain.geometry.Um
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * A area de resposta so tem a moldura e a pauta (`slice-5c-0-o-recorte-da-resposta`, tarefa 1.2;
 * design, decisao 6, camada 1).
 *
 * Cada cenario daqui corresponde a um cenario da spec de `layout-engine`, e o nome diz qual. Os
 * mapas com defeito partem do mapa que o motor produz e acrescentam **uma** primitiva, para que a
 * unica coisa que difere do mapa valido seja a que esta sob teste.
 */
class AreaDeRespostaLimpaTest {

    private val engine = LayoutEngine()

    private fun objetiva(id: String) = Question(
        id = id,
        statement = "Enunciado da questao $id, com texto suficiente para ocupar linhas.",
        options = listOf("primeira", "segunda", "terceira", "quarta"),
    )

    private fun discursiva(id: String) = Question(
        id = id,
        kind = QuestionKind.ESSAY,
        statement = "Explique, com as suas palavras, o raciocinio da questao $id.",
        points = 2,
        answerLines = 5,
        answerWidth = AnswerWidth.COLUMN,
        rubric = Rubric(
            listOf(3, 2).mapIndexed { i, n ->
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

    private val valido: LayoutMap = engine.layout(
        ExamDefinition(id = "area-limpa", title = "Prova", questions = listOf(objetiva("q1"), discursiva("q2"))),
    )

    private val regiao: ScannableRegion = valido.regions.single { it.kind == LayoutEngine.ESSAY_KIND }

    private val area: NormalizedRect = requireNotNull(regiao.answerArea)

    // A area em micrometros, calculada aqui por aritmetica propria, e nao pela funcao privada da
    // validacao que o teste julga (P4).
    private fun desnormaliza(ppm: Int, extensao: Int): Int =
        ((ppm.toLong() * extensao + 500_000L) / 1_000_000L).toInt()

    private val esquerda = regiao.quadX + desnormaliza(area.u, regiao.quadWidth)
    private val direita = regiao.quadX + desnormaliza(area.u + area.uSize, regiao.quadWidth)
    private val topo = regiao.quadY + desnormaliza(area.v, regiao.quadHeight)
    private val base = regiao.quadY + desnormaliza(area.v + area.vSize, regiao.quadHeight)
    private val meioX = (esquerda + direita) / 2
    private val meioY = (topo + base) / 2

    private fun LayoutMap.com(primitiva: Primitive): LayoutMap =
        copy(pages = pages.map { if (it.index == regiao.page) it.copy(primitives = it.primitives + primitiva) else it })

    private fun problemasDe(mapa: LayoutMap): List<String> =
        assertIs<ValidationResult.Invalid>(mapa.validate(), "o mapa devia ser recusado").problems

    private fun assertRecusadoPorPrimitiva(mapa: LayoutMap, id: String, tipo: String) {
        // Outras regras (tinta, sobreposicao) podem citar a mesma primitiva; o que se conta e a
        // recusa desta regra.
        val citados = problemasDe(mapa).filter { "`$id`" in it && "dentro da area de resposta" in it }
        assertEquals(1, citados.size, "esperava exatamente uma recusa de `$id`: ${problemasDe(mapa)}")
        assertTrue("regiao ${regiao.index}" in citados.single(), "a recusa nao identifica a regiao: $citados")
        assertTrue("($tipo)" in citados.single(), "a recusa nao diz o tipo `$tipo`: $citados")
    }

    // --- guarda de vacuidade: a area de hoje tem o que a regra admite, e a regra viu tudo ---

    @Test
    fun `o mapa de hoje e valido e a area contem a moldura e a pauta que a regra admite`() {
        assertEquals(ValidationResult.Valid, valido.validate())

        val primitivas = valido.pages.single { it.index == regiao.page }.primitives
        val moldura = primitivas.filterIsInstance<DrawRect>().single { it.id == "r${regiao.index}-moldura" }
        assertTrue(moldura.fill == null, "a moldura e um retangulo sem preenchimento")
        assertTrue(moldura.x in esquerda..direita && moldura.y in topo..base, "a moldura devia estar na area")

        val pauta = primitivas.filterIsInstance<DrawLine>().filter { it.id.startsWith("r${regiao.index}-p") }
        assertTrue(pauta.isNotEmpty(), "sem linha de pauta o cenario nao prova nada")
        assertTrue(pauta.all { it.y1 in topo..base }, "a pauta devia estar dentro da area")
    }

    @Test
    fun `o QR da regiao encosta na borda da area e o mapa e valido`() {
        val qr = valido.pages.single { it.index == regiao.page }.primitives
            .filterIsInstance<DrawQr>().single { it.id == regiao.qrId }

        assertEquals(topo, qr.y + qr.side, "o QR devia terminar exatamente onde a area comeca")
        assertEquals(ValidationResult.Valid, valido.validate())
    }

    // --- cenarios da spec ---

    /** Cenario "Texto dentro da area de resposta". */
    @Test
    fun `texto que comeca dentro da largura da regiao e cuja linha cruza a area e recusado`() {
        val texto = DrawText(
            id = "estranho-texto",
            x = regiao.quadX + Um.mm(5).raw,
            baseline = meioY,
            size = Um.mm(3).raw,
            text = "Enunciado que nao devia estar aqui",
        )

        assertRecusadoPorPrimitiva(valido.com(texto), "estranho-texto", "texto")
    }

    /** Cenario "Nome do aluno impresso sobre a area". */
    @Test
    fun `o nome do aluno impresso sobre a area e recusado`() {
        val nome = DrawText(
            id = "nome-do-aluno",
            x = regiao.quadX,
            baseline = topo + Um.mm(8).raw,
            size = Um.mm(3).raw,
            text = "Aluno: Fulano de Tal",
        )

        assertRecusadoPorPrimitiva(valido.com(nome), "nome-do-aluno", "texto")
    }

    /** Cenario "Marcador, QR ou imagem dentro da area". */
    @Test
    fun `marcador dentro da area e recusado`() {
        val marcador = DrawAruco("estranho-marcador", 60, meioX, meioY, Um.mm(11).raw, Um.mm(1).raw, emptyList())

        assertRecusadoPorPrimitiva(valido.com(marcador), "estranho-marcador", "marcador")
    }

    @Test
    fun `QR dentro da area e recusado`() {
        val qr = DrawQr("estranho-qr", meioX, meioY, Um.mm(14).raw, Um.mm(1).raw, "x", emptyList())

        assertRecusadoPorPrimitiva(valido.com(qr), "estranho-qr", "QR")
    }

    @Test
    fun `imagem dentro da area e recusada`() {
        val imagem = DrawImage("estranha-imagem", meioX, meioY, Um.mm(20).raw, Um.mm(10).raw, "ref")

        assertRecusadoPorPrimitiva(valido.com(imagem), "estranha-imagem", "imagem")
    }

    @Test
    fun `circulo e retangulo preenchido dentro da area sao recusados`() {
        val circulo = DrawCircle("estranho-circulo", meioX, meioY, Um.mm(4).raw, 100)
        val preenchido = DrawRect("estranho-retangulo", meioX, meioY, Um.mm(10).raw, Um.mm(5).raw, 0, fill = 100)

        assertRecusadoPorPrimitiva(valido.com(circulo), "estranho-circulo", "circulo")
        assertRecusadoPorPrimitiva(valido.com(preenchido), "estranho-retangulo", "retangulo preenchido")
    }

    /** Cenario "Encostar na borda da area": a imagem termina exatamente onde a area comeca. */
    @Test
    fun `primitiva que so encosta na borda da area e aceita`() {
        val encostada = DrawImage("imagem-encostada", meioX, topo - Um.mm(10).raw, Um.mm(20).raw, Um.mm(10).raw, "ref")

        assertEquals(topo, encostada.y + encostada.height, "a fixture devia encostar, e nao cruzar")
        assertEquals(ValidationResult.Valid, valido.com(encostada).validate())
    }

    /** Cenario "Moldura e pauta sao admitidas": uma linha de pauta a mais, com tom, continua aceita. */
    @Test
    fun `linha de pauta com tom dentro da area e aceita`() {
        val linha = DrawLine("pauta-extra", esquerda, meioY, direita, meioY, 200, tone = 300)

        assertEquals(ValidationResult.Valid, valido.com(linha).validate())
    }

    // --- o limite conhecido da camada 1 (design, decisao 6) ---

    /**
     * Texto que comeca **fora** da largura da regiao e a invade lendo para a direita **nao e visto**
     * por esta validacao: o `DrawText` nao carrega largura. Este teste existe para o limite nao
     * virar lacuna silenciosa — se a camada 1 passar a ve-lo, ele fica vermelho e a decisao 6 tem de
     * ser atualizada, e nao o teste apagado. Quem o ve, hoje, e a captura sobre o documento
     * renderizado (tarefa 6.1).
     */
    @Test
    fun `texto que comeca fora da largura da regiao e a invade nao e visto por esta camada`() {
        val invasor = DrawText(
            id = "texto-invasor",
            x = regiao.quadX - Um.mm(20).raw,
            baseline = meioY,
            size = Um.mm(3).raw,
            text = "Uma linha longa o bastante para atravessar a folga e entrar na area de resposta",
        )

        assertEquals(ValidationResult.Valid, valido.com(invasor).validate())
    }
}
