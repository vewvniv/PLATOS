package com.platos.android.omr

import com.platos.domain.capture.OmrMeasurement
import com.platos.domain.layout.DrawCircle
import com.platos.domain.layout.LayoutMap
import com.platos.domain.layout.ScannableRegion
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

/** O que saiu de uma tentativa de medir as bolhas de uma regiao retificada. */
sealed interface MeterOutcome {

    /** Todas as bolhas que a regiao declara, medidas. Nunca um subconjunto. */
    data class Measured(val measurements: List<OmrMeasurement>) : MeterOutcome

    data class Failed(val reason: String) : MeterOutcome
}

/**
 * Mede a cobertura de tinta de cada bolha sobre a regiao retificada (ADR-0010).
 *
 * **A posicao de cada bolha vem do `LayoutMap`, e nada e inferido da imagem.** Nao ha deteccao de
 * circulo, nem espacamento presumido: o mapa e a fonte geometrica do OMR, e o buffer cobre
 * exatamente o quadrilatero que o mapa descreve, entao projetar e uma regra de tres.
 *
 * Detectar circulo na imagem pareceria mais robusto e seria o contrario: uma bolha inteiramente
 * preenchida a caneta deixa de ter contorno visivel, e o detector nao acharia justamente as que
 * importam.
 *
 * Este objeto **nao decide se a bolha esta marcada** — ver [OmrMeasurement].
 */
object BubbleMeter {

    fun measure(map: LayoutMap, region: ScannableRegion, image: RectifiedRegion): MeterOutcome {
        val circles = map.pages
            .firstOrNull { it.index == region.page }
            ?.primitives
            ?.filterIsInstance<DrawCircle>()
            .orEmpty()
        if (circles.isEmpty()) {
            return MeterOutcome.Failed("a pagina ${region.page} nao desenha bolha nenhuma")
        }

        // O raio medido e o do circulo desenhado **menos o traco**: o que interessa e a tinta
        // dentro da bolha, e nao o contorno preto que a delimita. Mesma definicao de `tinta.mjs`.
        val radiusUm = circles.minOf { it.diameter / 2 - it.stroke }
        if (radiusUm <= 0) {
            return MeterOutcome.Failed("o traco da bolha cobre o raio inteiro: ${circles.first().id}")
        }

        val pxPerUmX = image.width.toDouble() / region.quadWidth
        val pxPerUmY = image.height.toDouble() / region.quadHeight
        val radiusPx = radiusUm * (pxPerUmX + pxPerUmY) / 2
        if (radiusPx < MIN_RADIUS_PX) {
            return MeterOutcome.Failed(
                "raio de medicao de ${format(radiusPx)} px e pequeno demais para medir cobertura",
            )
        }

        val white = PaperWhite.of(image)
            ?: return MeterOutcome.Failed("a captura esta escura demais para achar o branco do papel")

        val measurements = ArrayList<OmrMeasurement>(region.bubbles.size)
        for (bubble in region.bubbles) {
            val centerX = bubble.u.toDouble() / PPM * image.width
            val centerY = bubble.v.toDouble() / PPM * image.height
            val id = "${bubble.questionId}/${bubble.option}"

            var sum = 0.0
            var count = 0
            var y = ceil(centerY - radiusPx).toInt()
            val yEnd = floor(centerY + radiusPx).toInt()
            while (y <= yEnd) {
                var x = ceil(centerX - radiusPx).toInt()
                val xEnd = floor(centerX + radiusPx).toInt()
                while (x <= xEnd) {
                    val dx = x - centerX
                    val dy = y - centerY
                    if (dx * dx + dy * dy <= radiusPx * radiusPx) {
                        if (!image.contains(x, y)) {
                            return MeterOutcome.Failed(
                                "o disco da bolha $id cai fora da regiao capturada",
                            )
                        }
                        val darkness = 1.0 - image.luminanceAt(x, y) / white.at(x.toDouble(), y.toDouble())
                        sum += if (darkness > 0.0) darkness else 0.0
                        count += 1
                    }
                    x += 1
                }
                y += 1
            }

            if (count == 0) {
                return MeterOutcome.Failed("a janela de medicao da bolha $id saiu vazia")
            }
            val coverage = sum / count
            if (!coverage.isFinite()) {
                // `NaN > teto` e falso: sem esta guarda a bolha passaria calada como se estivesse
                // dentro de qualquer limite que alguem viesse a comparar depois.
                return MeterOutcome.Failed("a cobertura da bolha $id nao e finita: $coverage")
            }

            measurements += OmrMeasurement(
                questionId = bubble.questionId,
                option = bubble.option,
                coveragePerMille = (coverage * OmrMeasurement.FULL)
                    .roundToInt()
                    .coerceIn(0, OmrMeasurement.FULL),
            )
        }

        if (measurements.size != region.bubbles.size) {
            return MeterOutcome.Failed(
                "medi ${measurements.size} de ${region.bubbles.size} bolhas declaradas",
            )
        }
        return MeterOutcome.Measured(measurements)
    }

    /** Partes por milhao: a unidade em que o mapa declara `u` e `v`. */
    private const val PPM = 1_000_000.0

    /** Abaixo disto o disco tem poucos pixels demais para uma media significar coisa alguma. */
    private const val MIN_RADIUS_PX = 3.0

    private fun format(value: Double): String = ((value * 100).roundToInt() / 100.0).toString()
}
