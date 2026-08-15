package com.platos.android.render

import com.platos.domain.exam.ExamDefinition
import com.platos.domain.exam.Question
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

    @Test
    fun `primitiva desconhecida e recusada sem entregar documento parcial`() {
        val imagem = com.platos.domain.layout.DrawImage(
            id = "img-1",
            x = 0,
            y = 0,
            width = 1_000,
            height = 1_000,
            reference = "grafico.png",
        )
        val falha = assertThrows<UnknownPrimitiveException> {
            RendererContract.assertDrawable(imagem)
        }
        assertTrue(falha.message!!.contains("img-1"), falha.message)
        assertTrue(falha.message!!.contains("parcial"), falha.message)
    }

    @Test
    fun `primitivas suportadas passam pela guarda`() {
        for (primitive in map.pages.flatMap { it.primitives }) {
            RendererContract.assertDrawable(primitive)
        }
    }
}
