package com.platos.android.omr

import com.platos.domain.capture.OmrMeasurement
import com.platos.domain.exam.ExamDefinition
import com.platos.domain.exam.Question
import com.platos.domain.layout.DrawCircle
import com.platos.domain.layout.LayoutEngine
import kotlin.math.abs
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * A medicao de cobertura, conferida contra respostas que nao vieram de outra medicao.
 *
 * Roda na JVM, sem emulador e sem OpenCV: e essa a razao de a aritmetica estar separada do
 * adaptador. §13 poe a deteccao e a homografia no OpenCV; o que sobra aqui e o que produz numero.
 */
class BubbleMeterTest {

    private val map = LayoutEngine().layout(
        ExamDefinition(
            id = "prova-de-medicao",
            title = "Prova de medicao",
            questions = (1..8).map {
                Question(
                    id = "q$it",
                    statement = "Enunciado da questao $it com texto suficiente.",
                    options = listOf("a", "b", "c", "d"),
                )
            },
        ),
    )
    private val region = map.regions.single()
    private val radiusUm = map.pages[region.page].primitives
        .filterIsInstance<DrawCircle>()
        .minOf { it.diameter / 2 - it.stroke }

    // `assertTrue` do JUnit 5 nao tem contrato de smart cast, ao contrario do de `kotlin.test`.
    private fun measured(image: RectifiedRegion): List<OmrMeasurement> {
        val outcome = BubbleMeter.measure(map, region, image)
        val medida = outcome as? MeterOutcome.Measured
            ?: throw AssertionError("esperava medicao, veio $outcome")
        return medida.measurements
    }

    private fun failure(image: RectifiedRegion): String {
        val outcome = BubbleMeter.measure(map, region, image)
        val falha = outcome as? MeterOutcome.Failed
            ?: throw AssertionError("esperava falha, veio $outcome")
        return falha.reason
    }

    @Test
    fun `mede todas as bolhas que o mapa declara, e nenhuma a mais`() {
        val medicoes = measured(SyntheticRegion.of(region, radiusUm) { 0.0 })

        assertEquals(region.bubbles.size, medicoes.size)
        assertEquals(
            region.bubbles.map { "${it.questionId}/${it.option}" }.toSet(),
            medicoes.map { it.id }.toSet(),
        )
    }

    @Test
    fun `bolha vazia mede zero sobre papel limpo`() {
        val medicoes = measured(SyntheticRegion.of(region, radiusUm) { 0.0 })
        assertTrue(medicoes.all { it.coveragePerMille == 0 }) {
            "papel limpo mediu tinta: ${medicoes.filter { it.coveragePerMille != 0 }}"
        }
    }

    @Test
    fun `cobertura conferida contra fracao conhecida por construcao`() {
        // O oracle analitico: a faixa preta tem area exata dentro do circulo, calculada pela
        // formula do segmento circular — nada aqui passou por `BubbleMeter`.
        for (fracao in listOf(0.1, 0.25, 0.375, 0.5, 0.75, 1.0)) {
            val medicoes = measured(SyntheticRegion.of(region, radiusUm) { fracao })
            val esperado = SyntheticRegion.expectedPerMille(fracao)

            for (medicao in medicoes) {
                val desvio = abs(medicao.coveragePerMille - esperado)
                assertTrue(desvio <= TOLERANCIA_PER_MILLE) {
                    "fracao $fracao em ${medicao.id}: medido ${medicao.coveragePerMille}, " +
                        "esperado $esperado, desvio $desvio por mil"
                }
            }
        }
    }

    @Test
    fun `cobertura cresce junto com a fracao pintada`() {
        // Monotonicidade: pega o caso em que a medicao acerta a media por acidente mas perdeu a
        // relacao com o que foi pintado.
        var anterior = -1
        for (fracao in listOf(0.0, 0.2, 0.4, 0.6, 0.8, 1.0)) {
            val medido = measured(SyntheticRegion.of(region, radiusUm) { fracao }).first()
            assertTrue(medido.coveragePerMille > anterior) {
                "fracao $fracao mediu ${medido.coveragePerMille}, nao maior que $anterior"
            }
            anterior = medido.coveragePerMille
        }
    }

    @Test
    fun `bolhas vizinhas nao contaminam a medicao`() {
        // A janela de uma bolha nao pode alcancar a bolha do lado: o passo horizontal e 5,2 mm e o
        // diametro medido e 3,76 mm, entao sobra pouco. Uma bolha cheia ao lado de uma vazia e o
        // caso que revela a janela larga demais.
        val cheias = region.bubbles.filter { it.option == "A" }.map { it.questionId to it.option }.toSet()
        val imagem = SyntheticRegion.of(region, radiusUm) { bubble ->
            if ((bubble.questionId to bubble.option) in cheias) 1.0 else 0.0
        }

        for (medicao in measured(imagem)) {
            if (medicao.option == "A") {
                assertTrue(medicao.coveragePerMille > 800) {
                    "bolha cheia mediu ${medicao.coveragePerMille}"
                }
            } else {
                assertEquals(0, medicao.coveragePerMille, "a vizinha cheia vazou para ${medicao.id}")
            }
        }
    }

    @Test
    fun `papel escurecido nao vira tinta`() {
        // Iluminacao irregular: metade da regiao sai mais escura. A normalizacao e local, entao a
        // cobertura das bolhas vazias dessa metade tem de continuar em zero.
        val limpa = SyntheticRegion.of(region, radiusUm) { 0.0 }
        val escurecida = ByteArray(limpa.width * limpa.height)
        for (y in 0 until limpa.height) {
            for (x in 0 until limpa.width) {
                val valor = limpa.luminanceAt(x, y)
                val sombra = if (x > limpa.width / 2) (valor * 0.75).toInt() else valor
                escurecida[y * limpa.width + x] = sombra.toByte()
            }
        }

        val medicoes = measured(RectifiedRegion(limpa.width, limpa.height, escurecida))
        assertTrue(medicoes.all { it.coveragePerMille <= 5 }) {
            "a sombra virou tinta: ${medicoes.filter { it.coveragePerMille > 5 }}"
        }
    }

    @Test
    fun `regiao escura demais e recusada em vez de medida`() {
        val limpa = SyntheticRegion.of(region, radiusUm) { 0.0 }
        val preta = ByteArray(limpa.width * limpa.height) { 5 }

        assertTrue(
            failure(RectifiedRegion(limpa.width, limpa.height, preta)).contains("escura demais"),
        )
    }

    @Test
    fun `buffer pequeno demais e recusado em vez de medido`() {
        // Raio abaixo do minimo: uma media sobre tres pixels nao significa nada, e devolver um
        // numero ali seria pior que falhar.
        val minusculo = RectifiedRegion(40, 20, ByteArray(800) { SyntheticRegion.PAPER.toByte() })
        assertTrue(failure(minusculo).contains("pequeno demais"))
    }

    @Test
    fun `buffer inconsistente com as dimensoes e recusado na origem`() {
        val erro = runCatching { RectifiedRegion(10, 10, ByteArray(50)) }.exceptionOrNull()
        assertTrue(erro is IllegalArgumentException) { "buffer curto precisa falhar: $erro" }
    }

    private companion object {
        /**
         * Tolerancia entre a fracao ideal e a medida, em partes por mil.
         *
         * Doze, e o numero tem duas origens medidas — nao e folga escolhida por conforto:
         *
         * - **5 por mil** vem da corda da faixa. O gerador a desenha suavizada; o medidor integra
         *   pixel inteiro. Sobre uma corda de ~37 px isso e meio pixel de residuo.
         * - **11 por mil** vem da coroa do disco, e so aparece na fracao 1,0. O gerador suaviza a
         *   borda do circulo, o medidor usa mascara dura: pixels da coroa entram inteiros ou nao
         *   entram. A coroa e ~2/r da area, com r de 18,8 px.
         *
         * A segunda podia ser eliminada dando borda dura ao gerador tambem — e nao foi, de
         * proposito. O gerador e o **oracle**; fazer ele compartilhar a rasterizacao do disco com
         * quem ele julga tiraria justamente a independencia que ele existe para ter. Doze por mil
         * e 1,2 ponto percentual, contra um corredor de 200 a 400 que a fatia 3 herda.
         *
         * Um aviso que esta fatia aprendeu na pratica: a primeira versao deste teste tambem usava
         * 12, e passava **por acidente** — o gerador tinha 15 por mil de vies de rasterizacao e a
         * tolerancia larga escondia. Mesmo numero, evidencia diferente.
         */
        const val TOLERANCIA_PER_MILLE = 12
    }
}
