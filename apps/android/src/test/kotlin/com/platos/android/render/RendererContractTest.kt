package com.platos.android.render

import com.platos.domain.exam.ExamDefinition
import com.platos.domain.exam.Question
import com.platos.domain.layout.DrawImage
import com.platos.domain.layout.LayoutEngine
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * Roda como teste local de JVM, sem emulador: guarda de versao e conversao de unidade nao tocam
 * `android.graphics`. Sao justamente as duas coisas que precisam bater com o renderizador web.
 */
class RendererContractTest {

    private val map = LayoutEngine().layout(
        ExamDefinition(
            id = "prova-android",
            title = "Prova",
            questions = (1..6).map {
                Question(
                    id = "q$it",
                    statement = "Enunciado da questao $it.",
                    options = listOf("a", "b", "c", "d"),
                )
            },
        ),
    )

    @Test
    fun `renderizador desatualizado recusa imprimir`() {
        val exigente = map.copy(minRendererVersion = RendererContract.RENDERER_VERSION + 1)
        val falha = assertThrows<RendererVersionException> {
            RendererContract.assertSupports(exigente)
        }
        assertTrue(falha.message!!.contains("atualize"), falha.message)
    }

    @Test
    fun `renderizador compativel aceita o mapa`() {
        assertTrue(map.minRendererVersion <= RendererContract.RENDERER_VERSION)
        RendererContract.assertSupports(map)
    }

    @Test
    fun `conversao de unidade e identica a do renderizador web`() {
        // Os mesmos valores estao afirmados em apps/web/test/renderer.test.ts.
        assertEquals(72.0, RendererContract.umToPt(25_400))
        assertEquals(0.0, RendererContract.umToPt(0))
        assertEquals(595.2755905511812, RendererContract.umToPt(210_000))
        assertEquals((4_200 * 72.0) / 25_400.0, RendererContract.umToPt(4_200))
    }

    @Test
    fun `caixa de pagina em pontos inteiros bate com a do web`() {
        assertEquals(595, RendererContract.pagePoints(210_000))
        assertEquals(842, RendererContract.pagePoints(297_000))
    }

    private val imagem = DrawImage(
        id = "img-1",
        x = 0,
        y = 0,
        width = 1_000,
        height = 1_000,
        reference = "f-teste",
    )

    @Test
    fun `bytes ausentes recusam a imagem sem entregar documento parcial`() {
        val falha = assertThrows<MissingResourceException> {
            RendererContract.assertDrawable(imagem) { false }
        }
        assertTrue(falha.message!!.contains("img-1"), falha.message)
        assertTrue(falha.message!!.contains("f-teste"), falha.message)
        assertTrue(falha.message!!.contains("parcial"), falha.message)
    }

    @Test
    fun `referencia diferente da declarada nao satisfaz a guarda`() {
        // Ter *alguma* imagem disponivel nao basta: precisa ser a que o mapa declara, senao a
        // folha sairia com a formula de outra questao no lugar.
        assertThrows<MissingResourceException> {
            RendererContract.assertDrawable(imagem) { it == "f-outra" }
        }
    }

    @Test
    fun `imagem com os bytes fornecidos passa pela guarda`() {
        RendererContract.assertDrawable(imagem) { it == "f-teste" }
    }

    @Test
    fun `primitivas suportadas passam pela guarda`() {
        for (primitive in map.pages.flatMap { it.primitives }) {
            RendererContract.assertDrawable(primitive)
        }
    }

    /**
     * Cobre "Renderizador nao tipografa matematica" no lado Android.
     *
     * Mesma evidencia estrutural ja usada para medicao de texto: o renderizador nao pode conter
     * caminho de codigo que interprete LaTeX, MathML ou SVG. Ele desenha o recurso pronto.
     */
    @Test
    fun `o renderizador nao interpreta LaTeX MathML nem SVG`() {
        val fonte = java.io.File("src/main/kotlin/com/platos/android/render")
            .walkTopDown()
            .filter { it.extension == "kt" }
            .joinToString("\n") { it.readText() }

        assertTrue(fonte.isNotBlank(), "o teste nao encontrou o codigo do renderizador")
        for (proibido in listOf("latex", "mathml", "mathjax", "svg", "tex")) {
            assertTrue(
                !Regex("""\b$proibido\b""", RegexOption.IGNORE_CASE).containsMatchIn(fonte),
                "o renderizador Android menciona `$proibido`; ele deve desenhar o raster pronto",
            )
        }
        // O que ele *tem* de fazer: decodificar o raster que o mapa referencia.
        assertTrue(fonte.contains("decodeByteArray"), "o renderizador nao desenha o raster")
    }
}
