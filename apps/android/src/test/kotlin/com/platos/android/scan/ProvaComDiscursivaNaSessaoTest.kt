package com.platos.android.scan

import com.platos.android.vision.FrameOutcome
import com.platos.android.vision.RegiaoDiscursivaNoQuadro
import com.platos.domain.capture.CapturePayload
import com.platos.domain.capture.InterpretedReading
import com.platos.domain.capture.QuestionAnswer
import com.platos.domain.exam.ExamPackage
import com.platos.domain.exam.folhaDaAtribuicao
import com.platos.domain.layout.DrawText
import com.platos.domain.layout.LayoutEngine
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

    /**
     * Cenario "Nada e gravado", com a parcial presente **e o caderno ainda incompleto** — o
     * terceiro quadro (`SoDiscursivas(d2())`), que completaria o caderno com d1 e o gabarito ja
     * capturados, saiu deste teste na `slice-5b-4-envio-da-parcial`: e exatamente o cenario que ela
     * passa a entregar, coberto em `o caderno completo e entregue quando a ultima regiao e
     * capturada`.
     */
    @Test
    fun `nada e entregue para gravar enquanto o caderno nao completa, quantas vezes a folha for reconhecida`() {
        val sessao = sessaoAberta()
        val quadros = listOf(
            FrameOutcome.Read(gabarito(), listOf(d1())),
            FrameOutcome.Read(gabarito()),
        )

        // O canario (P13): a leitura do gabarito rende parcial no dominio. Sem isso, "nada e gravado"
        // nao diria nada sobre uma folha que tem o que apurar. Ele consulta o dominio, e nao a sessao,
        // de proposito: a 2.5 desliga a camada da sessao, e um canario que dependesse dela derrubaria
        // este teste pelo motivo errado (`rigorous.md` §3).
        assertTresDeQuatro(
            (ObjectiveScoring.scorePartial(pacote, gabarito().payload, gabarito().answers) as PartialScoringOutcome.Scored)
                .partial,
        )

        for (quadro in quadros) {
            assertNull(sessao.onFrame(quadro), "a sessao entregou apuracao de prova com discursiva")
            sessao.resume()
            assertNull(sessao.onFrame(quadro), "a sessao entregou apuracao depois de retomar")
        }
        assertEquals(2, reconhecida(sessao.state).caderno.capturadas, "guarda de vacuidade: o caderno continua incompleto")
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

    /**
     * Cenario "O indicador tem o numero impresso" (decisao 5).
     *
     * O oraculo e fixado: na folha da fixture, `d1` e impressa como questao 3 e `d2` como questao 6.
     * O teste abaixo deste e quem prende esses numeros ao texto da folha.
     */
    @Test
    fun `o indicador de cada discursiva traz o numero que a questao tem na folha impressa`() {
        val sessao = sessaoAberta()

        sessao.onFrame(FrameOutcome.Read(gabarito(), listOf(d1())))

        val rotulos = reconhecida(sessao.state).caderno.regioes.map { it.regionIndex to it.rotulo }
        assertEquals(listOf(0 to "Gabarito", 1 to "3", 2 to "6"), rotulos)
    }

    /**
     * A conferencia que a P28 exige do numero do indicador (decisao 5): a chave de `positions` de cada
     * discursiva e o numero impresso antes do enunciado dela, na folha de `tok-a`.
     *
     * **A folha e a do pacote, e nao um PDF**: o `LayoutMap` da atribuicao e a fonte geometrica que os
     * dois renderizadores desenham, e o texto de cada `DrawText` e o que sai no papel (a paridade e a
     * fidelidade do CI prendem os renderizadores a ele). O numero e achado **pela geometria**, e nao
     * pelo `id` da primitiva: e o texto na mesma linha de base da primeira linha do enunciado, logo a
     * esquerda dela.
     *
     * Hoje os dois coincidem por construcao, porque o motor numera pela ordem de `positions`. Quando a
     * paginacao (ADR-0019) passar a declarar o numero no mapa, e este teste que cai e obriga a trocar a
     * fonte do indicador.
     */
    @Test
    fun `a chave de positions de cada discursiva e o numero impresso antes do enunciado dela`() {
        val folha = requireNotNull(pacote.folhaDaAtribuicao("tok-a")) { "a fixture nao tem a atribuicao tok-a" }
        val posicaoDe = pacote.variants.single().positions.entries.associate { (posicao, item) -> item to posicao }
        val discursivas = folha.regions.filter { it.kind == LayoutEngine.ESSAY_KIND }
        assertEquals(listOf("d1", "d2"), discursivas.map { it.questionId }, "guarda de vacuidade")

        for (regiao in discursivas) {
            val item = pacote.items.single { it.id == regiao.questionId }
            val textos = folha.pages.single { it.index == regiao.page }.primitives.filterIsInstance<DrawText>()
            val primeiraLinha = textos.filter { it.text.length > 10 && item.statement.startsWith(it.text) }
            assertEquals(1, primeiraLinha.size, "a primeira linha do enunciado de ${item.id}: $primeiraLinha")
            val linha = primeiraLinha.single()
            val numero = textos
                .filter { it.baseline == linha.baseline && it.x < linha.x }
                .maxByOrNull { it.x }
                ?: throw AssertionError("nada impresso antes do enunciado de ${item.id}")

            assertEquals(
                "${posicaoDe.getValue(item.id)}.",
                numero.text,
                "o numero impresso antes de ${item.id} tem de ser a chave de positions dele",
            )
        }
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

    // --- Guardar e retomar (`slice-5b-3-guardar-a-parcial-e-o-caderno`) ---

    /**
     * Cenarios "O aplicativo fecha no meio da leitura de um aluno" e "O caderno guardado sobrevive
     * ao fechamento do aplicativo".
     *
     * O caderno da primeira sessao (`cadernoAtual`) e o que uma implementacao real guardaria no
     * `onStop`; a segunda sessao, construida so com ele — nenhum quadro entregue —, e o que a
     * `Activity` teria na reabertura. `onPermission(granted = true)` depois disso e o que `onCreate`
     * chama de verdade, e e ele que confere a guarda nova de [ScanSession.onPermission]: sem
     * respeitar `holdsResult`, o caderno retomado sumiria nesta mesma chamada.
     */
    @Test
    fun `a sessao nova retoma o caderno guardado, aluno, regioes e parcial, antes de qualquer quadro`() {
        val original = sessaoAberta()
        original.onFrame(FrameOutcome.Read(gabarito(), listOf(d1())))
        val guardado = requireNotNull(original.cadernoAtual) { "a sessao original nao capturou nada" }

        val retomada = ScanSession(pacote, cadernoInicial = guardado)

        val antesDaPermissao = reconhecida(retomada.state)
        assertEquals("tok-a", antesDaPermissao.aluno)
        assertEquals(listOf(0 to capturada, 1 to capturada, 2 to naoVista), estados(antesDaPermissao))
        assertTresDeQuatro(apurada(antesDaPermissao))

        retomada.onPermission(granted = true)

        assertEquals(antesDaPermissao, reconhecida(retomada.state), "a permissao concedida nao apaga o retomado")
    }

    /** Prova so objetiva: um `cadernoInicial` (de outra prova) e ignorado, e a sessao abre normal. */
    @Test
    fun `prova so objetiva ignora um caderno inicial, e abre procurando como sempre`() {
        val soObjetiva = Json.decodeFromString<ExamPackage>(
            File(fixtures, "prova-referencia.package.json").readText(),
        )
        val cadernoDeOutraProva = requireNotNull(sessaoAberta().apply {
            onFrame(FrameOutcome.Read(gabarito(), listOf(d1())))
        }.cadernoAtual)

        val sessao = ScanSession(soObjetiva, cadernoInicial = cadernoDeOutraProva)
        sessao.onPermission(granted = true)

        assertEquals(ScanState.Searching, sessao.state)
    }

    /** Cenario "Trocar de aluno antes de fechar continua substituindo o caderno". */
    @Test
    fun `cadernoAtual e do ultimo aluno, mesmo depois de trocar de aluno antes de fechar`() {
        val sessao = sessaoAberta()
        sessao.onFrame(FrameOutcome.Read(gabarito(), listOf(d1())))

        sessao.onFrame(FrameOutcome.SoDiscursivas(listOf(d2(token = "tok-b"))))

        val guardaria = requireNotNull(sessao.cadernoAtual)
        assertEquals("tok-b", guardaria.aluno)
        assertEquals(listOf(0 to naoVista, 1 to naoVista, 2 to capturada), guardaria.regioes.map { it.regionIndex to it.estado })
    }

    /** Prova sem discursiva alguma reconhecida ainda: `cadernoAtual` continua nulo. */
    @Test
    fun `cadernoAtual e nulo antes de qualquer regiao ser reconhecida`() {
        val sessao = sessaoAberta()

        assertNull(sessao.cadernoAtual)
    }

    // --- Envio da parcial (`slice-5b-4-envio-da-parcial`) ---

    /** Cenario "O caderno completo e entregue para gravacao". */
    @Test
    fun `o caderno completo e entregue quando a ultima regiao e capturada`() {
        val sessao = sessaoAberta()
        sessao.onFrame(FrameOutcome.Read(gabarito(), listOf(d1())))

        val entrega = sessao.onFrame(FrameOutcome.SoDiscursivas(listOf(d2())))

        val deCaderno = entrega as? ApuracaoNova.DeCaderno
            ?: throw AssertionError("esperava a entrega do caderno completo, veio $entrega")
        assertEquals("tok-a", deCaderno.aluno)
        assertTresDeQuatro(deCaderno.score)
        assertTrue(reconhecida(sessao.state).caderno.entregue, "o caderno guardado precisa marcar a entrega")
    }

    /** Cenario "Confirmar um caderno ja completo nao duplica o envio". */
    @Test
    fun `um caderno ja completo nao e entregue de novo em quadros seguintes`() {
        val sessao = sessaoAberta()
        sessao.onFrame(FrameOutcome.Read(gabarito(), listOf(d1())))
        sessao.onFrame(FrameOutcome.SoDiscursivas(listOf(d2())))

        val depois = sessao.onFrame(FrameOutcome.SoDiscursivas(listOf(d2())))

        assertNull(depois, "o caderno ja foi entregue, e o mesmo quadro nao pode entregar de novo")
    }

    /** Cenario "Caderno incompleto substituido por outro aluno nao e entregue". */
    @Test
    fun `trocar de aluno antes de completar nao entrega o caderno anterior`() {
        val sessao = sessaoAberta()
        sessao.onFrame(FrameOutcome.Read(gabarito(), listOf(d1())))

        val aoTrocar = sessao.onFrame(FrameOutcome.SoDiscursivas(listOf(d2(token = "tok-b"))))

        assertNull(aoTrocar, "o caderno de tok-a estava incompleto, e nao pode ter sido entregue")
    }

    /**
     * A parcial recusada nao conta como completude entregavel, mesmo com as tres regioes
     * capturadas — a protecao e do indicador de regiao ("capturada nao volta atras"), e nao de
     * `parcial`, que pode regredir para uma recusa (`Caderno.depoisDe`). Sem a guarda extra do
     * design decisao 1, este cenario entregaria uma parcial que na verdade foi recusada.
     *
     * A primeira folha deixa o caderno em 2 de 3 (gabarito valido e `d1`), para que a completude so
     * aconteca na segunda passada — junto com a recusa.
     */
    @Test
    fun `caderno que completa com a parcial recusada nao e entregue`() {
        val sessao = sessaoAberta()
        sessao.onFrame(FrameOutcome.Read(gabarito(), listOf(d1())))
        val semQ5 = listOf(
            QuestionAnswer.Marcada("q1", "A"),
            QuestionAnswer.Marcada("q2", "C"),
            QuestionAnswer.Marcada("q4", "A"),
        )

        val entrega = sessao.onFrame(FrameOutcome.Read(gabarito(respostas = semQ5), listOf(d1(), d2())))

        assertNull(entrega, "a parcial recusada nao pode virar entrega, mesmo com as tres regioes capturadas")
        val estado = reconhecida(sessao.state)
        assertEquals(3, estado.caderno.capturadas, "guarda de vacuidade: as tres regioes ficam capturadas")
        assertTrue(estado.parcial is PartialScoringOutcome.Rejected, "guarda de vacuidade: a parcial precisa estar recusada")
    }
}
