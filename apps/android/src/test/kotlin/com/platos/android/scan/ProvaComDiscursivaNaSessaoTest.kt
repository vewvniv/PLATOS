package com.platos.android.scan

import com.platos.android.vision.FrameOutcome
import com.platos.android.vision.RegiaoDiscursivaNoQuadro
import com.platos.domain.capture.CapturePayload
import com.platos.domain.capture.InterpretedReading
import com.platos.domain.exam.ExamPackage
import java.io.File
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * A sessao diante de uma prova com discursiva (`slice-5b-1-o-aparelho-reconhece-a-discursiva`, spec
 * de `scan-session`): reconhece e explica, e nao apura.
 *
 * Os quadros sao montados a mao, como em `ScanSessionTest`: a pergunta aqui e o que a sessao decide,
 * e nao o que a camera ve — isso e de `RegiaoDiscursivaInstrumentedTest`.
 */
class ProvaComDiscursivaNaSessaoTest {

    private val fixtures = File(
        System.getProperty("platos.fixtures")
            ?: error("propriedade `platos.fixtures` nao definida pelo build"),
    )

    private val pacote: ExamPackage =
        Json.decodeFromString(File(fixtures, "prova-discursiva.package.json").readText())

    private fun payload(regiao: Int, token: String = "tok-a", prova: String = pacote.meta.examId) =
        CapturePayload(prova, token, "v1", regiao)

    /** O gabarito lido; as respostas nao importam aqui, porque a sessao nao apura esta prova. */
    private fun gabarito(token: String = "tok-a", prova: String = pacote.meta.examId) =
        InterpretedReading(payload(0, token, prova), emptyList(), emptyList())

    private fun d1(token: String = "tok-a") = RegiaoDiscursivaNoQuadro.Reconhecida(1, "d1", payload(1, token))

    private fun d2(token: String = "tok-a") = RegiaoDiscursivaNoQuadro.Reconhecida(2, "d2", payload(2, token))

    private fun sessaoAberta() = ScanSession(pacote).apply { onPermission(granted = true) }

    private fun reconhecida(estado: ScanState): ScanState.DiscursivaNaoCorrigivel =
        estado as? ScanState.DiscursivaNaoCorrigivel
            ?: throw AssertionError("esperava a prova com discursiva reconhecida, veio $estado")

    /** Guarda de vacuidade: sem isto, a fixture poderia ser uma prova objetiva e tudo passaria. */
    @Test
    fun `a fixture e de fato uma prova com discursiva`() {
        assertEquals(false, pacote.meta.fullyOfflineGradable)
    }

    /** Cenario "Folha de prova com discursiva no quadro". */
    @Test
    fun `a folha e reconhecida, com o aluno, as regioes e o aviso, sem nota`() {
        val sessao = sessaoAberta()

        sessao.onFrame(FrameOutcome.Read(gabarito(), listOf(d1())))

        val estado = reconhecida(sessao.state)
        assertEquals("tok-a", estado.aluno)
        assertEquals("lido", estado.gabarito)
        assertEquals(listOf("d1"), estado.discursivas)
        assertTrue(
            ScanState.DiscursivaNaoCorrigivel.AVISO.contains("ainda nao esta disponivel neste aparelho"),
        )
        assertTrue(ScanState.DiscursivaNaoCorrigivel.AVISO.contains("Nada foi guardado"))
    }

    @Test
    fun `a folha so com a regiao de d2 tambem e reconhecida, sem gabarito`() {
        val sessao = sessaoAberta()

        sessao.onFrame(FrameOutcome.SoDiscursivas(listOf(d2())))

        val estado = reconhecida(sessao.state)
        assertEquals("tok-a", estado.aluno)
        assertNull(estado.gabarito)
        assertEquals(listOf("d2"), estado.discursivas)
    }

    /** Cenario "Nada e gravado". */
    @Test
    fun `nada e entregue para gravar, quantas vezes a folha for reconhecida`() {
        val sessao = sessaoAberta()
        val quadros = listOf(
            FrameOutcome.Read(gabarito(), listOf(d1())),
            FrameOutcome.Read(gabarito()),
            FrameOutcome.SoDiscursivas(listOf(d2())),
        )

        for (quadro in quadros) {
            assertNull(sessao.onFrame(quadro), "a sessao entregou apuracao de prova com discursiva")
            sessao.resume()
            assertNull(sessao.onFrame(quadro), "a sessao entregou apuracao depois de retomar")
        }
    }

    /** Parte da sessao do cenario "Abrir a camera numa prova com discursiva"; a queda e a 3.1. */
    @Test
    fun `a sessao abre e procura a folha`() {
        val sessao = sessaoAberta()
        assertEquals(ScanState.Searching, sessao.state)

        sessao.onFrame(FrameOutcome.NoSheet("nenhum marcador ArUco encontrado na captura"))

        assertEquals(ScanState.Searching, sessao.state)
    }

    @Test
    fun `folha de outra prova continua recusada com o motivo de sempre`() {
        val sessao = sessaoAberta()

        sessao.onFrame(FrameOutcome.Read(gabarito(prova = "prova-referencia-slice-1")))

        val recusa = sessao.state as? ScanState.Rejected
            ?: throw AssertionError("esperava recusa, veio ${sessao.state}")
        assertTrue(recusa.reason.startsWith("a folha e de outra prova"), recusa.reason)
    }

    @Test
    fun `a regiao nao lida aparece com o motivo, ao lado da reconhecida`() {
        val sessao = sessaoAberta()
        val naoLida = RegiaoDiscursivaNoQuadro.NaoLida(2, "d2", "nenhum QR decodificado na ROI que o mapa declara")

        sessao.onFrame(FrameOutcome.Read(gabarito(), listOf(d1(), naoLida)))

        val estado = reconhecida(sessao.state)
        assertEquals(listOf("d1"), estado.discursivas)
        assertEquals(listOf("d2: nenhum QR decodificado na ROI que o mapa declara"), estado.discursivasNaoLidas)
    }
}
