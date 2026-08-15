package com.platos.domain.text

import com.platos.domain.geometry.Um
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class TextMeasurerTest {

    private val measurer = TextMeasurer(EmbeddedFont.program)
    private val style = TextStyle.BODY

    @Test
    fun `largura sai da soma de avancos convertida por aritmetica inteira`() {
        // "AW" = 664 + 962 = 1626 unidades de fonte; 1626 * 3351 / 1000 = 5448,7 -> 5449 um.
        assertEquals(Um(5_449), measurer.width("AW", style))
        // "A" = 664 * 3351 / 1000 = 2225,06 -> 2225 um.
        assertEquals(Um(2_225), measurer.width("A", style))
    }

    @Test
    fun `texto vazio nao ocupa largura`() {
        assertEquals(Um.ZERO, measurer.width("", style))
    }

    @Test
    fun `arredondamento acontece uma vez sobre o total, nao por caractere`() {
        // 509 unidades por 'a'. Quatro caracteres sao 2036 unidades -> 6823 um.
        // Medir um por um e somar daria 4 * 1706 = 6824: a diferenca e exatamente o erro que
        // arredondar a cada passo introduz, e e por isso que a conversao ocorre so no fim.
        assertEquals(Um(1_706), measurer.width("a", style))
        assertEquals(Um(6_823), measurer.width("aaaa", style))
    }

    @Test
    fun `acentuacao portuguesa nao cai em notdef`() {
        // Em Source Serif 4 as formas acentuadas repetem o avanco da base — 'ç' mede como 'c' e
        // 'ã' como 'a' — entao a evidencia de cobertura e o indice de glifo, nao a largura.
        val font = EmbeddedFont.program
        assertTrue(font.glyphOf('ç'.code) != 0)
        assertTrue(font.glyphOf('ã'.code) != 0)
        assertEquals(Um(5_181), measurer.width("ção", style))
    }

    @Test
    fun `quebra ocorre apenas em espaco e respeita a largura`() {
        val texto = "Qual e o resultado da soma de dois numeros inteiros positivos consecutivos"
        val largura = Um.mm(60)
        val medido = measurer.measure(texto, style, largura)

        assertTrue(medido.lines.size > 1, "texto deveria quebrar em mais de uma linha")
        for (linha in medido.lines) {
            assertTrue(linha.width <= largura, "linha excede a largura: `${linha.text}`")
        }
        assertEquals(texto, medido.lines.joinToString(" ") { it.text })
    }

    @Test
    fun `altura e a entrelinha vezes o numero de linhas`() {
        val medido = measurer.measure("uma linha so", style, Um.mm(120))
        assertEquals(1, medido.lines.size)
        assertEquals(style.lineHeight, medido.height)

        val duas = measurer.measure("primeira\nsegunda", style, Um.mm(120))
        assertEquals(2, duas.lines.size)
        assertEquals(style.lineHeight * 2, duas.height)
    }

    @Test
    fun `palavra maior que a largura fica sozinha na linha`() {
        val medido = measurer.measure("a incomensurabilidade", style, Um.mm(8))
        assertEquals(listOf("a", "incomensurabilidade"), medido.lines.map { it.text })
        assertTrue(medido.lines[1].width > Um.mm(8), "a palavra longa deveria transbordar")
    }

    @Test
    fun `medicao e estavel entre chamadas`() {
        val texto = "O mesmo texto medido duas vezes"
        assertEquals(
            measurer.measure(texto, style, Um.mm(40)),
            measurer.measure(texto, style, Um.mm(40)),
        )
    }

    @Test
    fun `largura disponivel nao positiva e recusada`() {
        assertFailsWith<IllegalArgumentException> { measurer.measure("x", style, Um.ZERO) }
    }

    @Test
    fun `corpo e entrelinha precisam ser positivos`() {
        assertFailsWith<IllegalArgumentException> { TextStyle(Um.ZERO, Um(100)) }
        assertFailsWith<IllegalArgumentException> { TextStyle(Um(100), Um.ZERO) }
    }
}
