package com.platos.domain.text

import com.platos.domain.geometry.Um

/**
 * Corpo e entrelinha de um trecho de texto.
 *
 * Tamanhos vivem em micrometros, nao em pontos: 9,5 pt sao 3351,38 um, e guardar isso como
 * fracionario reintroduziria justamente o que D-1.2 elimina. O arredondamento ao micrometro
 * acontece uma vez, aqui, e nao a cada medicao.
 */
data class TextStyle(
    val size: Um,
    val lineHeight: Um,
) {
    init {
        require(size > Um.ZERO) { "corpo precisa ser positivo, veio $size" }
        require(lineHeight > Um.ZERO) { "entrelinha precisa ser positiva, veio $lineHeight" }
    }

    companion object {
        /** 9,5 pt (§7), arredondado ao micrometro: 9,5 * 25400 / 72. */
        val BODY_SIZE = Um(3_351)

        /** Entrelinha 1,4 sobre o corpo (§7), arredondada ao micrometro. */
        val BODY_LINE_HEIGHT = Um(4_691)

        /** Corpo do texto da prova. */
        val BODY = TextStyle(size = BODY_SIZE, lineHeight = BODY_LINE_HEIGHT)
    }
}

/** Uma linha ja quebrada, com a largura que ela ocupa. */
data class MeasuredLine(
    val text: String,
    val width: Um,
)

/** Bloco de texto medido: linhas quebradas e a altura total que elas ocupam. */
data class MeasuredText(
    val lines: List<MeasuredLine>,
    val height: Um,
) {
    val widest: Um get() = lines.maxOfOrNull { it.width } ?: Um.ZERO
}

/**
 * Medicao de texto em Kotlin puro sobre o programa de fonte (§6, item 4).
 *
 * Nenhuma API de plataforma participa: e por isso que o resultado e identico em JVM, Android e JS,
 * e e por isso que o LayoutMap pode ser comparado byte a byte entre alvos.
 */
class TextMeasurer(private val font: FontProgram) {

    /** Largura de [text] em micrometros, somando avancos e ajustes de par. */
    fun width(text: String, style: TextStyle): Um {
        if (text.isEmpty()) return Um.ZERO
        var units = 0L
        var previousGlyph = -1
        for (codePoint in codePointsOf(text)) {
            val glyph = font.glyphOf(codePoint)
            if (previousGlyph >= 0) {
                units += font.kerningBetween(previousGlyph, glyph)
            }
            units += font.advanceOf(glyph)
            previousGlyph = glyph
        }
        return toMicrometers(units, style.size)
    }

    /**
     * Quebra [text] em linhas que caibam em [maxWidth], sem hifenizar.
     *
     * A quebra e gulosa e so ocorre em espaco. Uma palavra maior que a largura disponivel fica
     * sozinha na linha e transborda: reportar transbordo e problema de quem valida o layout, e
     * partir a palavra em silencio seria pior que o transbordo.
     */
    fun measure(text: String, style: TextStyle, maxWidth: Um): MeasuredText {
        require(maxWidth > Um.ZERO) { "largura disponivel precisa ser positiva, veio $maxWidth" }

        val lines = mutableListOf<MeasuredLine>()
        for (paragraph in text.split('\n')) {
            val words = paragraph.split(' ').filter { it.isNotEmpty() }
            if (words.isEmpty()) {
                lines += MeasuredLine("", Um.ZERO)
                continue
            }
            var current = StringBuilder()
            var currentWidth = Um.ZERO
            for (word in words) {
                val candidate = if (current.isEmpty()) word else "$current $word"
                val candidateWidth = width(candidate, style)
                if (current.isNotEmpty() && candidateWidth > maxWidth) {
                    lines += MeasuredLine(current.toString(), currentWidth)
                    current = StringBuilder(word)
                    currentWidth = width(word, style)
                } else {
                    current = StringBuilder(candidate)
                    currentWidth = candidateWidth
                }
            }
            lines += MeasuredLine(current.toString(), currentWidth)
        }

        return MeasuredText(lines = lines, height = style.lineHeight * lines.size)
    }

    /**
     * Converte unidades de fonte para micrometros com arredondamento meio-para-cima declarado.
     *
     * `Long` no intermediario porque `unidades * corpo` estoura `Int` em textos longos.
     */
    private fun toMicrometers(units: Long, size: Um): Um {
        val scaled = units * size.raw.toLong()
        val divisor = font.unitsPerEm.toLong()
        val rounded = if (scaled >= 0) {
            (scaled + divisor / 2) / divisor
        } else {
            -((-scaled + divisor / 2) / divisor)
        }
        return Um(rounded.toInt())
    }

    /**
     * Decompoe [text] em code points.
     *
     * Funcao membro, e nao extensao de `String`, de proposito. Como extensao chamada `codePoints`
     * ela era sombreada em JVM e Android por `java.lang.String.codePoints(): IntStream` — o Kotlin
     * aceita `IntStream` no `for` porque `iterator()` serve de operador, entao compilava e passava
     * com um aviso, e so o alvo JS usava esta implementacao.
     *
     * As duas concordavam, inclusive em surrogate solto e par invertido, o que foi verificado
     * antes da correcao. Mas eram duas implementacoes para a operacao que decide quantos code
     * points um texto tem, dentro da medicao que D-1.1 exige ser identica entre alvos: a garantia
     * vinha de concordancia, e nao de existir um caminho so.
     */
    private fun codePointsOf(text: String): List<Int> {
        val points = mutableListOf<Int>()
        var index = 0
        while (index < text.length) {
            val char = text[index]
            val forma = char.isHighSurrogate() &&
                index + 1 < text.length &&
                text[index + 1].isLowSurrogate()
            if (forma) {
                val high = char.code - 0xD800
                val low = text[index + 1].code - 0xDC00
                points += 0x10000 + (high shl 10) + low
                index += 2
            } else {
                points += char.code
                index += 1
            }
        }
        return points
    }
}
