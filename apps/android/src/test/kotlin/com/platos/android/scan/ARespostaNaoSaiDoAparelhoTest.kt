package com.platos.android.scan

import com.platos.android.api.corpoDoEnvio
import com.platos.android.outbox.ResultadoPendente
import com.platos.android.vision.FrameOutcome
import com.platos.android.vision.RegiaoDiscursivaNoQuadro
import com.platos.domain.capture.CapturePayload
import com.platos.domain.capture.InterpretedReading
import com.platos.domain.capture.QuestionAnswer
import com.platos.domain.exam.ExamPackage
import com.platos.domain.scoring.ApuracaoParaEnvio
import java.io.File
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Cenario "A resposta nao sai do aparelho" (`slice-5c-1-a-resposta-fica-no-aparelho`, tarefa 6.1).
 *
 * Um caderno com respostas guardadas completa, e a parcial entregue vira o corpo que o aplicativo envia
 * ([corpoDoEnvio]). O corpo nao carrega imagem nem referencia a arquivo de resposta. A segunda metade da
 * prova — que **nenhum codigo de envio le `respostas/`** — e o `grep` registrado na cobertura: um teste de
 * comportamento nao a substitui, porque afirma sobre este corpo, e nao sobre todos os caminhos.
 */
class ARespostaNaoSaiDoAparelhoTest {

    private val pacote: ExamPackage = Json.decodeFromString(
        File(
            System.getProperty("platos.fixtures") ?: error("propriedade `platos.fixtures` nao definida pelo build"),
            "prova-discursiva.package.json",
        ).readText(),
    )

    private fun payload(regiao: Int) = CapturePayload(pacote.meta.examId, "tok-a", "v1", regiao)

    private val arquivoD1 = "3f2c9a52-1111-4000-8000-0000000000d1.png"
    private val arquivoD2 = "3f2c9a52-2222-4000-8000-0000000000d2.png"

    private fun guardada(arquivo: String) =
        RespostaDoQuadro.Guardada(RespostaGuardada(arquivo, capturadaEm = 1_000L, desvioSinalizado = true, foraPpm = 80_000))

    /** O corpo do envio da parcial de um caderno que completou com as duas respostas guardadas. */
    private fun corpoDoCadernoCompleto(): String {
        val sessao = ScanSession(pacote).apply { onPermission(granted = true) }
        val gabarito = InterpretedReading(
            payload(0),
            emptyList(),
            listOf(
                QuestionAnswer.Marcada("q1", "A"),
                QuestionAnswer.Marcada("q2", "C"),
                QuestionAnswer.Marcada("q4", "B"),
                QuestionAnswer.Marcada("q5", "B"),
            ),
        )
        sessao.onFrame(
            FrameOutcome.Read(gabarito, listOf(RegiaoDiscursivaNoQuadro.Reconhecida(1, "d1", payload(1)))),
            mapOf(1 to guardada(arquivoD1)),
        )
        val entrega = sessao.onFrame(
            FrameOutcome.SoDiscursivas(listOf(RegiaoDiscursivaNoQuadro.Reconhecida(2, "d2", payload(2)))),
            mapOf(2 to guardada(arquivoD2)),
        ) as? ApuracaoNova.DeCaderno ?: throw AssertionError("o caderno precisava completar")

        // As duas respostas estao mesmo no caderno: sem isso, "o corpo nao as traz" nao diria nada.
        val noCaderno = sessao.cadernoAtual!!.regioes.mapNotNull { it.resposta?.arquivo }
        assertEquals(listOf(arquivoD1, arquivoD2), noCaderno)

        return ResultadoPendente(
            captureId = "cap-1",
            organizacao = "org",
            prova = pacote.meta.examId,
            studentToken = entrega.aluno,
            apuradoEm = 1_789_646_400_000L,
            nota = ApuracaoParaEnvio.Parcial(entrega.score),
        ).corpoDoEnvio()
    }

    @Test
    fun `o corpo da parcial de um caderno com respostas nao traz imagem nem referencia a arquivo`() {
        val corpo = corpoDoCadernoCompleto()

        // Canario (P13): o corpo e o da parcial, e tem o conteudo de sempre.
        assertTrue(corpo.contains("\"partial\":true"), corpo)
        assertTrue(corpo.contains("\"capture_id\":\"cap-1\"") || corpo.contains("cap-1"), corpo)

        for (proibido in listOf(arquivoD1, arquivoD2, ".png", "respostas", "iVBOR", "data:image")) {
            assertFalse(corpo.contains(proibido), "o corpo do envio traz '$proibido': $corpo")
        }
    }
}
