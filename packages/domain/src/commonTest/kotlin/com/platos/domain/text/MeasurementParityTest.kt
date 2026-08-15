package com.platos.domain.text

import com.platos.domain.geometry.Um
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Cobre "Medicao de texto deterministica e independente de plataforma".
 *
 * Este arquivo vive em `commonTest`, entao roda em JVM, em Node pelo alvo JS e no unit test do
 * alvo Android. Os valores esperados sao constantes: se qualquer alvo medisse diferente, o mesmo
 * teste falharia naquele alvo e so nele. E essa a forma da garantia — nao ha o que comparar entre
 * execucoes, porque os tres precisam bater com o mesmo numero.
 */
class MeasurementParityTest {

    private val measurer = TextMeasurer(EmbeddedFont.program)
    private val style = TextStyle.BODY

    @Test
    fun `corpus de referencia mede igual em todo alvo`() {
        // Cobre caixa alta e baixa, digitos, pontuacao, acentuacao portuguesa e espaco.
        assertEquals(Um(5_449), measurer.width("AW", style))
        assertEquals(Um(5_181), measurer.width("ção", style))
        assertEquals(Um(6_823), measurer.width("aaaa", style))
        assertEquals(Um(781), measurer.width(" ", style))
        assertEquals(
            Um(80_327),
            measurer.width("Quantas unidades ha em 1 metro? Aproximadamente.", style),
        )
    }

    @Test
    fun `quebra de linha e identica em todo alvo`() {
        val texto =
            "A soma de dois numeros inteiros consecutivos e sempre impar, porque um deles e par."
        val medido = measurer.measure(texto, style, Um.mm(50))

        assertEquals(
            listOf(
                "A soma de dois numeros inteiros",
                "consecutivos e sempre impar,",
                "porque um deles e par.",
            ),
            medido.lines.map { it.text },
        )
        assertEquals(style.lineHeight * 3, medido.height)
    }

    @Test
    fun `medicao vem da fonte embarcada e nao de fonte do sistema`() {
        // A identidade e lida dos proprios bytes embarcados. Se a medicao viesse de uma API de
        // plataforma, o nome da familia poderia bater por coincidencia, mas nao ha caminho de
        // codigo que consulte fonte instalada: `FontProgram` so enxerga o ByteArray gerado.
        val font = EmbeddedFont.program
        assertEquals(EmbeddedFont.EXPECTED_FAMILY, font.familyName)
        assertEquals(EmbeddedFont.EXPECTED_VERSION, font.versionName)
        assertEquals(1_000, font.unitsPerEm)
        assertTrue(EmbeddedFont.bytes().size == 261_868)
    }
}
