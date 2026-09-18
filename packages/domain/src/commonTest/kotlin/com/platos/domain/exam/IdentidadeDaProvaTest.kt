package com.platos.domain.exam

import com.platos.domain.capture.PayloadReading
import com.platos.domain.capture.QrPayload
import com.platos.domain.fixtures.Fixtures
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Os tres identificadores de prova sao o mesmo valor — **afirmado**, e nao verdade por construcao.
 *
 * **Isto existe no lugar de uma renomeacao, e a recusa esta registrada.** `meta.exam_id` carrega o
 * `short_id`, e o nome esta errado: §5 lista `exam_id` e `short_id` como campos distintos, e o
 * implementado tem um so. ADR-0014 decisao 4 recusou renomear agora — `LayoutMap` tambem tem
 * `exam_id`, e mexer nos dois estenderia a quebra de hash ao golden do layout, a folha de teste e a
 * toda a cadeia de paridade; e renomeacao misturada com mudanca funcional e o que P25 proibe. O nome
 * continua errado, e este teste e o que o prende.
 *
 * **Por que "por construcao" nao basta.** Hoje `ExamPublication.publish` alimenta os tres do mesmo
 * valor, num ponto de escrita so — `Publish.kt` faz `examId = id` duas vezes, e o payload do QR sai
 * do mesmo `id`. Nada mais prende isso. O dia em que os tres divergirem, a folha de uma prova passa
 * a ser aceita contra o pacote de outra: `ScanSession` compara `examPackage.meta.examId` com
 * `payload.examShortId`, e se os dois vierem da mesma fonte errada a comparacao concorda com ela
 * mesma. **A camada (c) de ADR-0013 — a que julga de quem e a folha — deixaria de julgar coisa
 * nenhuma, e sem sintoma.**
 *
 * E o que a KDoc de `EXTRA_SHORT_ID` em `ScanActivity` ja pedia sem ter: *"os dois sao iguais hoje,
 * mas por um contrato implicito que nada nesta base prende"*.
 *
 * **A fixture da turma, e nao a de referencia**, porque e ela que tem atribuicoes — e sem
 * atribuicao nao ha QR de aluno para comparar, que e o terceiro dos tres.
 */
class IdentidadeDaProvaTest {

    private val definicao: ExamDefinition = Json.decodeFromString(
        ExamDefinition.serializer(),
        Fixtures.PROVA_REFERENCIA_JSON,
    )

    private val pacote: ExamPackage = ExamPackage.JSON.decodeFromString(
        ExamPackage.serializer(),
        Fixtures.PROVA_REFERENCIA_TURMA_PACKAGE_JSON,
    )

    @Test
    fun `o identificador do pacote e o da definicao publicada`() {
        assertEquals(
            definicao.id,
            pacote.meta.examId,
            "`meta.exam_id` do pacote divergiu do `id` da definicao que o produziu",
        )
    }

    @Test
    fun `o identificador do pacote e o que viaja no QR de cada atribuicao`() {
        // Guarda de vacuidade: sem atribuicao, o laco abaixo nao afirma nada e passa em silencio.
        assertTrue(
            pacote.assignments.isNotEmpty(),
            "a fixture da turma nao tem atribuicoes, e sem elas este cenario nao afirma nada",
        )

        pacote.assignments.forEach { atribuicao ->
            val qr = atribuicao.qr
                ?: throw AssertionError("atribuicao de `${atribuicao.studentToken}` sem QR")

            // Lido pelo mesmo leitor que o aparelho usa, e nao por `split` local: um leitor proprio
            // aqui concordaria com um escritor errado.
            val leitura = QrPayload.read(qr.payload)
            val payload = when (leitura) {
                is PayloadReading.Read -> leitura.payload
                is PayloadReading.Rejected ->
                    throw AssertionError("QR de `${atribuicao.studentToken}` ilegivel: ${leitura.reason}")
            }

            assertEquals(
                pacote.meta.examId,
                payload.examShortId,
                "o QR de `${atribuicao.studentToken}` diz uma prova, e `meta.exam_id` diz outra",
            )
        }
    }

    @Test
    fun `os tres coincidem, e e essa a afirmacao inteira`() {
        val doQr = pacote.assignments
            .mapNotNull { it.qr }
            .map { QrPayload.read(it.payload) }
            .filterIsInstance<PayloadReading.Read>()
            .map { it.payload.examShortId }
            .toSet()

        assertEquals(
            setOf(definicao.id),
            doQr + pacote.meta.examId,
            "os tres identificadores de prova deixaram de ser um valor so",
        )
    }
}
