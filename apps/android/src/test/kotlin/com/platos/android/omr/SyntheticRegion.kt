package com.platos.android.omr

import com.platos.domain.layout.Bubble
import com.platos.domain.layout.ScannableRegion
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Gera uma regiao retificada cuja cobertura e conhecida **por construcao**.
 *
 * Este e o oracle analitico da fatia: a resposta nao sai de outra medicao, sai da aritmetica de
 * quem desenhou. Nenhuma linha aqui e compartilhada com [BubbleMeter] — se as duas concordarem, e
 * porque as duas estao certas, e nao porque as duas herdaram o mesmo engano.
 *
 * A construcao e deliberadamente boba: para cobrir uma fracao `f` do disco, pinta-se de preto uma
 * **faixa horizontal** cuja area dentro do circulo e exatamente `f` da area do circulo. A area de
 * um segmento circular tem forma fechada, entao a altura da faixa sai por bissecao ate a precisao
 * que se quiser, sem depender de nenhuma nocao de "medir pixel".
 */
object SyntheticRegion {

    /** Escala do buffer sintetico. Perto da resolucao nativa da digitalizacao da fatia 2b. */
    const val PX_PER_MM = 10

    /** O papel do buffer sintetico. Nao e 255 de proposito: papel de captura nunca e. */
    const val PAPER = 240

    /** A tinta do buffer sintetico. Nao e 0 de proposito, pela mesma razao. */
    const val INK = 20

    /**
     * Buffer com cada bolha da regiao preenchida na fracao que [coverageOf] devolver.
     *
     * O valor devolvido e a cobertura **ideal**, de 0 a 1, antes de qualquer arredondamento de
     * pixel: e contra ele que a medicao vai ser conferida.
     */
    fun of(region: ScannableRegion, radiusUm: Int, coverageOf: (Bubble) -> Double): RectifiedRegion {
        val width = (region.quadWidth * PX_PER_MM) / 1_000
        val height = (region.quadHeight * PX_PER_MM) / 1_000
        val pixels = ByteArray(width * height) { PAPER.toByte() }

        val radiusPx = radiusUm * PX_PER_MM / 1_000.0
        for (bubble in region.bubbles) {
            val fraction = coverageOf(bubble)
            require(fraction in 0.0..1.0) { "fracao fora de [0,1]: $fraction" }
            if (fraction == 0.0) continue

            val centerX = bubble.u.toDouble() / 1_000_000 * width
            val centerY = bubble.v.toDouble() / 1_000_000 * height
            // Fronteira da faixa: `y` a partir do qual pintar, medido do topo do circulo.
            val cut = centerY - radiusPx + heightForFraction(fraction) * 2 * radiusPx

            var y = (centerY - radiusPx).toInt() - 1
            val yEnd = (centerY + radiusPx).toInt() + 1
            while (y <= yEnd) {
                var x = (centerX - radiusPx).toInt() - 1
                val xEnd = (centerX + radiusPx).toInt() + 1
                while (x <= xEnd) {
                    if (x in 0 until width && y in 0 until height) {
                        val inked = inkedFractionOf(x, y, centerX, centerY, radiusPx, cut)
                        if (inked > 0.0) {
                            val value = PAPER + (INK - PAPER) * inked
                            pixels[y * width + x] = value.roundToInt().toByte()
                        }
                    }
                    x += 1
                }
                y += 1
            }
        }
        return RectifiedRegion(width, height, pixels)
    }

    /**
     * Quanto da area **deste pixel** cai dentro do disco e acima da corda, de 0 a 1.
     *
     * O buffer e desenhado com suavizacao porque ele e o **oracle**, e um oracle enviesado nao
     * serve. Pintar pixel inteiro conforme o centro parece inofensivo e nao e: sobre uma corda de
     * ~37 px, meio pixel de viés na fronteira vale 15 por mil de cobertura — foi exatamente o que
     * a primeira versao deste gerador produziu, e a medicao levou a culpa por um erro do desenho.
     */
    private fun inkedFractionOf(
        x: Int,
        y: Int,
        centerX: Double,
        centerY: Double,
        radiusPx: Double,
        cut: Double,
    ): Double {
        var inside = 0
        for (sy in 0 until SUBPIXELS) {
            for (sx in 0 until SUBPIXELS) {
                val px = x + (sx + 0.5) / SUBPIXELS - 0.5
                val py = y + (sy + 0.5) / SUBPIXELS - 0.5
                val dx = px - centerX
                val dy = py - centerY
                if (dx * dx + dy * dy <= radiusPx * radiusPx && py < cut) inside += 1
            }
        }
        return inside.toDouble() / (SUBPIXELS * SUBPIXELS)
    }

    /** Subdivisoes por lado na suavizacao. 8x8 poe o erro de area bem abaixo de um por mil. */
    private const val SUBPIXELS = 8

    /**
     * Altura relativa da faixa, de 0 a 1, cuja area dentro do circulo unitario e [fraction].
     *
     * Bissecao sobre a formula fechada do segmento circular. Trinta iteracoes dao precisao muito
     * abaixo de um pixel, e o custo e irrelevante num teste.
     */
    private fun heightForFraction(fraction: Double): Double {
        var low = 0.0
        var high = 1.0
        repeat(30) {
            val mid = (low + high) / 2
            if (segmentArea(mid) < fraction) low = mid else high = mid
        }
        return (low + high) / 2
    }

    /**
     * Fracao da area de um circulo unitario abaixo de uma corda a altura relativa [h], de 0 a 1.
     *
     * Area do segmento circular de raio 1: `acos(1-2h) - (1-2h)*sqrt(1-(1-2h)^2)`, dividida por PI.
     */
    private fun segmentArea(h: Double): Double {
        val d = 1 - 2 * h
        return (kotlin.math.acos(d) - d * sqrt(1 - d * d)) / kotlin.math.PI
    }

    /**
     * A cobertura que [BubbleMeter] deveria medir num buffer feito por [of], dada a fracao ideal.
     *
     * Nao e a fracao crua: o buffer sintetico usa [PAPER] e [INK] em vez de 255 e 0, e a medicao
     * normaliza contra o branco do papel. Uma bolha totalmente pintada de [INK] sobre papel
     * [PAPER] mede `1 - INK/PAPER`, e nao 1.
     */
    fun expectedPerMille(fraction: Double): Int =
        (fraction * (1.0 - INK.toDouble() / PAPER) * 1_000).roundToInt()
}
