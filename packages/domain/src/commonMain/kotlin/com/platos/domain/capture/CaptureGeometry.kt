package com.platos.domain.capture

import com.platos.domain.geometry.Um

/**
 * Geometria de captura (§7 e §8).
 *
 * Estes numeros sao restricoes de visao computacional, nao escolhas de design: mexer neles muda o
 * que o OMR consegue ler de uma folha fotografada.
 *
 * O lado do marcador merece nota. §7 exige ao menos 12 mm no gabarito e zona de silencio de ao
 * menos um modulo. Um marcador do DICT_5X5 tem 7 modulos de lado, e 12 mm dariam 1714,28... um por
 * modulo — fracionario, que D-1.2 nao admite. 14 mm dao exatamente 2 mm por modulo, satisfazem o
 * minimo com folga e mantem toda a aritmetica inteira.
 */
object CaptureGeometry {

    val BUBBLE_DIAMETER = Um.mmTenths(42)
    val BUBBLE_PITCH_H = Um.mmTenths(52)
    val BUBBLE_PITCH_V = Um.mm(6)
    val BUBBLE_STROKE = Um.mmHundredths(22)

    /** 7 modulos de 2 mm. Acima do minimo de 12 mm de §7. */
    val MARKER_SIDE = Um.mm(14)
    val MARKER_MODULE = Um.mm(2)

    /** Zona de silencio: um modulo, o minimo que §7 exige. */
    val QUIET_ZONE = MARKER_MODULE

    /** Lado do QR impresso, igual ao do marcador para que a faixa superior tenha altura unica. */
    val QR_SIDE = Um.mm(14)

    /** Canaleta do numero da questao dentro do gabarito. */
    val LABEL_WIDTH = Um.mm(8)

    /** Espaco entre duas colunas de bolhas. */
    val BUBBLE_COLUMN_GAP = Um.mm(8)

    /** Teto de altura da regiao de gabarito, para que ela nao engula a pagina 1. */
    val MAX_REGION_HEIGHT = Um.mm(100)

    /**
     * Quantas regioes o dicionario comporta: quatro marcadores por regiao (§8).
     *
     * Derivado, e nao escolhido. Com `DICT_5X5_100` sao 25 — a do gabarito e 24 discursivas. Um
     * numero digitado aqui seria um segundo registro do tamanho do dicionario, e o dia em que um dos
     * dois mudasse sem o outro a recusa passaria a acontecer no lugar errado.
     */
    const val MAX_REGIONS: Int = ArucoDictionary.SIZE / 4

    /** Identificadores dos quatro marcadores da regiao [regionIndex] (§8). */
    fun markerIdsOf(regionIndex: Int): List<Int> {
        val first = 4 * regionIndex
        require(first + 3 < ArucoDictionary.SIZE) {
            "regiao $regionIndex excede o DICT_5X5_100: os marcadores iriam ate ${first + 3}"
        }
        return listOf(first, first + 1, first + 2, first + 3)
    }

    /** Matriz de modulos do marcador, para o renderizador desenhar sem consultar dicionario. */
    fun markerModules(markerId: Int): List<String> =
        ArucoDictionary.modulesOf(markerId).map { row ->
            row.joinToString("") { if (it) "1" else "0" }
        }
}
