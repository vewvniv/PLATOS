package com.platos.android.omr

/**
 * O branco do papel, medido **por pedaco** da propria captura.
 *
 * ADR-0010 define cobertura com zero no papel. O papel de uma foto nao e 255 e nao e o mesmo na
 * folha inteira: a lampada cai nas bordas, a mao faz sombra, o vinco escurece uma faixa. Medir
 * contra 255 fixo somaria essa sombra a tinta da caneta, e uma bolha vazia debaixo da sombra
 * pareceria marcada.
 *
 * O percentil alto de cada bloco e o papel daquele pedaco: dentro de um bloco de alguns
 * milimetros, a maior parte da area **e** papel, mesmo onde ha bolhas e texto.
 *
 * **Esta classe e o unico lugar do OMR que decide o que e branco, e isso e de proposito.** A fatia
 * do corpus provavelmente vai normalizar tambem contra o preto do ArUco, que esta presente em toda
 * captura — na digitalizacao da fatia 2b o miolo preto de um marcador lia 83 de 255, entao toner
 * pleno rendia no maximo 64% de cobertura, e uma camera comprime diferente a cada foto. Trocar
 * isso precisa ser uma mudanca aqui, e nao uma cacada por normalizacoes espalhadas.
 */
class PaperWhite private constructor(
    private val blocksX: Int,
    private val blocksY: Int,
    private val field: DoubleArray,
) {

    /** Branco no ponto, interpolado entre os centros dos blocos vizinhos. */
    fun at(x: Double, y: Double): Double {
        val fx = ((x / BLOCK) - 0.5).coerceIn(0.0, (blocksX - 1).toDouble())
        val fy = ((y / BLOCK) - 0.5).coerceIn(0.0, (blocksY - 1).toDouble())
        val x0 = fx.toInt()
        val y0 = fy.toInt()
        val x1 = minOf(blocksX - 1, x0 + 1)
        val y1 = minOf(blocksY - 1, y0 + 1)
        val tx = fx - x0
        val ty = fy - y0
        val top = field[y0 * blocksX + x0] * (1 - tx) + field[y0 * blocksX + x1] * tx
        val bottom = field[y1 * blocksX + x0] * (1 - tx) + field[y1 * blocksX + x1] * tx
        return top * (1 - ty) + bottom * ty
    }

    companion object {
        /** Lado do bloco em pixels. Alguns milimetros: grande o bastante para conter papel. */
        private const val BLOCK = 128

        /** Percentil que representa o papel dentro de um bloco. */
        private const val PERCENTILE = 0.9

        /**
         * Abaixo disto a captura esta escura demais para significar alguma coisa.
         *
         * Sem este piso, um bloco totalmente preto daria branco proximo de zero e a divisao
         * explodiria a cobertura de qualquer bolha ali para o teto — silenciosamente.
         */
        private const val MIN_WHITE = 40.0

        fun of(region: RectifiedRegion): PaperWhite? {
            val blocksX = (region.width + BLOCK - 1) / BLOCK
            val blocksY = (region.height + BLOCK - 1) / BLOCK
            val field = DoubleArray(blocksX * blocksY)
            val bucket = ArrayList<Int>()

            for (by in 0 until blocksY) {
                for (bx in 0 until blocksX) {
                    bucket.clear()
                    val xEnd = minOf(region.width, (bx + 1) * BLOCK)
                    val yEnd = minOf(region.height, (by + 1) * BLOCK)
                    var y = by * BLOCK
                    while (y < yEnd) {
                        var x = bx * BLOCK
                        while (x < xEnd) {
                            bucket.add(region.luminanceAt(x, y))
                            x += 2
                        }
                        y += 2
                    }
                    if (bucket.isEmpty()) return null
                    bucket.sort()
                    val index = (bucket.size * PERCENTILE).toInt().coerceAtMost(bucket.size - 1)
                    val white = bucket[index].toDouble()
                    if (white < MIN_WHITE) return null
                    field[by * blocksX + bx] = white
                }
            }
            return PaperWhite(blocksX, blocksY, field)
        }
    }
}
