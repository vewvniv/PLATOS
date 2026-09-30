package com.platos.android.vision

import com.platos.android.omr.RectifiedRegion
import com.platos.domain.layout.EssayGeometry
import com.platos.domain.layout.LayoutMap
import com.platos.domain.layout.ScannableRegion
import kotlin.math.roundToInt
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc

/** O que saiu de um pedido de recorte da area de resposta. */
sealed interface RecorteOutcome {

    /**
     * [resposta] e a `answer_area` do mapa, retificada, em cinza, a [RegionDetector.PX_PER_MM] px/mm, e
     * **so ela**: a faixa de fora nao sai da funcao (`design.md`, decisao 4).
     */
    class Recortado(val resposta: RectifiedRegion, val residuoMaxMm: Double) : RecorteOutcome

    data class Recusado(val motivo: String) : RecorteOutcome
}

/**
 * O recorte da area de resposta da regiao discursiva
 * (`slice-5c-0-o-recorte-da-resposta`, design, decisoes 1 e 4; §8).
 *
 * **Uma chamada a parte, e nao parte do `analyze`:** roda uma vez, quando alguem precisa da imagem, e
 * nenhum codigo de producao a chama ainda.
 *
 * Um unico `warpPerspective`, com a homografia do segundo ajuste, sobre um canvas da area de resposta
 * **mais a faixa** de [EssayGeometry.DEVIANT_BAND] em cada lado, supersampleado e reduzido por media de
 * area (a mesma tecnica de [RegionDetector.detect]: `warpPerspective` nao aceita `INTER_AREA`, e reduzir
 * direto perderia tinta). O miolo do canvas e o recorte.
 *
 * Assim como a retificacao de hoje, a escala do supersample nao corrige o deslocamento de centro de
 * pixel do bloco de reducao: cerca de 0,03 mm, e nao corrigido.
 */
object RecorteDaResposta {

    private const val SUPERSAMPLE = 3

    /** A faixa de fora da area, em pixels: 3 mm a 10 px/mm. */
    val FAIXA_PX: Int = EssayGeometry.DEVIANT_BAND.raw * RegionDetector.PX_PER_MM / 1_000

    fun recortar(gray: Mat, map: LayoutMap, region: ScannableRegion): RecorteOutcome {
        val ajuste = when (val resultado = RegionDetector.segundoAjuste(gray, map, region)) {
            is RegionDetector.SegundoAjuste.Recusado -> return RecorteOutcome.Recusado(resultado.motivo)
            is RegionDetector.SegundoAjuste.Ajustado -> resultado
        }
        val canvas = canvasDaResposta(gray, region, ajuste.homografia)
        return RecorteOutcome.Recortado(canvas.miolo(), ajuste.residuoMaxMm)
    }

    /**
     * O canvas da area mais a faixa, e a conta que leva um ponto do mapa (em micrometros) a um pixel
     * dele. Um lugar so para a geometria do canvas: quem desenha a mascara do desvio usa a mesma.
     */
    internal class CanvasDaResposta(
        val canvas: RectifiedRegion,
        val faixaPx: Int,
        val larguraPx: Int,
        val alturaPx: Int,
        private val region: ScannableRegion,
        private val pxPorUvX: Double,
        private val pxPorUvY: Double,
        private val origemX: Double,
        private val origemY: Double,
    ) {
        /** O pixel do canvas para uma coordenada X do mapa, em micrometros, na pagina da regiao. */
        fun xDe(um: Int): Double = pxPorUvX * (um - region.quadX).toDouble() / region.quadWidth - origemX

        fun yDe(um: Int): Double = pxPorUvY * (um - region.quadY).toDouble() / region.quadHeight - origemY

        /** A area de resposta, sem a faixa. */
        fun miolo(): RectifiedRegion {
            val pixels = ByteArray(larguraPx * alturaPx)
            for (y in 0 until alturaPx) {
                for (x in 0 until larguraPx) {
                    pixels[y * larguraPx + x] = canvas.luminanceAt(x + faixaPx, y + faixaPx).toByte()
                }
            }
            return RectifiedRegion(larguraPx, alturaPx, pixels)
        }
    }

    internal fun canvasDaResposta(gray: Mat, region: ScannableRegion, homografia: Mat): CanvasDaResposta {
        val area = requireNotNull(region.answerArea) { "regiao ${region.index} sem area de resposta" }
        val ppm = 1_000_000.0

        // Pixels por unidade do quadrado unitario: a regiao inteira a 10 px/mm.
        val pxPorUvX = region.quadWidth * RegionDetector.PX_PER_MM / 1_000.0
        val pxPorUvY = region.quadHeight * RegionDetector.PX_PER_MM / 1_000.0

        val larguraPx = (area.uSize / ppm * pxPorUvX).roundToInt()
        val alturaPx = (area.vSize / ppm * pxPorUvY).roundToInt()
        val faixa = FAIXA_PX
        val larguraTotal = larguraPx + 2 * faixa
        val alturaTotal = alturaPx + 2 * faixa

        // A origem do canvas no espaco em pixels da regiao: canto da area, menos a faixa.
        val origemX = area.u / ppm * pxPorUvX - faixa
        val origemY = area.v / ppm * pxPorUvY - faixa

        // uv -> canvas, ja na escala do supersample; depois, imagem -> uv pela homografia do ajuste.
        val paraCanvas = Mat(3, 3, CvType.CV_64F)
        paraCanvas.put(
            0, 0,
            SUPERSAMPLE * pxPorUvX, 0.0, -SUPERSAMPLE * origemX,
            0.0, SUPERSAMPLE * pxPorUvY, -SUPERSAMPLE * origemY,
            0.0, 0.0, 1.0,
        )
        val matriz = Mat()
        Core.gemm(paraCanvas, homografia, 1.0, Mat(), 0.0, matriz)

        val grande = Mat()
        Imgproc.warpPerspective(
            gray, grande, matriz,
            Size((larguraTotal * SUPERSAMPLE).toDouble(), (alturaTotal * SUPERSAMPLE).toDouble()),
            Imgproc.INTER_LINEAR,
        )
        val pequeno = Mat()
        Imgproc.resize(grande, pequeno, Size(larguraTotal.toDouble(), alturaTotal.toDouble()), 0.0, 0.0, Imgproc.INTER_AREA)

        val cinza8 = Mat()
        pequeno.convertTo(cinza8, CvType.CV_8UC1)
        val pixels = ByteArray(larguraTotal * alturaTotal)
        cinza8.get(0, 0, pixels)

        return CanvasDaResposta(
            canvas = RectifiedRegion(larguraTotal, alturaTotal, pixels),
            faixaPx = faixa,
            larguraPx = larguraPx,
            alturaPx = alturaPx,
            region = region,
            pxPorUvX = pxPorUvX,
            pxPorUvY = pxPorUvY,
            origemX = origemX,
            origemY = origemY,
        )
    }
}
