package com.platos.domain.layout

import com.platos.domain.exam.AnswerWidth
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
        answerLines = linhas.sum(),
        answerWidth = AnswerWidth.COLUMN,
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

    // --- 3.3: o bloco e a regiao discursivos ---

    private fun LayoutMap.regiaoDa(questao: String): ScannableRegion =
        regions.single { it.questionId == questao }

    private fun LayoutMap.primitivasDa(pagina: Int) = pages.single { it.index == pagina }.primitives

    /** Cenario "Identificadores dos marcadores da regiao discursiva". */
    @Test
    fun `cada discursiva tem a sua regiao, com os marcadores 4k a 4k+3`() {
        val map = engine.layout(prova(objetiva("q1"), discursiva("q2"), objetiva("q3"), discursiva("q4")))

        assertEquals(listOf(0, 1, 2), map.regions.map { it.index })
        val primeira = map.regiaoDa("q2")
        val segunda = map.regiaoDa("q4")
        assertEquals(1, primeira.index)
        assertEquals(listOf(4, 5, 6, 7), primeira.markerIds)
        assertEquals(2, segunda.index)
        assertEquals(listOf(8, 9, 10, 11), segunda.markerIds)

        // E os marcadores desenhados sao esses, na pagina da regiao.
        for (regiao in listOf(primeira, segunda)) {
            val desenhados = map.primitivasDa(regiao.page).filterIsInstance<DrawAruco>()
                .filter { it.id.startsWith("r${regiao.index}-") }
                .map { it.markerId }
            assertEquals(regiao.markerIds, desenhados)
        }
    }

    /** Cenario "Cada QR declara a sua regiao", do payload. */
    @Test
    fun `o QR de cada regiao carrega o indice dela`() {
        val map = engine.layout(prova(objetiva("q1"), discursiva("q2"), discursiva("q3")))
        for (regiao in map.regions) {
            val qr = map.primitivasDa(regiao.page).filterIsInstance<DrawQr>().single { it.id == regiao.qrId }
            val lido = com.platos.domain.capture.QrPayload.read(qr.payload)
            val payload = (lido as com.platos.domain.capture.PayloadReading.Read).payload
            assertEquals(regiao.index, payload.regionIndex, "o QR `${qr.id}` diz outra regiao")
        }
    }

    /** Cenario "Regiao discursiva completa". */
    @Test
    fun `a regiao discursiva declara a questao, a area de resposta e o QR, e nenhuma bolha`() {
        val map = engine.layout(prova(objetiva("q1"), discursiva("q2")))
        val regiao = map.regiaoDa("q2")

        assertEquals(LayoutEngine.ESSAY_KIND, regiao.kind)
        assertTrue(regiao.bubbles.isEmpty(), "regiao discursiva com bolha")
        val area = requireNotNull(regiao.answerArea)
        for (valor in listOf(area.u, area.v, area.u + area.uSize, area.v + area.vSize)) {
            assertTrue(valor in 0..1_000_000, "area de resposta fora de [0,1]: $area")
        }
        assertTrue(
            map.primitivasDa(regiao.page).any { it is DrawQr && it.id == regiao.qrId },
            "o QR `${regiao.qrId}` nao esta na pagina ${regiao.page}",
        )
    }

    /** Cenario "A rubrica dimensiona a moldura". */
    @Test
    fun `a rubrica dimensiona a moldura, e so ela`() {
        val curta = engine.layout(prova(objetiva("q1"), discursiva("q2", 3, 2)))
        val longa = engine.layout(prova(objetiva("q1"), discursiva("q2", 5, 2)))

        fun moldura(map: LayoutMap): DrawRect {
            val regiao = map.regiaoDa("q2")
            return map.primitivasDa(regiao.page).filterIsInstance<DrawRect>()
                .single { it.id == "r${regiao.index}-moldura" }
        }

        // Duas linhas a mais: a moldura cresce exatamente 2 x 8,6 mm.
        assertEquals(
            (EssayGeometry.PAUTA * 2).raw,
            moldura(longa).height - moldura(curta).height,
            "a altura da moldura nao seguiu os expected_lines",
        )
        // E o resto da regiao nao mexe: mesma largura, mesma distancia do topo do quadrilatero ao QR,
        // mesmo marcador de cima.
        val rc = curta.regiaoDa("q2")
        val rl = longa.regiaoDa("q2")
        assertEquals(rc.quadWidth, rl.quadWidth)
        assertEquals(rc.quadX, rl.quadX)
        assertEquals(moldura(curta).width, moldura(longa).width)
        assertEquals(moldura(curta).y - rc.quadY, moldura(longa).y - rl.quadY)
        // A pauta tem uma linha a menos que o numero de linhas: as bordas da moldura sao a 0 e a n.
        val pautaLonga = longa.primitivasDa(rl.page).count { it.id.startsWith("r${rl.index}-p") }
        assertEquals(5 + 2 - 1, pautaLonga)
    }

    /** Cenario "O enunciado fica fora da moldura". */
    @Test
    fun `nenhum texto do enunciado cai dentro da regiao`() {
        val map = engine.layout(prova(objetiva("q1"), discursiva("q2")))
        val regiao = map.regiaoDa("q2")
        // O quadrilatero e pelos centros dos marcadores; a regiao desenhada vai meio marcador alem.
        val meio = com.platos.domain.capture.CaptureGeometry.MARKER_SIDE.divFloor(2).raw
        val topo = regiao.quadY - meio
        val base = regiao.quadY + regiao.quadHeight + meio
        val enunciado = map.primitivasDa(regiao.page).filterIsInstance<DrawText>()
            .filter { it.id.startsWith("qq2-") }
        assertTrue(enunciado.isNotEmpty(), "o enunciado da q2 nao foi desenhado")
        for (texto in enunciado) {
            assertTrue(
                texto.baseline < topo || texto.baseline > base,
                "o texto `${texto.id}` (linha de base ${texto.baseline}) cai dentro da regiao " +
                    "[$topo, $base]",
            )
        }
    }

    /**
     * Cenario "Enunciado e moldura nao se separam".
     *
     * Varia o numero de objetivas antes da discursiva para que ela caia em lugares diferentes da
     * paginacao, e afirma, em todos, que o numero da questao e a regiao estao na mesma pagina e na
     * mesma coluna. A guarda de vacuidade exige que ao menos um caso tenha levado a discursiva para
     * fora da primeira coluna — senao a afirmacao seria sobre um bloco que nunca foi empurrado.
     */
    @Test
    fun `enunciado e moldura ficam juntos onde quer que o paginador os ponha`() {
        val lugares = mutableSetOf<Pair<Int, Int>>()
        for (n in 1..30) {
            val objetivas = (1..n).map { objetiva("o$it") }
            val map = engine.layout(prova(*(objetivas + discursiva("d")).toTypedArray()))
            val regiao = map.regiaoDa("d")
            val numero = map.pages.flatMap { p -> p.primitives.map { p.index to it } }
                .single { it.second.id == "qd-n" }
            val texto = numero.second as DrawText
            assertEquals(regiao.page, numero.first, "com $n objetivas o numero e a regiao se separaram")
            val xDaRegiao = regiao.quadX - com.platos.domain.capture.CaptureGeometry.MARKER_SIDE.divFloor(2).raw
            assertEquals(xDaRegiao, texto.x, "com $n objetivas a regiao foi para outra coluna")
            lugares += regiao.page to texto.x
        }
        assertTrue(lugares.size > 1, "a discursiva caiu sempre no mesmo lugar: $lugares")
    }

    /** Cenario "Moldura maior que a coluna". */
    @Test
    fun `moldura maior que a coluna e recusada, nomeando a questao`() {
        val falha = kotlin.test.assertFailsWith<LayoutException> {
            engine.layout(prova(objetiva("q1"), discursiva("q2", 40)))
        }
        assertTrue(falha.message!!.contains("q2"), falha.message!!)
    }

    // --- 3.4: a validacao da regiao discursiva ---
    //
    // Cada cenario parte de um mapa VALIDO produzido pelo motor e muda UMA coisa (`rigorous.md` §3):
    // se a recusa viesse de outra conferencia, a assercao do motivo nao passaria.

    private val valido: LayoutMap by lazy {
        engine.layout(prova(objetiva("q1"), discursiva("q2"), discursiva("q3")))
    }

    private fun LayoutMap.comRegiao(indice: Int, muda: (ScannableRegion) -> ScannableRegion) =
        copy(regions = regions.map { if (it.index == indice) muda(it) else it })

    private fun problemas(map: LayoutMap): List<String> {
        val resultado = map.validate()
        assertTrue(resultado is ValidationResult.Invalid, "esperava mapa invalido")
        return resultado.problems
    }

    @Test
    fun `o mapa com discursivas que o motor produz e valido, e a validacao nao o altera`() {
        val antes = valido.toCanonicalJson()
        assertEquals(ValidationResult.Valid, valido.validate())
        assertEquals(antes, valido.toCanonicalJson())
    }

    /** Cenario "Area de resposta sobre o QR". */
    @Test
    fun `area de resposta sobre o QR e recusada`() {
        val quebrado = valido.comRegiao(1) { it.copy(answerArea = it.qr) }
        assertEquals(
            listOf("a area de resposta da regiao 1 sobrepoe o QR da regiao"),
            problemas(quebrado),
        )
    }

    @Test
    fun `area de resposta fora do quadrilatero e recusada`() {
        val quebrado = valido.comRegiao(2) { r -> r.copy(answerArea = requireNotNull(r.answerArea).copy(u = 100_000)) }
        val lista = problemas(quebrado)
        assertEquals(1, lista.size, "$lista")
        assertTrue(lista.single().startsWith("a area de resposta da regiao 2 sai do quadrilatero"), "$lista")
    }

    /** Cenario "Duas regioes para a mesma questao". */
    @Test
    fun `duas regioes para a mesma questao e recusado`() {
        val quebrado = valido.comRegiao(2) { it.copy(questionId = "q2") }
        assertEquals(listOf("questoes com mais de uma regiao discursiva: q2"), problemas(quebrado))
    }

    /** Cenario "QR declarado que nao existe". */
    @Test
    fun `QR declarado que nao existe na pagina e recusado`() {
        val quebrado = valido.comRegiao(1) { it.copy(qrId = "nao-existe") }
        val lista = problemas(quebrado)
        assertEquals(1, lista.size, "$lista")
        assertTrue(lista.single().contains("regiao 1 declara o QR `nao-existe`"), "$lista")
    }

    @Test
    fun `regiao discursiva sem questao e recusada`() {
        val quebrado = valido.comRegiao(1) { it.copy(questionId = null) }
        assertEquals(listOf("regiao 1 e discursiva e nao declara questao"), problemas(quebrado))
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
