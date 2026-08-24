package com.platos.domain.capture

import com.platos.domain.layout.LayoutEngine
import com.platos.domain.layout.PrintTestSheet
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * O payload auto-descritivo do QR, escrito e lido pelo mesmo lugar (§8).
 *
 * O que estes testes protegem nao e o formato: e a **simetria**. Um leitor que divirja dos
 * escritores nao quebra nada visivel — ele atribui a folha ao aluno errado, em silencio, no
 * aparelho do professor. Por isso quase todo teste aqui e de ida e volta, e nao de literal.
 */
class QrPayloadTest {

    private fun read(text: String): PayloadReading = QrPayload.read(text)

    private fun readOrFail(text: String): CapturePayload {
        val reading = read(text)
        assertTrue(reading is PayloadReading.Read, "esperava aceite, veio $reading")
        return reading.payload
    }

    private fun rejection(text: String): String {
        val reading = read(text)
        assertTrue(reading is PayloadReading.Rejected, "esperava recusa, veio $reading")
        return reading.reason
    }

    @Test
    fun `ida e volta devolve exatamente os campos declarados`() {
        val payload = readOrFail(QrPayload.of("prova-referencia-slice-1", 0))

        assertEquals("prova-referencia-slice-1", payload.examShortId)
        assertEquals(0, payload.regionIndex)
        // Vazios de proposito enquanto exam_assignment e variantes nao existirem (fatia 7).
        assertEquals("", payload.studentToken)
        assertEquals("", payload.variant)
    }

    @Test
    fun `ida e volta vale para qualquer indice de regiao`() {
        // §8 da 4 marcadores por regiao no DICT_5X5_100, entao 25 regioes cabem. Todas rodam.
        for (index in 0 until 25) {
            assertEquals(index, readOrFail(QrPayload.of("prova", index)).regionIndex)
        }
    }

    @Test
    fun `o payload que a prova imprime e legivel`() {
        // Nao e literal: o escritor da prova e chamado, e o leitor confere o que ele produziu.
        val payload = readOrFail(LayoutEngine.qrPayloadOf("prova-referencia-slice-1", 0))
        assertEquals("prova-referencia-slice-1", payload.examShortId)
        assertEquals(0, payload.regionIndex)
    }

    @Test
    fun `o payload que a folha de teste imprime e legivel`() {
        val payload = readOrFail(PrintTestSheet.qrPayload())
        assertEquals(PrintTestSheet.SHEET_ID, payload.examShortId)
        assertEquals(0, payload.regionIndex)
    }

    @Test
    fun `corpo alterado sem recalcular o CRC e recusado`() {
        // O defeito que o CRC existe para pegar: um caractere trocado na leitura optica.
        val original = QrPayload.of("prova-referencia-slice-1", 0)
        val corrompido = original.replaceFirst("prova", "provb")

        assertTrue(
            rejection(corrompido).contains("CRC"),
            "a recusa precisa dizer que foi o CRC, e nao um motivo generico",
        )
    }

    @Test
    fun `corpo e CRC alterados juntos sao aceitos`() {
        // O par positivo. Sem ele, a recusa acima poderia estar recusando tudo — inclusive o
        // payload correto — e o teste continuaria verde.
        val payload = readOrFail(QrPayload.of("provb-referencia-slice-1", 0))
        assertEquals("provb-referencia-slice-1", payload.examShortId)
    }

    @Test
    fun `CRC trocado sozinho e recusado`() {
        val original = QrPayload.of("prova", 3)
        val semCrc = original.substringBeforeLast('.')
        assertTrue(rejection("$semCrc.0000").contains("CRC"))
    }

    @Test
    fun `payload com numero de campos diferente de cinco e recusado`() {
        assertTrue(rejection("prova..0.ABCD").contains("campos"))
        assertTrue(rejection("prova....0.ABCD").contains("campos"))
        assertTrue(rejection("").contains("campos"))
    }

    @Test
    fun `indice de regiao que nao e numero e recusado`() {
        // Com o CRC certo: sem isso o teste passaria pelo motivo errado, acusando o CRC.
        val body = "prova...zero"
        assertTrue(rejection("$body.${QrPayload.crc16(body)}").contains("indice de regiao"))
    }

    @Test
    fun `indice de regiao negativo e recusado na leitura e na escrita`() {
        val body = "prova...-1"
        assertTrue(rejection("$body.${QrPayload.crc16(body)}").contains("indice de regiao"))

        val erro = kotlin.runCatching { QrPayload.of("prova", -1) }.exceptionOrNull()
        assertTrue(erro is IllegalArgumentException, "escrever indice negativo precisa falhar")
    }

    @Test
    fun `identificador vazio e recusado`() {
        val body = "...0"
        assertTrue(rejection("$body.${QrPayload.crc16(body)}").contains("identificador de prova"))
    }

    @Test
    fun `identificador com o separador e recusado na escrita`() {
        // Um ponto dentro do campo tornaria a leitura de volta ambigua: o codec recusa na origem,
        // que e onde da para dizer o que esta errado.
        val erro = kotlin.runCatching { QrPayload.of("prova.slice.1", 0) }.exceptionOrNull()
        assertTrue(erro is IllegalArgumentException, "separador dentro do campo precisa falhar")
    }

    @Test
    fun `o CRC tem sempre quatro digitos hexadecimais maiusculos`() {
        // Comprimento fixo e o que torna a leitura de volta trivial. Um CRC que perdesse o zero a
        // esquerda deslocaria o campo e so falharia em 1 payload de cada 16.
        for (index in 0 until 25) {
            val crc = QrPayload.of("prova-$index", index).substringAfterLast('.')
            assertEquals(4, crc.length, "CRC de largura errada em prova-$index: '$crc'")
            assertTrue(crc.all { it in '0'..'9' || it in 'A'..'F' }, "CRC nao hexadecimal: '$crc'")
        }
    }
}
