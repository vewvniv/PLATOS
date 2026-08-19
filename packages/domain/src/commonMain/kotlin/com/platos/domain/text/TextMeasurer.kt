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

/**
 * Um pedaco de conteudo a medir: palavras ou uma caixa atomica (D-1.6.3).
 *
 * Tipo proprio do pacote `text` de proposito. A caixa vem de formula em linha, mas a medicao
 * nao precisa saber disso — e nao deve: ela empacota caixas, como o Layout Engine.
 */
sealed interface TextPiece {

    /** Texto corrido, quebravel em espacos. */
    data class Words(val text: String) : TextPiece

    /** Caixa indivisivel, alinhada a linha de base pelo proprio deslocamento. */
    data class Box(
        val reference: String,
        val width: Um,
        val height: Um,
        /** Quanto da caixa fica abaixo da linha de base. */
        val baselineOffset: Um,
    ) : TextPiece {
        val ascent: Um get() = height - baselineOffset
        val descent: Um get() = baselineOffset
    }
}

/** Um trecho ja posicionado dentro de uma linha, com o deslocamento resolvido. */
sealed interface LineRun {
    /** Deslocamento horizontal desde o inicio da linha. */
    val x: Um
    val width: Um

    data class Text(override val x: Um, override val width: Um, val text: String) : LineRun

    data class Box(
        override val x: Um,
        override val width: Um,
        val reference: String,
        val height: Um,
        val baselineOffset: Um,
    ) : LineRun
}

/**
 * Uma linha ja quebrada: os trechos que a compoem e o quanto ela ocupa acima e abaixo da
 * **unica** linha de base que todos compartilham (D-1.6.3).
 *
 * [ascent] e [descent] existem para que uma formula mais alta que o texto faca a linha crescer
 * sem que ninguem precise decidir posicao depois. Uma linha so de texto tem `ascent` igual a
 * entrelinha e `descent` zero, que e exatamente a aritmetica que o engine ja fazia antes desta
 * fatia — e por isso o perfil padrao continua reproduzindo o golden byte a byte.
 */
data class MeasuredLine(
    val runs: List<LineRun>,
    val width: Um,
    val ascent: Um,
    val descent: Um,
) {
    val height: Um get() = ascent + descent

    /** O texto da linha, sem as caixas. Existe para diagnostico e para teste. */
    val text: String get() = runs.filterIsInstance<LineRun.Text>().joinToString("") { it.text }
}

/** Bloco de texto medido: linhas quebradas e a altura total que elas ocupam. */
data class MeasuredText(
    val lines: List<MeasuredLine>,
) {
    /**
     * Soma das alturas das linhas, e **nao** entrelinha vezes numero de linhas.
     *
     * A multiplicacao valia enquanto toda linha tinha a mesma altura. Uma formula em linha mais
     * alta que o texto quebra essa suposicao, e ela estava embutida na paginacao inteira.
     */
    val height: Um get() = lines.fold(Um.ZERO) { total, line -> total + line.height }

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
    fun measure(text: String, style: TextStyle, maxWidth: Um): MeasuredText =
        measure(listOf(TextPiece.Words(text)), style, maxWidth)

    /**
     * Quebra uma sequencia de pedacos — texto e caixas — em linhas (D-1.6.3).
     *
     * Uma caixa e **indivisivel**: ela ocupa largura como uma palavra ocuparia e nunca e partida
     * entre duas linhas.
     *
     * O texto continua sendo medido em pedacos acumulados, e nao palavra a palavra somando o
     * espaco: e o par de kerning na juncao que faz a diferenca, e medir de outro jeito mudaria a
     * largura de linhas que nao tem formula nenhuma. Uma caixa **encerra** o trecho de texto
     * corrente, porque ela quebra a sequencia de glifos de qualquer forma.
     */
    fun measure(pieces: List<TextPiece>, style: TextStyle, maxWidth: Um): MeasuredText {
        require(maxWidth > Um.ZERO) { "largura disponivel precisa ser positiva, veio $maxWidth" }

        val lines = mutableListOf<MeasuredLine>()
        var runs = mutableListOf<LineRun>()
        var cursor = Um.ZERO
        // Trecho de texto em construcao: o texto acumulado e onde ele comeca na linha.
        var runText = StringBuilder()
        var runStart = Um.ZERO

        fun fecharTrecho() {
            if (runText.isNotEmpty()) {
                val largura = width(runText.toString(), style)
                runs += LineRun.Text(runStart, largura, runText.toString())
                cursor = runStart + largura
                runText = StringBuilder()
            }
            runStart = cursor
        }

        fun fecharLinha() {
            fecharTrecho()
            val ascent = runs.fold(style.lineHeight) { maior, run ->
                val a = if (run is LineRun.Box) run.height - run.baselineOffset else style.lineHeight
                if (a > maior) a else maior
            }
            val descent = runs.fold(Um.ZERO) { maior, run ->
                val d = if (run is LineRun.Box) run.baselineOffset else Um.ZERO
                if (d > maior) d else maior
            }
            lines += MeasuredLine(runs = runs.toList(), width = cursor, ascent = ascent, descent = descent)
            runs = mutableListOf()
            cursor = Um.ZERO
            runStart = Um.ZERO
        }

        for ((index, piece) in pieces.withIndex()) {
            when (piece) {
                is TextPiece.Words -> {
                    val paragraphs = piece.text.split('\n')
                    for ((p, paragraph) in paragraphs.withIndex()) {
                        if (p > 0) fecharLinha()
                        val words = paragraph.split(' ').filter { it.isNotEmpty() }
                        for (word in words) {
                            val candidate = if (runText.isEmpty()) word else "$runText $word"
                            val candidateWidth = width(candidate, style)
                            if (runText.isNotEmpty() && runStart + candidateWidth > maxWidth) {
                                fecharLinha()
                                runText = StringBuilder(word)
                            } else if (runText.isEmpty() && runs.isNotEmpty() &&
                                runStart + candidateWidth > maxWidth
                            ) {
                                // A linha ja tem caixa e a palavra nao cabe depois dela.
                                fecharLinha()
                                runText = StringBuilder(word)
                            } else {
                                runText = StringBuilder(candidate)
                            }
                        }
                    }
                }

                is TextPiece.Box -> {
                    fecharTrecho()
                    if (runs.isNotEmpty() && cursor + piece.width > maxWidth) {
                        fecharLinha()
                    }
                    runs += LineRun.Box(
                        x = cursor,
                        width = piece.width,
                        reference = piece.reference,
                        height = piece.height,
                        baselineOffset = piece.baselineOffset,
                    )
                    cursor += piece.width
                    runStart = cursor
                }
            }
            if (index == pieces.lastIndex) fecharLinha()
        }

        if (lines.isEmpty()) lines += MeasuredLine(emptyList(), Um.ZERO, style.lineHeight, Um.ZERO)
        return MeasuredText(lines)
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
