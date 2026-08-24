package com.platos.android.omr

/**
 * A regiao escaneavel **ja retificada**, em tons de cinza.
 *
 * O buffer cobre exatamente o quadrilatero da regiao: o pixel (0,0) e o centro do marcador
 * superior esquerdo e o pixel (width-1, height-1) e o centro do inferior direito. Por isso a
 * projecao de uma bolha aqui e uma regra de tres, e nao uma homografia — quem desempena e o
 * adaptador OpenCV, como §13 aloca.
 *
 * Um pixel e um byte sem sinal: 0 e preto, 255 e branco.
 */
class RectifiedRegion(
    val width: Int,
    val height: Int,
    private val pixels: ByteArray,
) {
    init {
        require(width > 0 && height > 0) { "regiao vazia: ${width}x$height" }
        require(pixels.size == width * height) {
            "buffer de ${pixels.size} bytes nao cobre ${width}x$height = ${width * height}"
        }
    }

    /** Luminancia em (x,y), de 0 a 255. */
    fun luminanceAt(x: Int, y: Int): Int = pixels[y * width + x].toInt() and 0xFF

    fun contains(x: Int, y: Int): Boolean = x in 0 until width && y in 0 until height
}
