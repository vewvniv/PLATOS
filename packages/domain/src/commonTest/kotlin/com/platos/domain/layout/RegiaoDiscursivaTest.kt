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

    /**
     * Discursiva com as duas escolhas do professor (ADR-0017).
     *
     * Por padrao, [linhas] e igual a soma de [esperadas]. So os dois cenarios da moldura os separam, e
     * e isso que faz a mutacao "moldura lida da rubrica" derrubar exatamente esses dois e nenhum
     * outro (tarefa 3.2): se o padrao os separasse, todo teste de geometria cairia junto, e a queda
     * nao diria qual camada segurou.
     */
    private fun discursiva(id: String, linhas: Int = 5, esperadas: IntArray = intArrayOf(3, 2)) = Question(
        id = id,
        kind = QuestionKind.ESSAY,
        statement = "Explique, com as suas palavras, o raciocinio da questao $id.",
        points = esperadas.size,
        answerLines = linhas,
        answerWidth = AnswerWidth.COLUMN,
        rubric = Rubric(
            esperadas.mapIndexed { i, n ->
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

    private fun LayoutMap.marcadoresDa(regiao: ScannableRegion) =
        primitivasDa(regiao.page).filterIsInstance<DrawAruco>().filter { it.id.startsWith("r${regiao.index}-") }

    private fun LayoutMap.molduraDa(regiao: ScannableRegion) =
        primitivasDa(regiao.page).filterIsInstance<DrawRect>().single { it.id == "r${regiao.index}-moldura" }

    private fun LayoutMap.pautaDa(regiao: ScannableRegion) =
        primitivasDa(regiao.page).filter { it.id.startsWith("r${regiao.index}-p") }

    /** Cenario "Identificadores dos marcadores da regiao discursiva" (ADR-0018). */
    @Test
    fun `cada discursiva tem a sua regiao, com os marcadores 4k e 4k+3`() {
        val map = engine.layout(prova(objetiva("q1"), discursiva("q2"), objetiva("q3"), discursiva("q4")))

        assertEquals(listOf(0, 1, 2), map.regions.map { it.index })
        val primeira = map.regiaoDa("q2")
        val segunda = map.regiaoDa("q4")
        assertEquals(1, primeira.index)
        assertEquals(listOf(4, 7), primeira.markerIds)
        assertEquals(2, segunda.index)
        assertEquals(listOf(8, 11), segunda.markerIds)

        // E os marcadores desenhados sao esses, e so esses, na pagina da regiao: 4k+1 e 4k+2 nao
        // sao impressos.
        for (regiao in listOf(primeira, segunda)) {
            assertEquals(regiao.markerIds, map.marcadoresDa(regiao).map { it.markerId })
        }
    }

    /** Cenario "Dois marcadores na diagonal e o QR no terceiro canto" (ADR-0018). */
    @Test
    fun `dois marcadores na diagonal e o QR no terceiro canto`() {
        val map = engine.layout(prova(objetiva("q1"), discursiva("q2"), discursiva("q3")))
        val discursivas = map.regions.filter { it.kind == LayoutEngine.ESSAY_KIND }
        assertEquals(2, discursivas.size)

        for (regiao in discursivas) {
            val k = regiao.index
            val direita = regiao.quadX + regiao.quadWidth
            val base = regiao.quadY + regiao.quadHeight
            val marcadores = map.marcadoresDa(regiao)

            // O retangulo de referencia e o externo: vai do canto de fora do 4k ao canto de fora do
            // 4k+3.
            val cima = marcadores.single { it.markerId == 4 * k }
            assertEquals(regiao.quadX to regiao.quadY, cima.x to cima.y, "o ${4 * k} nao esta no canto superior esquerdo")
            val baixo = marcadores.single { it.markerId == 4 * k + 3 }
            assertEquals(
                direita to base,
                (baixo.x + baixo.side) to (baixo.y + baixo.side),
                "o ${4 * k + 3} nao esta no canto inferior direito",
            )

            val qr = map.primitivasDa(regiao.page).filterIsInstance<DrawQr>().single { it.id == regiao.qrId }
            assertEquals(direita, qr.x + qr.side, "o QR da regiao $k nao encosta na borda direita")
            assertEquals(cima.y, qr.y, "o topo do QR da regiao $k nao esta na altura do topo do ${4 * k}")
            // O QR declarado e o desenhado: o canto superior direito do retangulo normalizado.
            assertEquals(0, regiao.qr.v)
            assertTrue(
                kotlin.math.abs(regiao.qr.u + regiao.qr.uSize - 1_000_000) <= 1,
                "o QR declarado da regiao $k nao chega a borda direita: ${regiao.qr}",
            )
        }
    }

    /** Cenario "Marcador discursivo dimensionado com folga" (ADR-0001, ADR-0018). */
    @Test
    fun `marcador discursivo continua com 10 mm mesmo reduzido 5 por cento`() {
        val minimo = Um.mm(10)
        val lado = EssayGeometry.MARKER_SIDE
        assertTrue(Um(lado.raw * 95 / 100) >= minimo, "reduzido 5%, o marcador cai para ${Um(lado.raw * 95 / 100)}")
        assertEquals(lado, EssayGeometry.MARKER_MODULE * 7, "o lado nao e sete modulos")

        // E e esse o lado desenhado, e nao uma constante que ninguem le.
        val map = engine.layout(prova(objetiva("q1"), discursiva("q2")))
        val marcadores = map.marcadoresDa(map.regiaoDa("q2"))
        assertEquals(2, marcadores.size)
        for (marcador in marcadores) {
            assertEquals(lado.raw, marcador.side)
            assertEquals(EssayGeometry.MARKER_MODULE.raw, marcador.module)
            assertEquals(7, marcador.modules.size)
            assertTrue(marcador.modules.all { it.length == 7 })
        }
    }

    /** Cenario "Coordenadas dentro da faixa normalizada", na regiao discursiva. */
    @Test
    fun `moldura, pauta, QR e area de resposta ficam dentro do retangulo de referencia`() {
        val map = engine.layout(prova(objetiva("q1"), discursiva("q2"), discursiva("q3", linhas = 9, esperadas = intArrayOf(5, 4))))
        for (regiao in map.regions.filter { it.kind == LayoutEngine.ESSAY_KIND }) {
            val dentro = { x: Int, y: Int ->
                x in regiao.quadX..(regiao.quadX + regiao.quadWidth) &&
                    y in regiao.quadY..(regiao.quadY + regiao.quadHeight)
            }
            val moldura = map.molduraDa(regiao)
            val meio = moldura.stroke / 2
            assertTrue(dentro(moldura.x - meio, moldura.y - meio), "moldura da regiao ${regiao.index} fora")
            assertTrue(
                dentro(moldura.x + moldura.width + meio, moldura.y + moldura.height + meio),
                "moldura da regiao ${regiao.index} fora",
            )
            for (linha in map.pautaDa(regiao).map { it as DrawLine }) {
                assertTrue(dentro(linha.x1, linha.y1 - linha.stroke / 2), "linha `${linha.id}` fora")
                assertTrue(dentro(linha.x2, linha.y2 + linha.stroke / 2), "linha `${linha.id}` fora")
            }
            val qr = map.primitivasDa(regiao.page).filterIsInstance<DrawQr>().single { it.id == regiao.qrId }
            assertTrue(dentro(qr.x, qr.y) && dentro(qr.x + qr.side, qr.y + qr.side), "QR da regiao ${regiao.index} fora")

            val area = requireNotNull(regiao.answerArea)
            for (rect in listOf(regiao.qr, area)) {
                for (valor in listOf(rect.u, rect.v, rect.u + rect.uSize, rect.v + rect.vSize)) {
                    assertTrue(valor in 0..1_000_000, "coordenada fora de [0,1] na regiao ${regiao.index}: $rect")
                }
            }
        }
    }

    /**
     * Cenario "Zona de silencio preservada", nos marcadores da regiao discursiva.
     *
     * A zona e de um modulo do proprio marcador, 1,6 mm. A discursiva cai em lugares diferentes da
     * paginacao conforme o numero de objetivas antes dela, e a guarda de vacuidade exige que ao menos
     * um marcador tenha caido na coluna da direita — e ali que a canaleta entre colunas e o vizinho.
     */
    @Test
    fun `nenhuma tinta invade a zona de silencio dos marcadores discursivos`() {
        val measurer = com.platos.domain.text.TextMeasurer(com.platos.domain.text.EmbeddedFont.program)
        val colunas = mutableSetOf<Int>()
        for (n in listOf(1, 6, 12, 20)) {
            val objetivas = (1..n).map { objetiva("o$it") }
            val map = engine.layout(
                prova(*(objetivas + discursiva("d1") + objetiva("x1") + discursiva("d2", linhas = 8, esperadas = intArrayOf(5, 3))).toTypedArray()),
            )
            for (regiao in map.regions.filter { it.kind == LayoutEngine.ESSAY_KIND }) {
                val primitivas = map.primitivasDa(regiao.page)
                for (marcador in map.marcadoresDa(regiao)) {
                    colunas += if (marcador.x > LayoutProfile.DEFAULT.columnLeft(1).raw - 1) 1 else 0
                    for (primitiva in primitivas) {
                        assertTrue(
                            clearsQuietZone(primitiva, marcador, marcador.module, measurer),
                            "com $n objetivas, `${primitiva.id}` invade a zona de silencio do marcador " +
                                "${marcador.markerId}: tinta em ${inkBoxOf(primitiva, measurer)}, marcador em " +
                                "${marcador.x}+${marcador.side} x ${marcador.y}+${marcador.side}",
                        )
                    }
                }
            }
        }
        assertEquals(setOf(0, 1), colunas, "os marcadores discursivos nao cairam nas duas colunas")
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
    fun `a regiao discursiva declara a questao, os marcadores, a area de resposta e o QR, e nenhuma bolha`() {
        val map = engine.layout(prova(objetiva("q1"), discursiva("q2")))
        val regiao = map.regiaoDa("q2")

        assertEquals(LayoutEngine.ESSAY_KIND, regiao.kind)
        assertEquals(listOf(4, 7), regiao.markerIds)
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

    /**
     * Cenario "O professor dimensiona a moldura" (ADR-0016, ADR-0017).
     *
     * As duas provas tem a mesma rubrica e diferem so no numero de linhas declarado: a moldura cresce
     * exatamente 3 x 7 mm, e o que fica em cima dela — o marcador de cima, o QR e o topo da moldura —
     * nao se move em relacao ao topo da regiao. O marcador de baixo acompanha a base.
     */
    @Test
    fun `o professor dimensiona a moldura pelo numero de linhas`() {
        val cinco = engine.layout(prova(objetiva("q1"), discursiva("q2", linhas = 5)))
        val oito = engine.layout(prova(objetiva("q1"), discursiva("q2", linhas = 8)))
        val rc = cinco.regiaoDa("q2")
        val ro = oito.regiaoDa("q2")

        assertEquals(
            (EssayGeometry.PAUTA * 3).raw,
            oito.molduraDa(ro).height - cinco.molduraDa(rc).height,
            "a altura da moldura nao seguiu o numero de linhas declarado",
        )
        assertEquals(cinco.molduraDa(rc).width, oito.molduraDa(ro).width)
        assertEquals(cinco.molduraDa(rc).y - rc.quadY, oito.molduraDa(ro).y - ro.quadY)

        fun qr(map: LayoutMap, regiao: ScannableRegion) =
            map.primitivasDa(regiao.page).filterIsInstance<DrawQr>().single { it.id == regiao.qrId }
        assertEquals(qr(cinco, rc).x - rc.quadX to qr(cinco, rc).y - rc.quadY, qr(oito, ro).x - ro.quadX to qr(oito, ro).y - ro.quadY)

        val cimaC = cinco.marcadoresDa(rc).single { it.markerId == 4 }
        val cimaO = oito.marcadoresDa(ro).single { it.markerId == 4 }
        assertEquals(cimaC.x - rc.quadX to cimaC.y - rc.quadY, cimaO.x - ro.quadX to cimaO.y - ro.quadY)
        val baixoC = cinco.marcadoresDa(rc).single { it.markerId == 7 }
        val baixoO = oito.marcadoresDa(ro).single { it.markerId == 7 }
        assertEquals(
            rc.quadY + rc.quadHeight - baixoC.y,
            ro.quadY + ro.quadHeight - baixoO.y,
            "o marcador de baixo nao acompanhou a base da regiao",
        )
        // A pauta tem uma linha a menos que o numero de linhas: as bordas da moldura sao a 0 e a n.
        assertEquals(4, cinco.pautaDa(rc).size)
        assertEquals(7, oito.pautaDa(ro).size)
    }

    /** Cenario "A rubrica nao mexe na moldura" (ADR-0017). */
    @Test
    fun `a rubrica nao mexe na moldura`() {
        val curta = prova(objetiva("q1"), discursiva("q2", linhas = 5, esperadas = intArrayOf(3, 2)))
        val longa = prova(objetiva("q1"), discursiva("q2", linhas = 5, esperadas = intArrayOf(6, 3)))
        // A entrada difere de fato, e so nos `expected_lines`.
        assertTrue(curta != longa)
        assertEquals(curta.questions.map { it.copy(rubric = null) }, longa.questions.map { it.copy(rubric = null) })

        assertEquals(engine.layout(curta).toCanonicalJson(), engine.layout(longa).toCanonicalJson())
    }

    /**
     * Cenario "Area de resposta com folga fora da moldura".
     *
     * A area vai da base do QR ate a zona de silencio do marcador de baixo, na largura inteira. A
     * moldura cabe inteira nela, com folga acima e com a folga da escrita abaixo.
     */
    @Test
    fun `a area de resposta contem a moldura, com folga acima e abaixo dela`() {
        val map = engine.layout(prova(objetiva("q1"), discursiva("q2"), discursiva("q3", linhas = 7, esperadas = intArrayOf(4, 3))))
        for (regiao in map.regions.filter { it.kind == LayoutEngine.ESSAY_KIND }) {
            val area = requireNotNull(regiao.answerArea)
            fun x(u: Int) = regiao.quadX + ((u.toLong() * regiao.quadWidth + 500_000) / 1_000_000).toInt()
            fun y(v: Int) = regiao.quadY + ((v.toLong() * regiao.quadHeight + 500_000) / 1_000_000).toInt()
            val (aLeft, aRight) = x(area.u) to x(area.u + area.uSize)
            val (aTop, aBottom) = y(area.v) to y(area.v + area.vSize)

            val moldura = map.molduraDa(regiao)
            val meio = moldura.stroke / 2
            val (mLeft, mRight) = (moldura.x - meio) to (moldura.x + moldura.width + meio)
            val (mTop, mBottom) = (moldura.y - meio) to (moldura.y + moldura.height + meio)
            val r = regiao.index

            assertTrue(aLeft <= mLeft && mRight <= aRight, "regiao $r: moldura [$mLeft, $mRight] fora da area [$aLeft, $aRight]")
            assertTrue(aTop < mTop, "regiao $r: sem folga acima da moldura (area $aTop, moldura $mTop)")
            assertTrue(
                aBottom - mBottom >= EssayGeometry.DESCENDER_CLEARANCE.raw,
                "regiao $r: folga abaixo da moldura de ${aBottom - mBottom} um, menor que a da escrita",
            )

            val qr = map.primitivasDa(regiao.page).filterIsInstance<DrawQr>().single { it.id == regiao.qrId }
            assertTrue(kotlin.math.abs(aTop - (qr.y + qr.side)) <= 1, "regiao $r: a area nao comeca na base do QR")
            val baixo = map.marcadoresDa(regiao).single { it.markerId == 4 * r + 3 }
            assertTrue(
                kotlin.math.abs(aBottom - (baixo.y - baixo.module)) <= 1,
                "regiao $r: a area nao termina na zona de silencio do marcador de baixo " +
                    "(area $aBottom, zona ${baixo.y - baixo.module})",
            )
        }
    }

    /** Cenario "Pauta abaixo do teto decorativo" (ADR-0010, ADR-0016). */
    @Test
    fun `a pauta e linha cinza de 7 mm abaixo do teto decorativo, e a moldura e preta`() {
        val map = engine.layout(prova(objetiva("q1"), discursiva("q2", linhas = 6, esperadas = intArrayOf(4, 2))))
        val regiao = map.regiaoDa("q2")
        val pauta = map.pautaDa(regiao)

        assertEquals(5, pauta.size, "a pauta de 6 linhas tem 5 tracos")
        assertTrue(pauta.all { it is DrawLine }, "a pauta tem primitiva que nao e linha: ${pauta.map { it.id }}")
        val linhas = pauta.map { it as DrawLine }.sortedBy { it.y1 }
        for (linha in linhas) {
            assertEquals(linha.y1, linha.y2, "a linha `${linha.id}` nao e horizontal")
            val tom = kotlin.test.assertNotNull(linha.tone, "a linha `${linha.id}` nao declara tom")
            assertTrue(
                tom < regiao.inkBudget.decorativeToneMax,
                "a linha `${linha.id}` tem tom $tom, e o teto decorativo e ${regiao.inkBudget.decorativeToneMax}",
            )
        }
        val moldura = map.molduraDa(regiao)
        val topo = moldura.y - moldura.stroke / 2
        assertEquals(
            List(5) { EssayGeometry.PAUTA.raw },
            (listOf(topo) + linhas.map { it.y1 }).zipWithNext { a, b -> b - a },
            "a pauta nao esta espacada de 7 mm a partir do topo da moldura",
        )
        // A moldura continua preta: retangulo de traco, sem trama e sem tom.
        assertEquals(null, moldura.fill)
        assertTrue(moldura.stroke > 0)
    }

    /** Cenario "O enunciado fica fora da moldura". */
    @Test
    fun `nenhum texto do enunciado cai dentro da regiao`() {
        val map = engine.layout(prova(objetiva("q1"), discursiva("q2")))
        val regiao = map.regiaoDa("q2")
        // O retangulo de referencia e o externo dos dois marcadores: e a propria regiao.
        val topo = regiao.quadY
        val base = regiao.quadY + regiao.quadHeight
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
            // A regiao comeca na borda da coluna, que e onde o numero da questao fica pendurado.
            assertEquals(regiao.quadX, texto.x, "com $n objetivas a regiao foi para outra coluna")
            lugares += regiao.page to texto.x
        }
        assertTrue(lugares.size > 1, "a discursiva caiu sempre no mesmo lugar: $lugares")
    }

    // --- a versao minima de renderizador, por mapa (decisao 4) ---

    /** Cenario "Mapa com pauta exige o renderizador que desenha linha". */
    @Test
    fun `mapa com pauta exige o renderizador que desenha linha`() {
        val map = engine.layout(prova(objetiva("q1"), discursiva("q2")))
        assertTrue(map.pages.any { page -> page.primitives.any { it is DrawLine } }, "o mapa nao tem linha")
        assertEquals(2, map.minRendererVersion)
    }

    /** Cenario "Mapa sem linha continua exigindo a versao 1". */
    @Test
    fun `mapa sem linha continua exigindo a versao 1`() {
        val map = engine.layout(prova(objetiva("q1"), objetiva("q2"), objetiva("q3")))
        assertTrue(map.pages.none { page -> page.primitives.any { it is DrawLine } }, "a prova objetiva tem linha")
        assertEquals(1, map.minRendererVersion)
    }

    /** Cenario "Moldura maior que a coluna". */
    @Test
    fun `moldura maior que a coluna e recusada, nomeando a questao`() {
        // A rubrica pede o mesmo que as linhas: o que se afirma aqui e o teto da coluna, e nao de
        // onde sai o numero — isso e dos dois cenarios da moldura, acima.
        val falha = kotlin.test.assertFailsWith<LayoutException> {
            engine.layout(prova(objetiva("q1"), discursiva("q2", linhas = 40, esperadas = intArrayOf(40))))
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
