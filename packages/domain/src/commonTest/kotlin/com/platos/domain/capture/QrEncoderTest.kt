package com.platos.domain.capture

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * A matriz esperada abaixo foi conferida por um decodificador independente (`jsQR`), que devolveu
 * exatamente o payload de origem. Congelar a matriz aqui transforma aquela conferencia pontual em
 * regressao permanente e, por rodar em `commonTest`, exige que os tres alvos produzam o mesmo QR.
 */
class QrEncoderTest {

    private val payload = "prova-demo.0.4F2A"

    private val expected = listOf(
        "1111111010111010001111111",
        "1000001001100000101000001",
        "1011101000110101101011101",
        "1011101011011011101011101",
        "1011101010011110101011101",
        "1000001011100110001000001",
        "1111111010101010101111111",
        "0000000010100010100000000",
        "1000101111001110101111001",
        "0101000100101001000011100",
        "0100111101000111101100000",
        "0101010101111100001110111",
        "0011001000001100001101101",
        "1110010010100110100011010",
        "0011001011000001101101000",
        "0001110100101011011100110",
        "1100101101010110111110101",
        "0000000010001001100010010",
        "1111111010111110101010100",
        "1000001001110101100011100",
        "1011101010101100111110101",
        "1011101000100110101101111",
        "1011101001100000001100110",
        "1000001001001011010000110",
        "1111111011110110001010111",
    )

    private fun render(matrix: QrMatrix): List<String> =
        matrix.modules.map { row -> row.joinToString("") { if (it) "1" else "0" } }

    @Test
    fun `matriz conferida por decodificador externo permanece identica`() {
        val matrix = QrEncoder.encode(payload)
        assertEquals(25, matrix.size)
        assertEquals(expected, render(matrix))
    }

    @Test
    fun `codificacao e estavel entre chamadas`() {
        assertEquals(render(QrEncoder.encode(payload)), render(QrEncoder.encode(payload)))
    }

    @Test
    fun `versao cresce com o tamanho do payload`() {
        assertEquals(21, QrEncoder.encode("x").size) // versao 1
        assertEquals(29, QrEncoder.encode("A".repeat(40)).size) // versao 3
        assertEquals(57, QrEncoder.encode("C".repeat(213)).size) // versao 10
    }

    @Test
    fun `localizadores estao nos tres cantos`() {
        val matrix = render(QrEncoder.encode(payload))
        val size = matrix.size
        for (corner in listOf(0 to 0, 0 to size - 7, size - 7 to 0)) {
            val (row, column) = corner
            // Anel externo preto, anel interno branco, nucleo preto.
            assertTrue(matrix[row][column] == '1', "canto $corner sem localizador")
            assertTrue(matrix[row + 1][column + 1] == '0', "canto $corner sem anel branco")
            assertTrue(matrix[row + 3][column + 3] == '1', "canto $corner sem nucleo")
        }
    }

    @Test
    fun `payload maior que a versao 10 e recusado explicitamente`() {
        val falha = assertFailsWith<QrCapacityException> { QrEncoder.encode("D".repeat(214)) }
        assertTrue(falha.message!!.contains("versao 10"), falha.message!!)
    }
}
