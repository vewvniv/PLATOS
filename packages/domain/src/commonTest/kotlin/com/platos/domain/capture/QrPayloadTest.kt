package com.platos.domain.capture

import com.platos.domain.layout.LayoutEngine
import com.platos.domain.layout.PrintTestSheet
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
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
        // Vazios porque este payload nao pertence a aluno nenhum: e o da folha da variante, de
        // que as folhas dos alunos derivam, e tambem o da folha avulsa (§7). Preenchidos, eles vem
        // de uma atribuicao — ver `o payload de uma atribuicao carrega o token e a variante`.
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

    // --- a identidade da folha (tarefa 3.2) ---

    @Test
    fun `o payload de uma atribuicao carrega o token e a variante`() {
        // O escritor da publicacao e chamado, e o leitor confere o que ele produziu — nao ha
        // literal no meio. Um escritor que divergisse do leitor atribuiria a folha ao aluno errado
        // em silencio, e e por isso que os dois moram juntos.
        val payload = readOrFail(
            LayoutEngine.qrPayloadDaAtribuicao("prova-referencia-slice-1", "tok-1", "v1"),
        )

        assertEquals("prova-referencia-slice-1", payload.examShortId)
        assertEquals("tok-1", payload.studentToken)
        assertEquals("v1", payload.variant)
    }

    @Test
    fun `sem atribuicao o campo de aluno fica vazio, e nenhum valor de reserva aparece`() {
        val payload = readOrFail(LayoutEngine.qrPayloadOf("prova-referencia-slice-1", 0))

        // Igualdade com vazio, e nao "nao contem tal palavra": o requisito e sobre **o que pode
        // estar ali**, e qualquer marcador enfiado no campo — `sem-aluno`, `-`, `0` — muda esta
        // asercao. Valor inventado e indistinguivel de token verdadeiro para quem le a folha, que
        // e a mesma familia do nome de reserva que a 4a-zero viu falhar.
        assertEquals("", payload.studentToken)
        assertEquals("", payload.variant)
    }

    @Test
    fun `dois alunos da mesma prova produzem payloads diferentes`() {
        val um = LayoutEngine.qrPayloadDaAtribuicao("prova", "tok-1", "v1")
        val outro = LayoutEngine.qrPayloadDaAtribuicao("prova", "tok-2", "v1")

        assertTrue(um != outro, "os dois alunos receberiam folhas com a mesma identidade: $um")
        assertEquals("tok-1", readOrFail(um).studentToken)
        assertEquals("tok-2", readOrFail(outro).studentToken)
    }

    @Test
    fun `token com o separador e recusado na escrita`() {
        // O separador dentro de um campo torna a leitura de volta ambigua, e a ambiguidade cai na
        // atribuicao: o campo seguinte passaria a ser lido no lugar errado. Recusar na escrita e o
        // que impede a folha de ser impressa assim.
        val erro = assertFailsWith<IllegalArgumentException> {
            QrPayload.of("prova", 0, studentToken = "tok.1")
        }
        assertTrue(
            erro.message!!.contains("token de aluno"),
            "a recusa nao disse que o problema era o token: ${erro.message}",
        )
    }

    @Test
    fun `variante com o separador e recusada na escrita`() {
        val erro = assertFailsWith<IllegalArgumentException> {
            QrPayload.of("prova", 0, variant = "v.1")
        }
        assertTrue(
            erro.message!!.contains("variante"),
            "a recusa nao disse que o problema era a variante: ${erro.message}",
        )
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
