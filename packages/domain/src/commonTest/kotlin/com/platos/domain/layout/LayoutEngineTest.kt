package com.platos.domain.layout

import com.platos.domain.capture.CaptureGeometry
import com.platos.domain.exam.AnswerWidth
import com.platos.domain.exam.ExamDefinition
import com.platos.domain.exam.Question
import com.platos.domain.exam.QuestionKind
import com.platos.domain.exam.UnsupportedContentException
import com.platos.domain.geometry.Ppm
import com.platos.domain.geometry.Um
import com.platos.domain.text.EmbeddedFont
import com.platos.domain.text.TextMeasurer
import com.platos.domain.text.TextStyle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class LayoutEngineTest {

    private val engine = LayoutEngine()
    private val measurer = TextMeasurer(EmbeddedFont.program)

    private fun prova(questionCount: Int = 12) = ExamDefinition(
        id = "prova-teste",
        title = "Prova de teste",
        questions = (1..questionCount).map {
            Question(
                id = "q$it",
                statement = "Enunciado da questao $it, com texto suficiente para ocupar linhas.",
                options = listOf("primeira", "segunda", "terceira", "quarta"),
            )
        },
    )

    @Test
    fun `mapa declara as duas versoes`() {
        val map = engine.layout(prova())
        assertEquals(1, map.layoutEngineVersion)
        assertEquals(1, map.minRendererVersion)
    }

    @Test
    fun `mapa declara a folha A4 e a fonte embarcada`() {
        val map = engine.layout(prova())
        assertEquals(Um.mm(210).raw, map.pageWidth)
        assertEquals(Um.mm(297).raw, map.pageHeight)
        assertEquals(
            "e5a4ee6a3d87bb9024796be390c6771e2a0eb1883dae25effaf57ca01668e24b",
            map.fontSha256,
        )
    }

    @Test
    fun `existe exatamente uma regiao de gabarito no topo da pagina 1`() {
        val map = engine.layout(prova())
        val region = map.regions.single()
        assertEquals("answer_block", region.kind)
        assertEquals(0, region.page)
        assertEquals(0, region.index)

        // A regiao deixou de encostar na margem superior quando o cabecalho entrou (fatia 2b), e o
        // que importa continua valendo: ela esta na pagina 1 e **antes de qualquer questao**. E
        // por isso que a pilha objetiva e escaneada sem folhear (§7), e nao pela distancia ate a
        // borda do papel. Afirmar a constante de novo so registraria a aritmetica do dia.
        val topoDoMarcador = region.quadY - CaptureGeometry.MARKER_SIDE.divFloor(2).raw
        assertTrue(
            topoDoMarcador >= LayoutProfile.DEFAULT.marginTop.raw,
            "regiao acima da margem superior: $topoDoMarcador",
        )
        val questoes = map.pages[0].primitives.filter { it.id.startsWith("q") }
        assertTrue(questoes.isNotEmpty(), "a pagina 1 deveria ter questoes")
        val primeiraQuestao = questoes.minOf { inkBoxOf(it, measurer).top }
        assertTrue(
            region.quadY + region.quadHeight <= primeiraQuestao,
            "a regiao invade a primeira questao: regiao termina em " +
                "${region.quadY + region.quadHeight}, questao comeca em $primeiraQuestao",
        )
    }

    @Test
    fun `marcadores da regiao zero usam os identificadores 0 a 3`() {
        val map = engine.layout(prova())
        assertEquals(listOf(0, 1, 2, 3), map.regions.single().markerIds)

        val arucos = map.pages[0].primitives.filterIsInstance<DrawAruco>()
        assertEquals(4, arucos.size)
        assertEquals(listOf(0, 1, 2, 3), arucos.map { it.markerId }.sorted())
        // Cada marcador carrega o proprio padrao: o renderizador nao consulta dicionario.
        assertTrue(arucos.all { it.modules.size == 7 && it.modules.all { row -> row.length == 7 } })
    }

    @Test
    fun `marcador respeita o minimo de 12 mm de lado`() {
        val map = engine.layout(prova())
        for (aruco in map.pages[0].primitives.filterIsInstance<DrawAruco>()) {
            assertTrue(aruco.side >= Um.mm(12).raw, "marcador ${aruco.markerId} menor que 12 mm")
            assertEquals(CaptureGeometry.MARKER_MODULE.raw, aruco.module)
        }
    }

    @Test
    fun `zona de silencio dos marcadores fica livre de qualquer tinta`() {
        // Ate a fatia 2b este teste olhava so bolhas, porque so bolhas chegavam perto. Com
        // cabecalho e faixa na folha, texto e trama passaram a poder invadir — e um marcador com
        // traco alheio na zona de silencio e a causa mais comum de captura que nao fecha (§16).
        val map = engine.layout(prova())
        val quiet = CaptureGeometry.QUIET_ZONE.raw

        for (page in map.pages) {
            val arucos = page.primitives.filterIsInstance<DrawAruco>()
            for (aruco in arucos) {
                for (primitive in page.primitives) {
                    if (primitive is DrawAruco) continue
                    assertTrue(
                        clearsQuietZone(primitive, aruco, quiet, measurer),
                        "`${primitive.id}` invade a zona de silencio do marcador " +
                            "${aruco.markerId}: tinta em ${inkBoxOf(primitive, measurer)}, " +
                            "marcador em ${aruco.x}+${aruco.side} x ${aruco.y}+${aruco.side}",
                    )
                }
            }
        }
    }

    @Test
    fun `cabecalho traz titulo e instrucao de preenchimento acima da regiao`() {
        val map = engine.layout(prova())
        val cabecalho = map.pages[0].primitives.filterIsInstance<DrawText>()
            .filter { it.id.startsWith("hd-") }
        assertTrue(cabecalho.isNotEmpty(), "a folha saiu sem cabecalho")

        val titulo = cabecalho.filter { it.id.startsWith("hd-t") }
        val instrucao = cabecalho.filter { it.id.startsWith("hd-i") }
        assertEquals("Prova de teste", titulo.joinToString(" ") { it.text }.trim())
        // A instrucao existe para o OMR, e nao para enfeitar: bolha preenchida pela metade e a
        // resposta que a leitura optica le como duvida.
        val texto = instrucao.joinToString(" ") { it.text }
        assertTrue(texto.contains("Preencha"), texto)
        assertTrue(texto.contains("caneta"), texto)

        // O titulo e maior que o corpo, e a instrucao e do corpo.
        assertTrue(titulo.all { it.size > LayoutProfile.DEFAULT.style.size.raw })
        assertTrue(instrucao.all { it.size == LayoutProfile.DEFAULT.style.size.raw })

        // Tudo do cabecalho fica acima do primeiro marcador.
        val topoDoMarcador = map.pages[0].primitives.filterIsInstance<DrawAruco>().minOf { it.y }
        assertTrue(
            cabecalho.all { it.baseline <= topoDoMarcador },
            "cabecalho invade a regiao de gabarito",
        )
    }

    @Test
    fun `nada e desenhado dentro da faixa reservada ao grampo`() {
        val map = engine.layout(prova())
        val grampo = LayoutProfile.DEFAULT.stapleReserve.raw

        for (page in map.pages) {
            for (primitive in page.primitives) {
                assertTrue(
                    inkBoxOf(primitive, measurer).top >= grampo,
                    "`${primitive.id}` entra na faixa do grampo: topo em " +
                        "${inkBoxOf(primitive, measurer).top}, grampo ate $grampo",
                )
            }
        }
    }

    @Test
    fun `rodape numera todas as paginas dentro da margem inferior`() {
        val map = engine.layout(prova(questionCount = 24))
        assertTrue(map.pages.size > 1, "esta prova deveria passar de uma pagina")

        val fimDoConteudo = LayoutProfile.DEFAULT.pageHeight.raw -
            LayoutProfile.DEFAULT.marginBottom.raw

        for (page in map.pages) {
            val rodape = page.primitives.filterIsInstance<DrawText>()
                .single { it.id.startsWith("ft-") }
            assertEquals("Página ${page.index + 1} de ${map.pages.size}", rodape.text)
            // Dentro da margem inferior: abaixo do fim do conteudo e acima da borda do papel.
            assertTrue(
                inkBoxOf(rodape, measurer).top >= fimDoConteudo,
                "rodape da pagina ${page.index} invade a area de conteudo",
            )
            assertTrue(
                inkBoxOf(rodape, measurer).bottom <= LayoutProfile.DEFAULT.pageHeight.raw,
                "rodape da pagina ${page.index} sai do papel",
            )
        }
    }

    @Test
    fun `gabarito agrupa as linhas em grupos de 3 a 5 e alterna a faixa`() {
        for (questionCount in 6..30) {
            val map = engine.layout(prova(questionCount))
            val faixas = map.pages[0].primitives.filterIsInstance<DrawRect>()
                .filter { it.id.startsWith("r0-f") }
            assertTrue(faixas.isNotEmpty(), "gabarito de $questionCount questoes saiu sem faixa")

            for (faixa in faixas) {
                assertEquals(45, faixa.fill, "trama fora dos 4,5% de §7 em ${faixa.id}")
                val linhas = faixa.height / CaptureGeometry.BUBBLE_PITCH_V.raw
                assertTrue(
                    linhas in 3..5,
                    "faixa ${faixa.id} cobre $linhas linhas, fora da faixa de 3 a 5",
                )
                assertEquals(
                    0,
                    faixa.height % CaptureGeometry.BUBBLE_PITCH_V.raw,
                    "faixa ${faixa.id} nao fecha em linhas inteiras",
                )
            }

            // Alternancia: duas faixas nunca sao grupos vizinhos da mesma coluna.
            val porColuna = faixas.groupBy { it.id.substringAfter("r0-f").substringBefore("-") }
            for ((coluna, daColuna) in porColuna) {
                val grupos = daColuna.map { it.id.substringAfterLast("-").toInt() }.sorted()
                for (index in 1 until grupos.size) {
                    assertTrue(
                        grupos[index] - grupos[index - 1] >= 2,
                        "coluna $coluna tem faixa em grupos vizinhos: $grupos",
                    )
                }
            }
        }
    }

    @Test
    fun `letra da alternativa fica dentro do circulo e mais clara que o corpo`() {
        val map = engine.layout(prova())
        val circulos = map.pages[0].primitives.filterIsInstance<DrawCircle>()
            .associateBy { it.id.removePrefix("r0-b") }
        val letras = map.pages[0].primitives.filterIsInstance<DrawText>()
            .filter { it.id.startsWith("r0-l") }
        assertEquals(circulos.size, letras.size, "faltou letra em alguma bolha")

        for (letra in letras) {
            val circulo = circulos.getValue(letra.id.removePrefix("r0-l"))
            assertEquals(circulo.id.substringAfterLast("-"), letra.text)

            // Mais clara que o corpo, que e preto pleno.
            assertTrue(letra.tone != null && letra.tone!! < LayoutMap.TONE_FULL, "letra opaca")

            // Dentro do circulo: a caixa da letra cabe no diametro interno, descontado o traco.
            val box = inkBoxOf(letra, measurer)
            val raio = circulo.diameter / 2 - circulo.stroke
            assertTrue(
                box.left >= circulo.centerX - raio && box.right <= circulo.centerX + raio,
                "letra ${letra.id} vaza do circulo na horizontal: $box",
            )
            assertTrue(
                box.top >= circulo.centerY - raio && box.bottom <= circulo.centerY + raio,
                "letra ${letra.id} vaza do circulo na vertical: $box",
            )
        }
    }

    @Test
    fun `toda bolha declarada coincide com o circulo desenhado`() {
        // O OMR le a coordenada **declarada**; o aluno marca o circulo **desenhado**. Se os dois
        // se separassem, a folha pareceria correta e a leitura sairia deslocada — e nenhuma outra
        // verificacao desta base olharia os dois lados ao mesmo tempo. E tambem o que reprova a
        // decoracao desta fatia se ela empurrar geometria: faixa e letra nao podem mover nada.
        val map = engine.layout(prova())
        val region = map.regions.single()
        val circulos = map.pages[0].primitives.filterIsInstance<DrawCircle>()
            .associateBy { it.id.removePrefix("r0-b") }
        assertEquals(circulos.size, region.bubbles.size)

        for (bubble in region.bubbles) {
            val circulo = circulos.getValue("${bubble.questionId}-${bubble.option}")
            assertEquals(
                Ppm.of(Um(circulo.centerX - region.quadX), Um(region.quadWidth)).raw,
                bubble.u,
                "bolha ${bubble.questionId}/${bubble.option} declarada fora do circulo em u",
            )
            assertEquals(
                Ppm.of(Um(circulo.centerY - region.quadY), Um(region.quadHeight)).raw,
                bubble.v,
                "bolha ${bubble.questionId}/${bubble.option} declarada fora do circulo em v",
            )
        }
    }

    @Test
    fun `todo caractere impresso tem glifo na fonte embarcada`() {
        // `glyphOf` devolve `.notdef` para o que a fonte nao cobre, **sem reclamar**: o texto sai
        // medido, o mapa sai valido e o defeito aparece so no papel, como um retangulo vazio no
        // lugar da letra. A folha ganhou acento nesta fatia — antes dela, nenhum texto emitido
        // pelo engine tinha um.
        val map = engine.layout(prova())
        val font = EmbeddedFont.program
        val semGlifo = map.pages
            .flatMap { it.primitives }
            .filterIsInstance<DrawText>()
            .flatMap { it.text.toList() }
            .filter { font.glyphOf(it.code) == 0 }
            .toSet()
        assertEquals(emptySet(), semGlifo, "caracteres sem glifo na fonte embarcada")
    }

    @Test
    fun `toda coordenada normalizada fica no intervalo unitario`() {
        val map = engine.layout(prova(30))
        val region = map.regions.single()
        for (bubble in region.bubbles) {
            assertTrue(Ppm(bubble.u).isInUnitRange, "bolha ${bubble.questionId} com u=${bubble.u}")
            assertTrue(Ppm(bubble.v).isInUnitRange, "bolha ${bubble.questionId} com v=${bubble.v}")
        }
        assertTrue(Ppm(region.qr.u).isInUnitRange)
        assertTrue(Ppm(region.qr.v).isInUnitRange)
    }

    @Test
    fun `ha uma bolha por alternativa de cada questao`() {
        val exam = prova(10)
        val region = engine.layout(exam).regions.single()
        assertEquals(10 * 4, region.bubbles.size)
        for (question in exam.questions) {
            val doQuestao = region.bubbles.filter { it.questionId == question.id }
            assertEquals(listOf("A", "B", "C", "D"), doQuestao.map { it.option })
        }
    }

    @Test
    fun `passo vertical das bolhas e 6 mm e multiplo da grade`() {
        assertEquals(Um.mm(6), CaptureGeometry.BUBBLE_PITCH_V)
        assertTrue(CaptureGeometry.BUBBLE_PITCH_V.isMultipleOf(LayoutProfile.DEFAULT.grid))

        val map = engine.layout(prova(6))
        val circles = map.pages[0].primitives.filterIsInstance<DrawCircle>()
        val primeiraColuna = circles.filter { it.id.endsWith("-A") }.sortedBy { it.centerY }
        val passos = primeiraColuna.zipWithNext { a, b -> b.centerY - a.centerY }.distinct()
        assertEquals(listOf(CaptureGeometry.BUBBLE_PITCH_V.raw), passos)
    }

    @Test
    fun `o QR fica dentro da regiao e carrega a matriz`() {
        val map = engine.layout(prova())
        val qr = map.pages[0].primitives.filterIsInstance<DrawQr>().single()
        assertTrue(qr.modules.isNotEmpty())
        assertEquals(qr.modules.size, qr.modules.first().length)
        assertTrue(qr.payload.startsWith("prova-teste..."))
    }

    @Test
    fun `mapa e identico entre recalculos`() {
        val exam = prova(24)
        assertEquals(engine.layout(exam).toCanonicalJson(), engine.layout(exam).toCanonicalJson())
    }

    @Test
    fun `json canonico nao tem numero fracionario`() {
        val json = engine.layout(prova()).toCanonicalJson()
        // Precisa olhar so os literais numericos: o payload do QR e o texto das questoes contem
        // digitos e pontos, e casar contra o JSON cru acusaria "0.4F2A" como se fosse um decimal.
        val semStrings = json.replace(Regex("\"(\\\\.|[^\"\\\\])*\""), "\"\"")
        assertTrue(
            !Regex("""\d+\.\d+""").containsMatchIn(semStrings),
            "o mapa serializado tem numero fracionario: $semStrings",
        )
        assertTrue(!semStrings.contains("E-"), "o mapa tem numero em notacao cientifica")
    }

    // Este cenario se chamava `questao discursiva impede a emissao do mapa` e afirmava que TODA
    // discursiva impedia o mapa. Com a `slice-5a-regiao-discursiva` isso deixou de ser verdade: o
    // que impede e a discursiva sem rubrica, que e o caso que este teste sempre montou. O nome e o
    // motivo conferido mudaram; a entrada, nao.
    @Test
    fun `discursiva sem rubrica impede a emissao do mapa`() {
        val exam = prova(4).let {
            it.copy(questions = it.questions.mapIndexed { index, q ->
                // As duas escolhas do professor declaradas, para que o unico defeito seja a rubrica.
                if (index == 2) {
                    q.copy(kind = QuestionKind.ESSAY, answerLines = 5, answerWidth = AnswerWidth.COLUMN)
                } else {
                    q
                }
            })
        }
        val falha = assertFailsWith<UnsupportedContentException> { engine.layout(exam) }
        assertTrue(falha.message!!.contains("q3"), falha.message!!)
        assertTrue(falha.message!!.contains("nao declara rubrica"), falha.message!!)
    }

    @Test
    fun `prova longa ocupa mais de uma pagina sem partir questao`() {
        val map = engine.layout(prova(40))
        assertTrue(map.pages.size > 1, "40 questoes deveriam passar de uma pagina")
        // Cada questao aparece com o seu numero uma unica vez em todo o mapa.
        val numeros = map.pages.flatMap { it.primitives }
            .filterIsInstance<DrawText>()
            .filter { it.id.endsWith("-n") }
        assertEquals(40, numeros.size)
        assertEquals(40, numeros.map { it.id }.toSet().size)
    }
}
