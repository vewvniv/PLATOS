package com.platos.android.scan

import com.platos.android.vision.FrameOutcome
import com.platos.domain.capture.BubbleJudgement
import com.platos.domain.capture.CapturePayload
import com.platos.domain.capture.InterpretedReading
import com.platos.domain.capture.OmrMeasurement
import com.platos.domain.capture.OmrReading
import com.platos.domain.capture.OmrThreshold
import com.platos.domain.capture.QuestionAnswer
import com.platos.domain.capture.SheetInterpreter
import com.platos.domain.capture.InterpretationOutcome
import com.platos.domain.exam.ExamPackage
import com.platos.domain.layout.InkBudget
import java.io.File
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * A sessao de escaneamento, sem camera e sem aparelho.
 *
 * O pacote e o de referencia versionado, e nao um montado a mao: a nota e apurada contra o mesmo
 * `answer_key` que a folha impressa usa, e o `exam_short_id` conferido e o real. O que e montado
 * aqui sao as **leituras** — e so elas —, porque produzir leitura de verdade exige imagem, e imagem
 * exige aparelho.
 *
 * Os motivos de recusa que chegam a tela **nao sao escritos neste arquivo**: os que vem do dominio
 * sao produzidos chamando o dominio, e comparados por igualdade. Um teste que escrevesse a frase
 * esperada a mao passaria com a tela inventando texto proprio, que e justamente o que a spec
 * proibe.
 */
class ScanSessionTest {

    private val fixtures = File(
        System.getProperty("platos.fixtures")
            ?: error("propriedade `platos.fixtures` nao definida pelo build"),
    )

    private val pacote: ExamPackage = Json.decodeFromString(
        File(fixtures, "prova-referencia.package.json").readText(),
    )

    private val regiao = pacote.layout.getValue(VARIANTE).regions.single()
    private val limiar = OmrThreshold.MEDIDO_NA_FATIA_3B

    private fun payload(token: String = "") =
        CapturePayload(pacote.meta.examId, token, "", regiao.index)

    /**
     * Uma leitura interpretada de mentira, com cobertura de verdade.
     *
     * As bolhas saem do `LayoutMap` do pacote, e a cobertura sai de duas nuvens bem separadas — 900‰
     * para a marcada, 50‰ para as demais —, que sao a ordem de grandeza que o corpus da 3b mediu no
     * papel. O veredito nao e escrito aqui: ele vem de [OmrThreshold.verdictFor], como no aparelho.
     *
     * @param acertos quantas questoes recebem a alternativa correta; as demais recebem outra.
     * @param rasura quando true, a primeira questao recebe **duas** marcadas, e vira pendencia.
     */
    private fun leitura(
        token: String = "",
        acertos: Int = pacote.answerKey.size,
        rasura: Boolean = false,
    ): InterpretedReading {
        val gabarito = pacote.answerKey.associate { it.itemId to it.correct }
        val questoes = regiao.bubbles.map { it.questionId }.distinct()
        val alternativas = regiao.bubbles.groupBy({ it.questionId }, { it.option })

        val marcadas: Map<String, List<String>> = questoes.withIndex().associate { (i, questao) ->
            val correta = gabarito.getValue(questao)
            val escolhida = if (i < acertos) {
                correta
            } else {
                alternativas.getValue(questao).first { it != correta }
            }
            questao to if (rasura && i == 0) {
                listOf(escolhida, alternativas.getValue(questao).first { it != escolhida })
            } else {
                listOf(escolhida)
            }
        }

        val judgements = regiao.bubbles.map { bolha ->
            val cobertura = if (bolha.option in marcadas.getValue(bolha.questionId)) 900 else 50
            val medicao = OmrMeasurement(bolha.questionId, bolha.option, cobertura)
            BubbleJudgement(medicao, limiar.verdictFor(cobertura))
        }

        val answers = questoes.map { questao ->
            val opcoes = marcadas.getValue(questao)
            if (opcoes.size == 1) {
                QuestionAnswer.Marcada(questao, opcoes.single())
            } else {
                QuestionAnswer.MultiplaMarcacao(questao, opcoes)
            }
        }

        return InterpretedReading(payload(token), judgements, answers)
    }

    private fun sessaoAberta() = ScanSession(pacote).apply { onPermission(granted = true) }

    @Test
    fun `sem permissao a sessao nao olha quadro nenhum`() {
        val sessao = ScanSession(pacote)
        assertEquals(ScanState.NoPermission, sessao.state)

        sessao.onFrame(FrameOutcome.Read(leitura()))

        assertEquals(ScanState.NoPermission, sessao.state)
    }

    @Test
    fun `permissao negada mantem a sessao sem permissao`() {
        val sessao = sessaoAberta()
        sessao.onPermission(granted = false)
        assertEquals(ScanState.NoPermission, sessao.state)
    }

    @Test
    fun `quadro sem folha deixa a sessao procurando, e sem motivo nenhum`() {
        val sessao = sessaoAberta()

        sessao.onFrame(FrameOutcome.NoSheet("nenhum marcador ArUco encontrado na captura"))

        assertEquals(ScanState.Searching, sessao.state)
    }

    @Test
    fun `folha achada e nao lida chega a tela com o motivo que o pipeline produziu`() {
        val sessao = sessaoAberta()
        val motivo = "nenhum QR decodificado na ROI que o mapa declara"

        sessao.onFrame(FrameOutcome.NotRead(motivo))

        assertEquals(ScanState.NotRead(motivo), sessao.state)
    }

    @Test
    fun `folha lida vira nota apurada contra o pacote carregado`() {
        val sessao = sessaoAberta()

        sessao.onFrame(FrameOutcome.Read(leitura(acertos = 40)))

        val lida = sessao.state as ScanState.Scored
        assertEquals(40, lida.score.points)
        assertEquals(pacote.scoring.maxScore, lida.score.maxScore)
        assertTrue(lida.score.closed, "folha sem rasura tem de fechar a nota")
    }

    /**
     * Cenario "Prova so objetiva nao tem caderno" (`slice-5b-2-a-nota-objetiva-parcial`): a tela e a
     * de antes, a nota apurada, e nao a da prova com discursiva, que e a unica que carrega caderno.
     */
    @Test
    fun `prova so objetiva nao tem caderno, e a folha continua virando nota`() {
        assertTrue(pacote.meta.fullyOfflineGradable, "a fixture precisa ser so objetiva, ou o cenario nao mede")
        val sessao = sessaoAberta()

        val apuracao = sessao.onFrame(FrameOutcome.Read(leitura(acertos = 40)))

        assertTrue(sessao.state is ScanState.Scored, "esperava a nota, veio ${sessao.state}")
        assertEquals(40, apuracao?.score?.points, "a nota continua sendo entregue para gravar")
    }

    @Test
    fun `troca de folha substitui o resultado por inteiro`() {
        val sessao = sessaoAberta()

        sessao.onFrame(FrameOutcome.Read(leitura(token = "folha-A", acertos = 40)))
        val primeira = sessao.state as ScanState.Scored
        assertEquals(40, primeira.score.points)

        sessao.onFrame(FrameOutcome.Read(leitura(token = "folha-B", acertos = 11)))

        val segunda = sessao.state as ScanState.Scored
        // As tres afirmacoes sao a mesma: nada da folha A sobreviveu.
        assertEquals("folha-B", segunda.payload.studentToken)
        assertEquals(11, segunda.score.points)
        assertTrue(
            segunda.reading.judgements.all { it.measurement.coveragePerMille in setOf(50, 900) },
            "a leitura apresentada tem de ser a da folha B, inteira",
        )
        assertEquals(segunda.payload, segunda.reading.payload)
    }

    @Test
    fun `quadro que falha nao apaga o resultado apresentado`() {
        val sessao = sessaoAberta()
        sessao.onFrame(FrameOutcome.Read(leitura(token = "folha-A")))
        val apresentado = sessao.state

        // Baixar o aparelho depois de escanear e o caso normal, e nao motivo para o resultado sumir.
        sessao.onFrame(FrameOutcome.NoSheet("nenhum marcador ArUco encontrado na captura"))
        sessao.onFrame(FrameOutcome.NotRead("nenhum QR decodificado na ROI que o mapa declara"))

        assertEquals(apresentado, sessao.state)
    }

    @Test
    fun `retomar descarta o resultado e volta a procurar`() {
        val sessao = sessaoAberta()
        sessao.onFrame(FrameOutcome.Read(leitura()))

        sessao.resume()

        assertEquals(ScanState.Searching, sessao.state)
    }

    @Test
    fun `folha de outra prova e recusada, dizendo de qual prova ela e`() {
        val sessao = sessaoAberta()
        val deOutraProva = leitura().let {
            it.copy(payload = it.payload.copy(examShortId = "folha-de-teste-de-impressao"))
        }

        sessao.onFrame(FrameOutcome.Read(deOutraProva))

        val recusa = sessao.state as ScanState.Rejected
        assertTrue(
            recusa.reason.contains("outra prova") &&
                recusa.reason.contains("folha-de-teste-de-impressao") &&
                recusa.reason.contains(pacote.meta.examId),
            "a recusa precisa nomear as duas provas: ${recusa.reason}",
        )
    }

    @Test
    fun `folha cujo corredor exclui o limiar chega a tela com o motivo do dominio`() {
        // O motivo nao e escrito aqui: e produzido pelo dominio, com uma folha cujo `ink_budget`
        // exclui o limiar do aplicativo, e comparado por igualdade com o que chegou a tela.
        val forasteira = regiao.copy(
            inkBudget = InkBudget(
                decorativeMax = 120,
                decorativeToneMax = 500,
                thresholdFloor = 600,
                thresholdCeiling = 800,
            ),
        )
        val medicoes = regiao.bubbles.map { OmrMeasurement(it.questionId, it.option, 50) }
        val recusaDoDominio = SheetInterpreter.interpret(
            OmrReading.Read(payload(), medicoes),
            forasteira,
            limiar,
        ) as InterpretationOutcome.Rejected

        val sessao = sessaoAberta()
        sessao.onFrame(FrameOutcome.Unreadable(recusaDoDominio.reason))

        assertEquals(ScanState.Rejected(recusaDoDominio.reason), sessao.state)
        assertTrue(
            recusaDoDominio.reason.contains("corredor"),
            "o motivo do dominio mudou de forma: ${recusaDoDominio.reason}",
        )
    }

    @Test
    fun `a cobertura de cada bolha sobrevive ate a tela`() {
        val sessao = sessaoAberta()
        val lida = leitura(acertos = 40)

        sessao.onFrame(FrameOutcome.Read(lida))

        val apresentado = sessao.state as ScanState.Scored
        assertEquals(regiao.bubbles.size, apresentado.reading.judgements.size)
        assertEquals(
            lida.judgements.associate { it.id to it.measurement.coveragePerMille },
            apresentado.reading.judgements.associate { it.id to it.measurement.coveragePerMille },
        )
    }

    @Test
    fun `folha com rasura apresenta a pendencia, e a nota nao fecha`() {
        val sessao = sessaoAberta()

        sessao.onFrame(FrameOutcome.Read(leitura(acertos = 40, rasura = true)))

        val lida = sessao.state as ScanState.Scored
        assertFalse(lida.score.closed, "folha com multipla marcacao nao pode fechar a nota")
        assertEquals(1, lida.score.pending.size)
        assertTrue(lida.score.pointsAtStake > 0, "a pendencia tem de dizer quanto esta em disputa")
    }

    private companion object {
        const val VARIANTE = "v1"
    }

    // ------------------------------------------------------------------ a captura que sobe

    @Test
    fun `folha apurada entrega uma apuracao para gravar`() {
        val sessao = sessaoAberta()

        val apuracao = sessao.onFrame(FrameOutcome.Read(leitura(token = "aluno-1")))

        assertTrue(apuracao != null, "a folha apurada precisa produzir o que gravar")
        assertEquals("aluno-1", apuracao!!.reading.payload.studentToken)
        assertEquals(40, apuracao.score.points)
        assertEquals(40, apuracao.score.outcomes.size, "a evidencia por questao precisa vir junto")
    }

    /**
     * A folha parada na frente da camera e **uma** captura.
     *
     * Sem esta regra, cada quadro viraria uma linha na fila e uma revisao no servidor: escanear uma
     * folha por tres segundos produziria dezenas de revisoes da mesma correcao, e a mais recente —
     * que e a corrente — seria escolhida por acaso entre quadros identicos.
     */
    @Test
    fun `a mesma folha em quadros seguidos nao vira captura nova`() {
        val sessao = sessaoAberta()

        val primeira = sessao.onFrame(FrameOutcome.Read(leitura(token = "aluno-1")))
        val segunda = sessao.onFrame(FrameOutcome.Read(leitura(token = "aluno-1")))
        val terceira = sessao.onFrame(FrameOutcome.Read(leitura(token = "aluno-1")))

        assertTrue(primeira != null, "a primeira apresentacao precisa gravar")
        assertEquals(null, segunda, "a folha que nao saiu do quadro ja foi gravada")
        assertEquals(null, terceira)
    }

    @Test
    fun `outra folha na sequencia vira captura nova`() {
        val sessao = sessaoAberta()

        sessao.onFrame(FrameOutcome.Read(leitura(token = "aluno-1")))
        val outra = sessao.onFrame(FrameOutcome.Read(leitura(token = "aluno-2")))

        assertTrue(outra != null, "folha de outro aluno e outra captura")
        assertEquals("aluno-2", outra!!.reading.payload.studentToken)
    }

    /**
     * Reapresentar a mesma folha de proposito e captura nova.
     *
     * E o professor que desconfiou da leitura e escaneou de novo. O servidor grava isso como revisao
     * nova, e a mais recente passa a ser a corrente — que e exatamente o que ele pediu ao repetir.
     */
    @Test
    fun `depois de voltar a procurar, a mesma folha vira captura nova`() {
        val sessao = sessaoAberta()

        sessao.onFrame(FrameOutcome.Read(leitura(token = "aluno-1")))
        sessao.resume()
        val denovo = sessao.onFrame(FrameOutcome.Read(leitura(token = "aluno-1")))

        assertTrue(denovo != null, "reapresentacao deliberada precisa valer como captura nova")
    }

    @Test
    fun `folha de outra prova nao produz nada para gravar`() {
        val sessao = sessaoAberta()
        val deOutraProva = leitura().let {
            it.copy(payload = it.payload.copy(examShortId = "prova-de-outra-escola"))
        }

        val apuracao = sessao.onFrame(FrameOutcome.Read(deOutraProva))

        assertEquals(null, apuracao, "recusa nao e correcao, e nao vira resultado duravel")
        assertTrue(sessao.state is ScanState.Rejected)
    }

    @Test
    fun `quadro ilegivel nao produz nada para gravar`() {
        val sessao = sessaoAberta()

        val apuracao = sessao.onFrame(FrameOutcome.Unreadable("folha dobrada"))

        assertEquals(null, apuracao)
    }

    /**
     * O canario desta secao (P13): sem uma folha que **nao** e apurada por outro motivo, os testes
     * acima passariam tambem numa sessao que nunca produz apuracao nenhuma.
     */
    @Test
    fun `a sessao produz apuracao em algum caso, e nao em nenhum`() {
        val sessao = sessaoAberta()
        assertTrue(
            sessao.onFrame(FrameOutcome.Read(leitura())) != null,
            "se nem a folha correta produz apuracao, os testes de ausencia nao medem nada",
        )
    }
}
