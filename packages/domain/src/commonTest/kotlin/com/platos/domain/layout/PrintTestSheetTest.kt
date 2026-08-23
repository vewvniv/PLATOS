package com.platos.domain.layout

import com.platos.domain.capture.CaptureGeometry
import com.platos.domain.exam.ExamDefinition
import com.platos.domain.exam.Question
import com.platos.domain.fixtures.Fixtures
import com.platos.domain.geometry.Um
import com.platos.domain.text.EmbeddedFont
import com.platos.domain.text.TextMeasurer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A folha que reprova uma impressora (§16, D-2b.7).
 *
 * O que estes testes protegem nao e a aparencia dela: e a afirmacao de que ela percorre o **mesmo
 * caminho** da prova. Uma folha de teste desenhada por fora aprovaria uma impressora contra
 * geometria que a prova nao usa, e a aprovacao nao valeria nada.
 */
class PrintTestSheetTest {

    private val measurer = TextMeasurer(EmbeddedFont.program)
    private val folha = PrintTestSheet().layout()

    private val prova = LayoutEngine().layout(
        ExamDefinition(
            id = "prova-comparacao",
            title = "Prova de comparacao",
            questions = (1..12).map {
                Question(
                    id = "q$it",
                    statement = "Enunciado da questao $it com texto suficiente.",
                    options = listOf("a", "b", "c", "d"),
                )
            },
        ),
    )

    @Test
    fun `cabe em uma pagina e traz o conteudo minimo`() {
        assertEquals(1, folha.pages.size, "a folha de teste precisa caber em uma pagina")
        val primitives = folha.pages.single().primitives

        assertEquals(4, primitives.filterIsInstance<DrawAruco>().size)
        assertEquals(1, primitives.filterIsInstance<DrawQr>().size)
        assertEquals(5, primitives.filterIsInstance<DrawCircle>().size)

        val tramas = primitives.filterIsInstance<DrawRect>().filter { it.fill != null }
        assertEquals(listOf(45, 80), tramas.mapNotNull { it.fill }.sorted())

        val vao = primitives.filterIsInstance<DrawRect>().single { it.fill == null }
        assertEquals(LayoutProfile.DEFAULT.contentWidth.raw, vao.width)
    }

    @Test
    fun `geometria de captura e a mesma da prova`() {
        // A afirmacao central desta folha. Se um destes valores divergisse, a impressora seria
        // aprovada contra uma geometria que a prova nao usa.
        val daFolha = folha.pages.single().primitives
        val daProva = prova.pages[0].primitives

        val marcadorFolha = daFolha.filterIsInstance<DrawAruco>().first()
        val marcadorProva = daProva.filterIsInstance<DrawAruco>().first()
        assertEquals(marcadorProva.side, marcadorFolha.side)
        assertEquals(marcadorProva.module, marcadorFolha.module)
        assertEquals(CaptureGeometry.MARKER_SIDE.raw, marcadorFolha.side)

        val bolhaFolha = daFolha.filterIsInstance<DrawCircle>().first()
        val bolhaProva = daProva.filterIsInstance<DrawCircle>().first()
        assertEquals(bolhaProva.diameter, bolhaFolha.diameter)
        assertEquals(bolhaProva.stroke, bolhaFolha.stroke)

        // Passo horizontal entre bolhas vizinhas, medido no desenho e nao na constante.
        val centros = daFolha.filterIsInstance<DrawCircle>().map { it.centerX }.sorted()
        val passos = centros.zipWithNext { a, b -> b - a }.toSet()
        assertEquals(setOf(CaptureGeometry.BUBBLE_PITCH_H.raw), passos)

        // O QR sai do mesmo codificador, no mesmo lado.
        assertEquals(
            daProva.filterIsInstance<DrawQr>().single().side,
            daFolha.filterIsInstance<DrawQr>().single().side,
        )
    }

    @Test
    fun `criterio de aprovacao esta impresso na propria folha`() {
        val texto = folha.pages.single().primitives.filterIsInstance<DrawText>()
            .joinToString(" ") { it.text }

        // Quem confere esta com o papel na mao. O numero esperado e a faixa que aprova precisam
        // estar na folha, e nao neste repositorio.
        assertTrue(texto.contains("180,0 mm"), "falta o comprimento esperado do vao: $texto")
        assertTrue(texto.contains("171,0") && texto.contains("189,0"), "falta a faixa de ±5%")
        for (conferencia in listOf("1.", "2.", "3.", "4.", "5.")) {
            assertTrue(texto.contains(conferencia), "falta a conferencia $conferencia")
        }
        assertTrue(texto.contains("não está apta"), "falta o que fazer quando reprova")
    }

    /**
     * A folha reprova alguem? (tarefa 8.2)
     *
     * A conferencia de papel — imprimir de proposito fora de escala e ver a folha reprovar — nao
     * pode ser feita: nao ha impressora. O que da para provar sem uma e que a faixa **impressa na
     * folha** separa as reescalas que ADR-0001 declara normais das que quebram a captura. Uma
     * faixa larga demais aprovaria qualquer coisa, e ai a folha seria cerimonia.
     *
     * Os casos nao sao inventados: sao a populacao real de reescala. "Ajustar a pagina" de A4 para
     * A4 encolhe ~3% e **precisa** ser aprovado, porque e o que a impressao de referencia de
     * 2026-08-22 fez (175,0 mm) e o que o ADR absorve. A4 em papel Letter encolhe 5,9% e **precisa**
     * ser reprovado, porque e o erro de papel que a folha existe para pegar.
     */
    @Test
    fun `a faixa impressa separa reescala normal de reescala que reprova`() {
        val texto = folha.pages.single().primitives.filterIsInstance<DrawText>()
            .joinToString(" ") { it.text }

        val faixa = Regex("""Aprova entre (\d+),(\d) e (\d+),(\d) mm""").find(texto)
        assertTrue(faixa != null, "a faixa que aprova precisa estar impressa na folha: $texto")
        val (minInteiro, minDecimo, maxInteiro, maxDecimo) = faixa.destructured
        val minimo = Um.mmTenths(minInteiro.toInt() * 10 + minDecimo.toInt())
        val maximo = Um.mmTenths(maxInteiro.toInt() * 10 + maxDecimo.toInt())

        val vao = folha.pages.single().primitives.filterIsInstance<DrawRect>()
            .single { it.fill == null }.width

        // Aritmetica inteira, como todo o resto (D-1.2): escala em partes por milhao.
        fun impressoEm(escalaPpm: Long) = Um((vao.toLong() * escalaPpm / 1_000_000L).toInt())

        // ADR-0001: ±5% e reescala normal, e a homografia da fatia 3 absorve sem codigo adicional.
        // As duas pontas da faixa do ADR precisam ser aprovadas, ou a folha contradiz o ADR.
        assertTrue(impressoEm(950_000) >= minimo, "a folha reprovaria os −5% que ADR-0001 aprova")
        assertTrue(impressoEm(1_050_000) <= maximo, "a folha reprovaria os +5% que ADR-0001 aprova")

        // A impressao de referencia de 2026-08-22 mediu 175,0 mm com regua, e foi aprovada.
        assertTrue(Um.mmTenths(1_750) in minimo..maximo, "a impressao de referencia reprovaria")

        // A4 desenhado em papel Letter com "ajustar a pagina": a altura e quem limita.
        val a4EmLetter = minOf(215_900L * 1_000_000 / 210_000, 279_400L * 1_000_000 / 297_000)
        assertTrue(
            impressoEm(a4EmLetter) < minimo,
            "A4 em Letter mede ${impressoEm(a4EmLetter)} e a folha aprovaria — o erro de papel " +
                "passaria batido",
        )

        // Escala explicita errada no driver, os dois sentidos.
        assertTrue(impressoEm(900_000) < minimo, "90% seria aprovado")
        assertTrue(impressoEm(1_100_000) > maximo, "110% seria aprovado")
    }

    @Test
    fun `o vao de referencia e a maior distancia medivel da folha`() {
        val vao = folha.pages.single().primitives.filterIsInstance<DrawRect>().single {
            it.fill == null
        }
        // 180 mm: e onde a regua e mais confiavel, e e o mesmo portao de escala do protocolo.
        assertEquals(Um.mm(180).raw, vao.width)
        assertEquals(LayoutProfile.DEFAULT.marginSide.raw, vao.x)
    }

    @Test
    fun `folha de teste e um mapa valido`() {
        assertEquals(ValidationResult.Valid, folha.validate())
    }

    @Test
    fun `zona de silencio dos marcadores fica livre`() {
        val quiet = CaptureGeometry.QUIET_ZONE.raw
        val primitives = folha.pages.single().primitives
        for (aruco in primitives.filterIsInstance<DrawAruco>()) {
            for (primitive in primitives) {
                if (primitive is DrawAruco) continue
                assertTrue(
                    clearsQuietZone(primitive, aruco, quiet, measurer),
                    "`${primitive.id}` invade a zona de silencio do marcador ${aruco.markerId}",
                )
            }
        }
    }

    @Test
    fun `nada sai da pagina nem entra na faixa do grampo`() {
        val profile = LayoutProfile.DEFAULT
        for (primitive in folha.pages.single().primitives) {
            val box = inkBoxOf(primitive, measurer)
            assertTrue(box.top >= profile.stapleReserve.raw, "`${primitive.id}` entra no grampo")
            assertTrue(box.left >= 0 && box.top >= 0, "`${primitive.id}` sai da pagina: $box")
            assertTrue(
                box.right <= profile.pageWidth.raw && box.bottom <= profile.pageHeight.raw,
                "`${primitive.id}` sai da pagina: $box",
            )
        }
    }

    @Test
    fun `todo caractere da folha tem glifo na fonte embarcada`() {
        val font = EmbeddedFont.program
        val semGlifo = folha.pages.single().primitives
            .filterIsInstance<DrawText>()
            .flatMap { it.text.toList() }
            .filter { font.glyphOf(it.code) == 0 }
            .toSet()
        assertEquals(emptySet(), semGlifo, "caracteres sem glifo na fonte embarcada")
    }

    @Test
    fun `mesma folha em qualquer execucao`() {
        assertEquals(PrintTestSheet().layout().toCanonicalJson(), folha.toCanonicalJson())
    }

    @Test
    fun `folha de teste bate byte a byte com a versionada`() {
        // Mesma garantia do golden da prova, nos tres alvos: se algum deles calculasse um
        // micrometro diferente, a folha que aprova a impressora seria outra em cada plataforma.
        assertEquals(Fixtures.FOLHA_DE_TESTE_LAYOUT_JSON.trim(), folha.toCanonicalJson())
    }
}
