package com.platos.android.scan

import com.platos.android.vision.FrameOutcome
import com.platos.android.vision.RegiaoDiscursivaNoQuadro
import com.platos.domain.capture.CapturePayload
import com.platos.domain.capture.InterpretedReading
import com.platos.domain.capture.QuestionAnswer
import com.platos.domain.exam.ExamPackage
import com.platos.domain.scoring.AwaitingEssay
import com.platos.domain.scoring.ObjectiveScoring
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

        // O motivo sai do dominio, e nao e escrito aqui: a comparacao por igualdade e o que prova que
        // a sessao nao inventa texto proprio (a mesma regra de `ScanSessionTest`).
        val doDominio = ObjectiveScoring.scorePartial(pacote, payload(0), semQ5)
        assertTrue(doDominio is PartialScoringOutcome.Rejected, "o dominio precisa recusar, ou o cenario nao mede")

        sessao.onFrame(FrameOutcome.Read(gabarito(respostas = semQ5)))

        val estado = reconhecida(sessao.state)
        assertEquals("tok-a", estado.aluno)
        assertEquals(doDominio, estado.parcial)
        val motivo = (doDominio as PartialScoringOutcome.Rejected).reason
        assertEquals(
            EstadoDaRegiao.ComProblema(motivo),
            estado.caderno.regioes.single { it.gabarito }.estado,
            "a recusa da parcial e o problema do gabarito no caderno (decisao 6)",
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

    // --- O caderno do aluno (requisito "A completude da folha do aluno e mostrada por regiao") ---
    //
    // Os testes abaixo conferem o caderno por **indice de regiao e estado**, e nao pelo rotulo: o
    // rotulo e so do cenario "O indicador tem o numero impresso", e e isso que deixa a mutacao da 2.4
    // derrubar aquele cenario e so ele. Na fixture, a regiao 0 e o gabarito, a 1 e `d1`, a 2 e `d2`.

    private val naoVista = EstadoDaRegiao.NaoVista
    private val capturada = EstadoDaRegiao.Capturada

    private fun estados(estado: ScanState.ProvaComDiscursiva) =
        estado.caderno.regioes.map { it.regionIndex to it.estado }

    /** Guarda de vacuidade: o caderno so diz algo se a fixture tem o gabarito e duas discursivas. */
    @Test
    fun `a fixture tem gabarito e duas discursivas, nas regioes 0, 1 e 2`() {
        val regioes = pacote.layout.getValue("v1").regions
        assertEquals(listOf(0, 1, 2), regioes.map { it.index })
        assertEquals(listOf(null, "d1", "d2"), regioes.map { it.questionId })
    }

    /** Cenario "Caderno comeca com tudo nao visto". */
    @Test
    fun `o primeiro quadro do aluno captura o gabarito e d1, e d2 fica nao vista`() {
        val sessao = sessaoAberta()

        sessao.onFrame(FrameOutcome.Read(gabarito(), listOf(d1())))

        val estado = reconhecida(sessao.state)
        assertEquals("tok-a", estado.caderno.aluno)
        assertEquals(listOf(0 to capturada, 1 to capturada, 2 to naoVista), estados(estado))
        assertEquals(2, estado.caderno.capturadas)
        assertEquals(3, estado.caderno.esperadas)
    }

    /** Cenario "A segunda pagina completa o caderno". */
    @Test
    fun `a pagina de d2 do mesmo aluno completa o caderno`() {
        val sessao = sessaoAberta()
        sessao.onFrame(FrameOutcome.Read(gabarito(), listOf(d1())))

        sessao.onFrame(FrameOutcome.SoDiscursivas(listOf(d2())))

        val estado = reconhecida(sessao.state)
        assertEquals(listOf(0 to capturada, 1 to capturada, 2 to capturada), estados(estado))
        assertEquals(3, estado.caderno.capturadas)
        assertEquals(3, estado.caderno.esperadas)
    }

    /** Cenario "Regiao com problema", e a regra "com problema passa a capturada". */
    @Test
    fun `a regiao presente e nao lida fica com problema, com o motivo, ate ser lida`() {
        val sessao = sessaoAberta()
        val motivo = "nenhum QR decodificado na ROI que o mapa declara"

        sessao.onFrame(FrameOutcome.Read(gabarito(), listOf(RegiaoDiscursivaNoQuadro.NaoLida(1, "d1", motivo))))

        val comProblema = reconhecida(sessao.state)
        assertEquals(
            listOf(0 to capturada, 1 to EstadoDaRegiao.ComProblema(motivo), 2 to naoVista),
            estados(comProblema),
        )
        assertEquals(1, comProblema.caderno.capturadas, "regiao com problema nao conta como capturada")

        sessao.onFrame(FrameOutcome.Read(gabarito(), listOf(d1())))

        assertEquals(listOf(0 to capturada, 1 to capturada, 2 to naoVista), estados(reconhecida(sessao.state)))
    }

    /** Cenario "Capturada nao volta atras": nem a discursiva, nem o gabarito. */
    @Test
    fun `a regiao capturada continua capturada quando um quadro seguinte nao a le`() {
        val sessao = sessaoAberta()
        sessao.onFrame(FrameOutcome.Read(gabarito(), listOf(d1())))

        sessao.onFrame(
            FrameOutcome.NotRead(
                "a medicao foi recusada",
                listOf(RegiaoDiscursivaNoQuadro.NaoLida(1, "d1", "nenhum QR decodificado na ROI que o mapa declara"), d2()),
            ),
        )

        val estado = reconhecida(sessao.state)
        assertEquals("nao lido: a medicao foi recusada", estado.gabarito, "o quadro de fato nao leu o gabarito")
        assertEquals(listOf(0 to capturada, 1 to capturada, 2 to capturada), estados(estado))
    }

    /** Cenario "Outro aluno comeca outro caderno". */
    @Test
    fun `a folha de outro aluno comeca outro caderno, sem nada do anterior`() {
        val sessao = sessaoAberta()
        sessao.onFrame(FrameOutcome.Read(gabarito(), listOf(d1())))

        sessao.onFrame(FrameOutcome.SoDiscursivas(listOf(d2(token = "tok-b"))))

        val estado = reconhecida(sessao.state)
        assertEquals("tok-b", estado.aluno)
        assertEquals("tok-b", estado.caderno.aluno)
        assertEquals(listOf(0 to naoVista, 1 to naoVista, 2 to capturada), estados(estado))
        assertEquals(1, estado.caderno.capturadas)
        assertNull(estado.parcial, "a parcial de tok-a nao passa para tok-b")
    }
}
