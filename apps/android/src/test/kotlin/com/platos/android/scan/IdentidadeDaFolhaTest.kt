package com.platos.android.scan

import com.platos.android.vision.FrameOutcome
import com.platos.domain.capture.BubbleJudgement
import com.platos.domain.capture.CapturePayload
import com.platos.domain.capture.InterpretedReading
import com.platos.domain.capture.OmrMeasurement
import com.platos.domain.capture.QuestionAnswer
import com.platos.domain.capture.OmrThreshold
import com.platos.domain.exam.ExamPackage
import java.io.File
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * A camada (c) de ADR-0013: o pacote afirma que e desta prova, conferido contra o payload do QR.
 *
 * **E a mais critica das tres, e a unica que julga de quem e a folha.** (a) e (b) julgam bytes; esta
 * decide se a nota que vai sair pertence a folha que esta na frente da camera.
 *
 * **As duas provas usadas aqui sao adversariais de proposito.** `prova-2` declara os mesmos
 * identificadores de item, as mesmas posicoes e o mesmo gabarito da `prova-referencia`, mudando so o
 * `short_id`. Com conjuntos de itens diferentes, `ObjectiveScoring` recusaria a folha trocada por
 * divergencia de itens, e a conferencia de identidade ficaria **sombreada**: os cenarios passariam
 * sem que ela existisse. E o mesmo sombreamento que a mutacao da tarefa 4.4 revelou no cache, e a
 * fixture foi construida para nao repeti-lo.
 */
class IdentidadeDaFolhaTest {

    private val fixtures = File(
        System.getProperty("platos.fixtures")
            ?: error("propriedade `platos.fixtures` nao definida pelo build"),
    )

    private val pacote1: ExamPackage = Json.decodeFromString(
        File(fixtures, "prova-referencia.package.json").readText(),
    )

    private val pacote2: ExamPackage = Json.decodeFromString(
        File(fixtures, "prova-2.package.json").readText(),
    )

    private val limiar = OmrThreshold.MEDIDO_NA_FATIA_3B

    /**
     * A fixture continua adversarial.
     *
     * Se alguem regravar `prova-2` a partir de uma definicao com outros itens, os cenarios abaixo
     * continuariam verdes e deixariam de medir identidade — passariam a medir divergencia de itens.
     * Este cenario e o que impede essa deriva de acontecer em silencio.
     */
    @Test
    fun as_duas_provas_diferem_so_na_identidade() {
        assertNotEquals(pacote1.meta.examId, pacote2.meta.examId)
        assertEquals(pacote1.items.map { it.id }, pacote2.items.map { it.id })
        assertEquals(pacote1.variants, pacote2.variants)
        assertEquals(pacote1.answerKey, pacote2.answerKey)
    }

    /**
     * Tarefa 8.3: folha da outra prova e recusada, **pelo motivo certo**.
     *
     * A assercao e sobre o motivo, e nao sobre haver recusa: recusa por outra causa e indistinguivel
     * de recusa por identidade para um teste que so pergunta se recusou.
     */
    @Test
    fun folha_de_outra_prova_e_recusada_dizendo_que_e_de_outra_prova() {
        val sessao = sessaoDe(pacote1)

        sessao.onFrame(FrameOutcome.Read(leituraDe(pacote2)))

        val estado = sessao.state
        assertTrue(estado is ScanState.Rejected, "veio $estado")
        val motivo = (estado as ScanState.Rejected).reason
        assertTrue(motivo.contains("outra prova"), "o motivo nao fala de identidade: $motivo")
        assertTrue(motivo.contains(pacote2.meta.examId), "o motivo nao diz o que o QR trouxe: $motivo")
        assertTrue(motivo.contains(pacote1.meta.examId), "o motivo nao diz o que o aparelho carrega: $motivo")
    }

    /** A folha da prova carregada continua sendo apurada normalmente. */
    @Test
    fun folha_da_prova_carregada_produz_nota() {
        val sessao = sessaoDe(pacote1)

        sessao.onFrame(FrameOutcome.Read(leituraDe(pacote1)))

        assertTrue(sessao.state is ScanState.Scored, "veio ${sessao.state}")
    }

    /**
     * Tarefa 8.3c: **o que o defeito produziria** se a conferencia nao existisse.
     *
     * Com os itens coincidentes, retirar a camada (c) nao levaria a recusa por outra causa: levaria
     * a `ObjectiveScoring` apurar a folha da `prova-2` contra o gabarito da `prova-referencia` e
     * produzir nota. Este cenario nao remove a conferencia — ele **mede** o que ela esta segurando,
     * apurando a mesma leitura contra o pacote errado e afirmando que sai nota, e nao recusa.
     *
     * Sem ele, a mutacao da 8.3b provaria que o teste cai, e nao que havia algo caindo junto.
     */
    @Test
    fun sem_a_identidade_a_folha_trocada_produziria_nota_e_nao_recusa() {
        // A leitura e da folha da prova 2, e o pacote e o da prova 2: e a mesma apuracao que
        // aconteceria com a prova 1 carregada, se a identidade nao fosse conferida — os itens, as
        // posicoes e o gabarito sao identicos nos dois pacotes.
        val comoSeNaoConferisse = sessaoDe(pacote2)

        comoSeNaoConferisse.onFrame(FrameOutcome.Read(leituraDe(pacote2)))

        val estado = comoSeNaoConferisse.state
        assertTrue(estado is ScanState.Scored, "esperava nota, veio $estado")
        val nota = (estado as ScanState.Scored).score

        // **O numero, e nao o limiar.** `> 0` diria que saiu alguma nota; o que interessa e que sai
        // a nota **cheia** — a folha da prova errada, apurada contra o gabarito coincidente, produz
        // o mesmo resultado de uma folha certa e perfeita. Nao ha nada na tela que a distinga.
        assertEquals(pacote1.scoring.maxScore, nota.points, "a nota que a identidade impede")
        assertEquals(pacote1.scoring.maxScore, nota.maxScore)
        assertTrue(nota.pending.isEmpty(), "sairia ate sem pendencia: ${nota.pending}")
        println("8.3c: sem a camada (c), a folha trocada produz ${nota.points}/${nota.maxScore}")
    }

    /**
     * Tarefa 8.6: folha cuja variante o pacote nao declara.
     *
     * **A folha fisica desse caso nao e fabricavel hoje**, e a razao esta no gerador: `qrPayloadOf`
     * recebe `examId` e indice de regiao, e nada mais — a variante sai vazia de toda folha impressa
     * ate a fatia 7 criar variantes de verdade. O caso so passa a existir no papel la.
     *
     * O cenario em JVM e produzivel, e fica: o payload e montado com uma variante que o pacote nao
     * declara, e a recusa diz qual veio e quais existem. Quando a fatia 7 imprimir variantes, este
     * cenario ja estara aqui, e o que faltara e o oraculo fisico — nao o codigo.
     */
    @Test
    fun folha_de_variante_que_o_pacote_nao_declara_e_recusada() {
        val sessao = sessaoDe(pacote1)
        val regiao = pacote1.layout.values.single().regions.single()
        val gabarito = pacote1.answerKey.associate { it.itemId to it.correct }

        val judgements = regiao.bubbles.map { bolha ->
            val cobertura = if (bolha.option == gabarito.getValue(bolha.questionId)) 900 else 50
            BubbleJudgement(
                OmrMeasurement(bolha.questionId, bolha.option, cobertura),
                limiar.verdictFor(cobertura),
            )
        }
        val answers = regiao.bubbles.map { it.questionId }.distinct().map { questao ->
            QuestionAnswer.Marcada(questao, gabarito.getValue(questao))
        }
        // Mesma prova, variante inexistente: a identidade da prova confere, e a da variante nao.
        val payload = CapturePayload(pacote1.meta.examId, "", "v9", regiao.index)

        sessao.onFrame(FrameOutcome.Read(InterpretedReading(payload, judgements, answers)))

        val estado = sessao.state
        assertTrue(estado is ScanState.Rejected, "veio $estado")
        val motivo = (estado as ScanState.Rejected).reason
        assertTrue(motivo.contains("v9"), "o motivo nao diz qual variante veio: $motivo")
        assertTrue(motivo.contains("v1"), "o motivo nao diz quais o pacote declara: $motivo")
    }

    private fun sessaoDe(pacote: ExamPackage): ScanSession {
        val sessao = ScanSession(pacote)
        sessao.onPermission(granted = true)
        return sessao
    }

    /** Uma leitura completa e correta da folha daquele pacote, no molde de `ScanSessionTest`. */
    private fun leituraDe(pacote: ExamPackage): InterpretedReading {
        val regiao = pacote.layout.values.single().regions.single()
        val gabarito = pacote.answerKey.associate { it.itemId to it.correct }

        val judgements = regiao.bubbles.map { bolha ->
            val cobertura = if (bolha.option == gabarito.getValue(bolha.questionId)) 900 else 50
            BubbleJudgement(
                OmrMeasurement(bolha.questionId, bolha.option, cobertura),
                limiar.verdictFor(cobertura),
            )
        }

        val payload = CapturePayload(pacote.meta.examId, "", "", regiao.index)
        val answers = regiao.bubbles.map { it.questionId }.distinct().map { questao ->
            QuestionAnswer.Marcada(questao, gabarito.getValue(questao))
        }
        return InterpretedReading(payload, judgements, answers)
    }
}
