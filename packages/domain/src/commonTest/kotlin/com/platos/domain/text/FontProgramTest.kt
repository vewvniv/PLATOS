package com.platos.domain.text

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Os valores esperados aqui vieram de uma leitura independente das tabelas `cmap` e `hmtx` do
 * mesmo TTF, feita fora do Kotlin. Se o parser daqui e o leitor de referencia concordam, o parser
 * esta lendo a fonte, e nao apenas sendo consistente consigo mesmo.
 */
class FontProgramTest {

    private val font = EmbeddedFont.program

    @Test
    fun `cabecalho da fonte bate com a leitura de referencia`() {
        assertEquals(1_000, font.unitsPerEm)
        assertEquals("Source Serif 4", font.familyName)
        assertEquals(EmbeddedFont.EXPECTED_VERSION, font.versionName)
    }

    @Test
    fun `indice de glifo bate com a leitura de referencia`() {
        assertEquals(2, font.glyphOf('A'.code))
        assertEquals(28, font.glyphOf('a'.code))
        assertEquals(24, font.glyphOf('W'.code))
        assertEquals(1, font.glyphOf(' '.code))
        assertEquals(254, font.glyphOf('ç'.code))
        assertEquals(234, font.glyphOf('ã'.code))
    }

    @Test
    fun `avanco de glifo conhecido bate com a leitura de referencia`() {
        assertEquals(664, font.advanceOf(font.glyphOf('A'.code)))
        assertEquals(509, font.advanceOf(font.glyphOf('a'.code)))
        assertEquals(962, font.advanceOf(font.glyphOf('W'.code)))
        assertEquals(298, font.advanceOf(font.glyphOf('i'.code)))
        assertEquals(233, font.advanceOf(font.glyphOf(' '.code)))
        assertEquals(902, font.advanceOf(font.glyphOf('M'.code)))
        assertEquals(300, font.advanceOf(font.glyphOf('.'.code)))
        assertEquals(500, font.advanceOf(font.glyphOf('1'.code)))
        assertEquals(488, font.advanceOf(font.glyphOf('ç'.code)))
    }

    @Test
    fun `caractere fora da cobertura cai em notdef`() {
        // Bloco de uso privado: a fonte nao cobre.
        assertEquals(0, font.glyphOf(0xE000))
        // Fora do BMP, que o formato 4 nao enderecca.
        assertEquals(0, font.glyphOf(0x1F600))
    }

    @Test
    fun `fonte sem tabela kern legada tem ajuste zero`() {
        // Source Serif 4 traz apenas GPOS. D-1.3 aceita isso: o ajuste e zero e, sobretudo,
        // e o mesmo zero nos tres alvos.
        assertFalse(font.hasKerning)
        assertEquals(0, font.kerningBetween(font.glyphOf('A'.code), font.glyphOf('W'.code)))
    }

    @Test
    fun `arquivo truncado falha com erro identificavel`() {
        val truncated = EmbeddedFont.bytes().copyOf(64)
        val failure = assertFailsWith<FontFormatException> { FontProgram.parse(truncated) }
        assertTrue(failure.message!!.contains("fora dos limites"), failure.message!!)
    }

    @Test
    fun `arquivo vazio falha com erro identificavel`() {
        val failure = assertFailsWith<FontFormatException> { FontProgram.parse(ByteArray(0)) }
        assertTrue(failure.message!!.contains("curto demais"), failure.message!!)
    }

    @Test
    fun `assinatura desconhecida falha com erro identificavel`() {
        val garbage = ByteArray(64) { 0x7F }
        val failure = assertFailsWith<FontFormatException> { FontProgram.parse(garbage) }
        assertTrue(failure.message!!.contains("assinatura sfnt"), failure.message!!)
    }

    @Test
    fun `fonte de tamanho divergente e recusada na carga`() {
        val failure = assertFailsWith<FontFormatException> {
            EmbeddedFont.load(EmbeddedFont.bytes().copyOf(EmbeddedFont.bytes().size - 1))
        }
        assertTrue(failure.message!!.contains("esperados"), failure.message!!)
    }

    @Test
    fun `identidade da fonte embarcada e estavel`() {
        assertEquals("SourceSerif4-Regular.ttf", EmbeddedFont.fileName)
        assertEquals(
            "e5a4ee6a3d87bb9024796be390c6771e2a0eb1883dae25effaf57ca01668e24b",
            EmbeddedFont.sha256,
        )
        assertEquals(261_868, EmbeddedFont.bytes().size)
    }
}
