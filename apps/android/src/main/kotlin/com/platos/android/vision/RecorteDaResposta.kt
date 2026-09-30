package com.platos.android.vision

import com.platos.android.omr.RectifiedRegion
import com.platos.android.omr.RetanguloPx
import com.platos.android.omr.TintaDoAluno
import com.platos.domain.capture.DesvioDaResposta
import com.platos.domain.layout.DrawAruco
import com.platos.domain.layout.DrawQr
import com.platos.domain.layout.DrawRect
import com.platos.domain.layout.EssayGeometry
import com.platos.domain.layout.LayoutMap
import com.platos.domain.layout.ScannableRegion
import kotlin.math.ceil
import kotlin.math.floor
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
     * **so ela**: a faixa de fora nao sai da funcao (`design.md`, decisao 4). [desvio] e um sinal de
     * conferencia: nao altera o recorte nem o recusa.
     */
    class Recortado(
        val resposta: RectifiedRegion,
        val residuoMaxMm: Double,
        val desvio: DesvioDaResposta,
    ) : RecorteOutcome

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
        val desvio = when (val medido = medirDesvio(map, region, canvas)) {
            is Desvio.Recusado -> return RecorteOutcome.Recusado(medido.motivo)
            is Desvio.Medido -> medido.desvio
        }
        return RecorteOutcome.Recortado(canvas.miolo(), ajuste.residuoMaxMm, desvio)
    }

    private sealed interface Desvio {
        class Medido(val desvio: DesvioDaResposta) : Desvio
        class Recusado(val motivo: String) : Desvio
    }

    /**
     * Conta a tinta do aluno na area e na faixa, com a tinta impressa descontada, e classifica
     * (`design.md`, decisao 5). Uma contagem que nao se faz recusa o recorte com o motivo, e nao o
     * entrega com um "sem desvio" que ninguem mediu.
     */
    private fun medirDesvio(map: LayoutMap, region: ScannableRegion, canvas: CanvasDaResposta): Desvio {
        val mascara = mascaraDaTintaImpressa(map, region, canvas)
            ?: return Desvio.Recusado("a pagina da regiao ${region.index} nao desenha a moldura dela")
        val contagem = TintaDoAluno.contar(
            canvas.canvas, canvas.faixaPx, mascara, region.inkBudget.decorativeToneMax,
        ) ?: return Desvio.Recusado("a captura esta escura demais para achar o branco do papel")

        val dentro = TintaDoAluno.centesimosDeMm2(contagem.dentro, RegionDetector.PX_PER_MM)
        val fora = TintaDoAluno.centesimosDeMm2(contagem.fora, RegionDetector.PX_PER_MM)
        return try {
            Desvio.Medido(DesvioDaResposta.classificar(dentro, fora))
        } catch (e: IllegalArgumentException) {
            Desvio.Recusado("a contagem de tinta nao e valida: ${e.message}")
        }
    }

    /**
     * Os retangulos da tinta que o mapa declara na pagina da regiao — os dois marcadores, o QR e os
     * quatro lados da moldura —, no canvas, **dilatados pelo teto do residuo**: o erro de posicao que o
     * proprio ajuste admite. Nulo se a moldura nao esta na pagina.
     */
    internal fun mascaraDaTintaImpressa(
        map: LayoutMap,
        region: ScannableRegion,
        canvas: CanvasDaResposta,
    ): List<RetanguloPx>? {
        val primitivas = map.pages.firstOrNull { it.index == region.page }?.primitives ?: return null
        val dilatacao = ceil(RegionDetector.MAX_RESIDUAL_MM * RegionDetector.PX_PER_MM).toInt()

        fun caixa(x0: Int, y0: Int, x1: Int, y1: Int) = RetanguloPx(
            floor(canvas.xDe(x0)).toInt(), floor(canvas.yDe(y0)).toInt(),
            ceil(canvas.xDe(x1)).toInt(), ceil(canvas.yDe(y1)).toInt(),
        ).dilatado(dilatacao)

        val mascara = ArrayList<RetanguloPx>()
        for (p in primitivas) {
            when {
                p is DrawAruco && p.markerId in region.markerIds -> mascara += caixa(p.x, p.y, p.x + p.side, p.y + p.side)
                p is DrawQr && p.id == region.qrId -> mascara += caixa(p.x, p.y, p.x + p.side, p.y + p.side)
            }
        }
        val moldura = primitivas.filterIsInstance<DrawRect>().firstOrNull { it.id == "r${region.index}-moldura" }
            ?: return null
        // O traco e centrado no caminho do retangulo: meio traco para cada lado.
        val meio = moldura.stroke / 2
        val x0 = moldura.x - meio; val x1 = moldura.x + moldura.width + meio
        val y0 = moldura.y - meio; val y1 = moldura.y + moldura.height + meio
        mascara += caixa(x0, y0, x1, moldura.y + meio) // topo
        mascara += caixa(x0, moldura.y + moldura.height - meio, x1, y1) // base
        mascara += caixa(x0, y0, moldura.x + meio, y1) // esquerda
        mascara += caixa(moldura.x + moldura.width - meio, y0, x1, y1) // direita
        return mascara
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
