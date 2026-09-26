package com.platos.android.scan

import com.platos.android.vision.FrameOutcome
import com.platos.android.vision.RegiaoDiscursivaNoQuadro
import com.platos.domain.capture.CapturePayload
import com.platos.domain.capture.InterpretedReading
import com.platos.domain.capture.QuestionAnswer
import com.platos.domain.exam.ExamPackage
import com.platos.domain.scoring.AwaitingEssay
import com.platos.domain.scoring.PartialScore
import com.platos.domain.scoring.PartialScoringOutcome
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

    /**
     * O gabarito lido, com tres objetivas certas e `q4` errada: a parcial e 3 de 4.
     *
     * *Ate a 5b-1 as respostas vinham vazias, com a KDoc "as respostas nao importam aqui, porque a
     * sessao nao apura esta prova". Desde a 5b-2 a sessao apura a parcial, e elas importam.*
     */
    private fun gabarito(
        token: String = "tok-a",
        prova: String = pacote.meta.examId,
        respostas: List<QuestionAnswer> = listOf(
            QuestionAnswer.Marcada("q1", "A"),
            QuestionAnswer.Marcada("q2", "C"),
            QuestionAnswer.Marcada("q4", "B"),
            QuestionAnswer.Marcada("q5", "B"),
        ),
    ) = InterpretedReading(payload(0, token, prova), emptyList(), respostas)

    private fun apurada(estado: ScanState.ProvaComDiscursiva): PartialScore {
        val parcial = estado.parcial
        if (parcial !is PartialScoringOutcome.Scored) throw AssertionError("esperava parcial, veio $parcial")
        return parcial.partial
    }

    /** Os numeros fixados da parcial de [gabarito], contra o gabarito da fixture (P4). */
    private fun assertTresDeQuatro(parcial: PartialScore) {
        assertEquals(3, parcial.objectivePoints)
        assertEquals(4, parcial.objectiveMaxScore)
        assertEquals(listOf(AwaitingEssay("d1", 3), AwaitingEssay("d2", 4)), parcial.awaiting)
        assertEquals(11, parcial.maxScore)
    }

    private fun d1(token: String = "tok-a") = RegiaoDiscursivaNoQuadro.Reconhecida(1, "d1", payload(1, token))

    private fun d2(token: String = "tok-a") = RegiaoDiscursivaNoQuadro.Reconhecida(2, "d2", payload(2, token))

    private fun sessaoAberta() = ScanSession(pacote).apply { onPermission(granted = true) }

    private fun reconhecida(estado: ScanState): ScanState.ProvaComDiscursiva =
        estado as? ScanState.ProvaComDiscursiva
            ?: throw AssertionError("esperava a prova com discursiva reconhecida, veio $estado")

    /** Guarda de vacuidade: sem isto, a fixture poderia ser uma prova objetiva e tudo passaria. */
    @Test
    fun `a fixture e de fato uma prova com discursiva`() {
        assertEquals(false, pacote.meta.fullyOfflineGradable)
    }

    /**
     * Cenario "Folha de prova com discursiva no quadro".
     *
     * *Na 5b-1 este teste se chamava "a folha e reconhecida, com o aluno, as regioes e o aviso, sem
     * nota", e era do requisito que a 5b-2 remove. Ele passou a conferir a parcial e a frase nova.*
     */
    @Test
    fun `a folha e reconhecida, com o aluno, as regioes, a parcial nao definitiva e o aviso`() {
        val sessao = sessaoAberta()

        sessao.onFrame(FrameOutcome.Read(gabarito(), listOf(d1())))

        val estado = reconhecida(sessao.state)
        assertEquals("tok-a", estado.aluno)
        assertEquals("lido", estado.gabarito)
        assertEquals(listOf("d1"), estado.discursivas)
        assertTresDeQuatro(apurada(estado))
        assertEquals(
            "A nota nao e definitiva: a correcao das discursivas ainda nao esta disponivel neste " +
                "aparelho. Nada foi guardado.",
            ScanState.ProvaComDiscursiva.AVISO,
        )
    }

    /** Cenario "So as discursivas no quadro": o gabarito deste aluno ainda nao foi lido. */
    @Test
    fun `a folha so com a regiao de d2 tambem e reconhecida, sem gabarito e sem parcial`() {
        val sessao = sessaoAberta()

        sessao.onFrame(FrameOutcome.SoDiscursivas(listOf(d2())))

        val estado = reconhecida(sessao.state)
        assertEquals("tok-a", estado.aluno)
        assertNull(estado.gabarito)
        assertEquals(listOf("d2"), estado.discursivas)
        assertNull(estado.parcial, "sem gabarito lido deste aluno, nao ha parcial")
    }

    /** Cenario "A outra pagina do mesmo aluno mantem a parcial" (decisao 6). */
    @Test
    fun `a outra pagina do mesmo aluno mantem a parcial dele`() {
        val sessao = sessaoAberta()
        sessao.onFrame(FrameOutcome.Read(gabarito(), listOf(d1())))

        sessao.onFrame(FrameOutcome.SoDiscursivas(listOf(d2())))

        val estado = reconhecida(sessao.state)
        assertEquals("tok-a", estado.aluno)
        assertNull(estado.gabarito, "o quadro corrente nao tem o gabarito")
        assertEquals(listOf("d2"), estado.discursivas)
        assertTresDeQuatro(apurada(estado))
    }

    /** Cenario "A parcial recusada mostra o motivo". */
    @Test
    fun `a parcial recusada traz o motivo do dominio, e nenhuma parcial`() {
        val sessao = sessaoAberta()
        val semQ5 = listOf(
            QuestionAnswer.Marcada("q1", "A"),
            QuestionAnswer.Marcada("q2", "C"),
            QuestionAnswer.Marcada("q4", "A"),
        )

        sessao.onFrame(FrameOutcome.Read(gabarito(respostas = semQ5)))

        val estado = reconhecida(sessao.state)
        assertEquals("tok-a", estado.aluno)
        assertEquals(
            PartialScoringOutcome.Rejected("itens objetivos lidos divergem da variante 'v1'; faltando: q5"),
            estado.parcial,
        )
    }

    /** Cenario "Nada e gravado", com a parcial presente. */
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
            // O canario (P13): sem parcial apresentada, "nada e gravado" nao diria nada sobre ela.
            assertTresDeQuatro(apurada(reconhecida(sessao.state)))
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
