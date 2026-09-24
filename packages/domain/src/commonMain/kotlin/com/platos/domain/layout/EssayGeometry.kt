package com.platos.domain.layout

import com.platos.domain.capture.CaptureGeometry
import com.platos.domain.geometry.Um

/**
 * A geometria da regiao discursiva (§7, §8; `slice-5a-regiao-discursiva`).
 *
 * **Marcador e QR sao os do gabarito, de 14 mm** (decisao 5 do `design.md` da mudanca). O §7 admite
 * 10 mm para a discursiva, mas 14 mm e o unico tamanho com deteccao medida nesta base (3b, 3c): um
 * marcador menor seria geometria de captura nao medida, entrando por economia de papel.
 *
 * A regiao e montada como o gabarito: os quatro marcadores nos cantos, o quadrilatero pelos centros
 * deles, e o QR no topo, comecando na linha do quadrilatero. Abaixo do QR fica a **area de
 * resposta**, que e tudo o que a moldura contem — o enunciado fica fora (§8).
 */
object EssayGeometry {

    /** Pauta discursiva de §7: 8,6 mm, "generosa: manuscrito espremido e o pior inimigo da leitura". */
    val PAUTA: Um = Um.mmTenths(86)

    /** Traco da moldura: o mesmo da bolha, que e o traco fino ja impresso e medido nesta folha. */
    val FRAME_STROKE: Um = CaptureGeometry.BUBBLE_STROKE

    /** Traco da linha da pauta: mais fino que a moldura, para guiar a escrita sem competir com ela. */
    val PAUTA_STROKE: Um = Um.mmHundredths(15)

    /**
     * Recuo da linha da pauta em relacao a moldura, de cada lado.
     *
     * Existe para a linha nao encostar no traco da moldura: encostada, a tinta de uma entraria na
     * janela de medicao da outra, e a paridade de traco (`compare.mjs`) mediria as duas misturadas.
     */
    val PAUTA_INSET: Um = Um.mm(1)

    /** Do topo da regiao ate o topo da area de resposta: meio marcador, o QR e a zona de silencio. */
    val TOP_BAND: Um =
        CaptureGeometry.MARKER_SIDE.divFloor(2) + CaptureGeometry.QR_SIDE + CaptureGeometry.QUIET_ZONE

    /** Do fim da area de resposta ate o fim da regiao: a zona de silencio e o marcador de baixo. */
    val BOTTOM_CLEARANCE: Um = CaptureGeometry.QUIET_ZONE + CaptureGeometry.MARKER_SIDE
}
