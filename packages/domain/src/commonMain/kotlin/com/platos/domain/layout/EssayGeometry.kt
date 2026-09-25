package com.platos.domain.layout

import com.platos.domain.capture.CaptureGeometry
import com.platos.domain.geometry.Um

/**
 * A geometria da regiao discursiva (§7, §8; ADR-0016, ADR-0018).
 *
 * **Dois marcadores na diagonal, e o QR no terceiro canto** (ADR-0018, decisao 1 do `design.md` da
 * `slice-5b-0-a-regiao-discursiva-compacta`). O `4k` fica no canto superior esquerdo, o QR no
 * superior direito, na mesma faixa, e o `4k+3` no inferior direito. A moldura fica logo abaixo da
 * faixa de cima, na largura inteira da regiao. O retangulo de referencia e o **externo** dos dois
 * marcadores, e e a propria regiao.
 *
 * Ate essa mudanca a regiao tinha quatro marcadores de 14 mm, os do gabarito, escolhidos na 5a por
 * serem o unico tamanho com deteccao medida. O marcador de 11,2 mm entra por custo de papel, decidido
 * pelo mantenedor, e a deteccao dele e **medida** na 4.1 retomada da `slice-5b-1`, com regra de
 * parada: se as fotos nao o detectarem, ele volta a 14 mm.
 */
object EssayGeometry {

    /** Pauta de 7 mm (ADR-0016): guia para o aluno, e nao geometria para a captura. */
    val PAUTA: Um = Um.mm(7)

    /** Traco da moldura: o mesmo da bolha, que e o traco fino ja impresso e medido nesta folha. */
    val FRAME_STROKE: Um = CaptureGeometry.BUBBLE_STROKE

    /**
     * Traco da linha da pauta. **Provisorio** (decisao 5): e o da previa que o mantenedor aprovou na
     * tela, e tela nao e papel. O valor que fica sai da impressao, pelo criterio escrito antes dela.
     */
    val PAUTA_STROKE: Um = Um.mmHundredths(20)

    /**
     * Tom da pauta em permilagem de preto. **Provisorio**, pela mesma razao de [PAUTA_STROKE]. Fica
     * abaixo do teto decorativo da regiao (ADR-0010), que e o que faz a pauta ser decoracao.
     */
    const val PAUTA_TONE: Int = 300

    /**
     * Recuo da linha da pauta em relacao a borda da regiao, de cada lado.
     *
     * Existe para a linha nao encostar no traco da moldura: encostada, a tinta de uma entraria na
     * janela de medicao da outra, e a paridade de traco (`compare.mjs`) mediria as duas misturadas.
     */
    val PAUTA_INSET: Um = Um.mm(1)

    /** Modulo do marcador discursivo: 1,6 mm, e sete dele dao o lado. */
    val MARKER_MODULE: Um = Um.mmTenths(16)

    /**
     * Lado do marcador discursivo: 7 modulos de 1,6 mm, 11,2 mm.
     *
     * O minimo de §7 para a discursiva e 10 mm, no pior caso da reescala (ADR-0001): reduzido 5%, este
     * da 10,64 mm. Os 10,5 mm dariam 9,98 mm.
     */
    val MARKER_SIDE: Um = MARKER_MODULE * 7

    /** Zona de silencio do marcador discursivo: um modulo, o minimo que §7 exige. */
    val MARKER_QUIET_ZONE: Um = MARKER_MODULE

    /**
     * Zona de silencio do QR, entre ele e a moldura: 2 mm.
     *
     * O QR e o do gabarito, 29 modulos de 0,482 mm, e quatro modulos dao 1,93 mm. A zona do marcador,
     * de 1,6 mm, nao basta para ele — por isso as duas ficam separadas.
     */
    val QR_QUIET_ZONE: Um = Um.mm(2)

    /** Folga abaixo da moldura: a tinta que desce da ultima linha, a perna do "g", do "p" e do "q". */
    val DESCENDER_CLEARANCE: Um = Um.mm(2)

    /** Do topo da regiao ate o topo da moldura: o QR e a zona de silencio dele. */
    val TOP_BAND: Um = CaptureGeometry.QR_SIDE + QR_QUIET_ZONE

    /** Da base da moldura ate o fim da regiao: a folga da escrita, a zona de silencio e o marcador. */
    val BOTTOM_CLEARANCE: Um = DESCENDER_CLEARANCE + MARKER_QUIET_ZONE + MARKER_SIDE

    /**
     * Os dois marcadores da regiao discursiva [regionIndex]: `4k` e `4k+3` (ADR-0018).
     *
     * A alocacao `{4k..4k+3}` do §8 fica, e `4k+1` e `4k+2` simplesmente nao sao impressos: a regiao
     * continua identificada pelos IDs dela, e o teto de regioes do dicionario nao muda.
     */
    fun markerIdsOf(regionIndex: Int): List<Int> {
        val todos = CaptureGeometry.markerIdsOf(regionIndex)
        return listOf(todos.first(), todos.last())
    }
}
