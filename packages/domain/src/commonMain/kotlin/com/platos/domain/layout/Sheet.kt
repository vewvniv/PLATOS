package com.platos.domain.layout

import com.platos.domain.geometry.Um

/**
 * Geometria da folha (§7).
 *
 * Duas leituras precisaram ser fixadas aqui porque §7 nao as fecha:
 *
 * - **Grampo.** §7 lista "margens 15 mm laterais e inferior / 14 mm superior, 8 mm reservados para
 *   grampo". Os 8 mm sao tratados como faixa de exclusao *dentro* da margem superior de 14 mm, e
 *   nao como reserva somada a ela. E a leitura que mantem os dois numeros coerentes — 8 < 14 — e
 *   nao consome altura util nenhuma. Nada e desenhado nessa faixa.
 * - **Medianiz.** §7 fixa duas colunas mas nao o espaco entre elas. Adotados 6 mm, dois passos da
 *   grade de 3 mm, para que a medianiz nao quebre o ritmo vertical.
 */
object Sheet {

    /** Passo da grade vertical (§7): toda altura de bloco e multiplo dela. */
    val GRID = Um.mm(3)

    val WIDTH = Um.mm(210)
    val HEIGHT = Um.mm(297)

    val MARGIN_TOP = Um.mm(14)
    val MARGIN_BOTTOM = Um.mm(15)
    val MARGIN_SIDE = Um.mm(15)

    /** Faixa superior onde o grampo entra e nada e desenhado. */
    val STAPLE_RESERVE = Um.mm(8)

    val GUTTER = Um.mm(6)

    const val COLUMNS = 2

    /** Largura util entre as margens laterais. */
    val CONTENT_WIDTH = WIDTH - MARGIN_SIDE * 2

    /** Largura de uma coluna. */
    val COLUMN_WIDTH = (CONTENT_WIDTH - GUTTER).divFloor(COLUMNS)

    /** Altura util entre as margens, antes de descontar qualquer regiao. */
    val CONTENT_HEIGHT = HEIGHT - MARGIN_TOP - MARGIN_BOTTOM

    /** Deslocamento horizontal do inicio de uma coluna. */
    fun columnLeft(column: Int): Um {
        require(column in 0 until COLUMNS) { "coluna fora da folha: $column" }
        return MARGIN_SIDE + (COLUMN_WIDTH + GUTTER) * column
    }
}
