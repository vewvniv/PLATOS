package com.platos.domain.layout

import com.platos.domain.capture.CaptureGeometry
import com.platos.domain.exam.ExamDefinition
import com.platos.domain.exam.Question
import com.platos.domain.exam.QuestionKind
import com.platos.domain.exam.UnsupportedContentException
import com.platos.domain.geometry.Ppm
import com.platos.domain.geometry.Um
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class LayoutEngineTest {

    private val engine = LayoutEngine()

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
        // O topo do quadrilatero fica meio marcador abaixo do topo da regiao, que comeca na
        // margem superior.
        assertEquals(
            (Sheet.MARGIN_TOP + CaptureGeometry.MARKER_SIDE.divFloor(2)).raw,
            region.quadY,
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
    fun `zona de silencio dos marcadores fica livre de bolhas`() {
        val map = engine.layout(prova())
        val arucos = map.pages[0].primitives.filterIsInstance<DrawAruco>()
        val circles = map.pages[0].primitives.filterIsInstance<DrawCircle>()
        val quiet = CaptureGeometry.QUIET_ZONE.raw
        val radius = CaptureGeometry.BUBBLE_DIAMETER.raw / 2

        for (aruco in arucos) {
            for (circle in circles) {
                val separatedHorizontally =
                    circle.centerX + radius <= aruco.x - quiet ||
                        circle.centerX - radius >= aruco.x + aruco.side + quiet
                val separatedVertically =
                    circle.centerY + radius <= aruco.y - quiet ||
                        circle.centerY - radius >= aruco.y + aruco.side + quiet
                assertTrue(
                    separatedHorizontally || separatedVertically,
                    "bolha ${circle.id} invade a zona de silencio do marcador ${aruco.markerId}",
                )
            }
        }
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
        assertTrue(CaptureGeometry.BUBBLE_PITCH_V.isMultipleOf(Sheet.GRID))

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

    @Test
    fun `questao discursiva impede a emissao do mapa`() {
        val exam = prova(4).let {
            it.copy(questions = it.questions.mapIndexed { index, q ->
                if (index == 2) q.copy(kind = QuestionKind.ESSAY) else q
            })
        }
        val falha = assertFailsWith<UnsupportedContentException> { engine.layout(exam) }
        assertTrue(falha.message!!.contains("q3"), falha.message!!)
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
