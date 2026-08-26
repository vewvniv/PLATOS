package com.platos.domain.capture

import com.platos.domain.fixtures.Fixtures
import com.platos.domain.layout.LayoutMap
import com.platos.domain.layout.ScannableRegion
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A ponte entre a medicao da fatia 3a e a nota.
 *
 * Roda contra a **regiao real** do golden — 160 bolhas, as mesmas que se imprimem —, e nao contra
 * uma regiao inventada. Um mapa de brinquedo com quatro bolhas passaria por todos estes caminhos
 * sem tocar no unico caso que interessa: o conjunto de verdade, do tamanho de verdade.
 */
class SheetInterpreterTest {

    private val map: LayoutMap = Json.decodeFromString(
        LayoutMap.serializer(),
        Fixtures.PROVA_REFERENCIA_LAYOUT_JSON,
    )

    private val region: ScannableRegion = map.regions.first()

    /** Limiar de teste, no meio do corredor. O do aplicativo sai do corpus (ADR-0011). */
    private val limiar = OmrThreshold(value = 300, margin = 50)

    private val payload = CapturePayload("prova-referencia-slice-1", "", "", region.index)

    /** Uma folha em que a alternativa [marcada] foi preenchida em toda questao. */
    private fun folhaCom(marcada: String, coberturaMarcada: Int = 900, coberturaVazia: Int = 40) =
        OmrReading.Read(
            payload = payload,
            measurements = region.bubbles.map {
                OmrMeasurement(
                    questionId = it.questionId,
                    option = it.option,
                    coveragePerMille = if (it.option == marcada) coberturaMarcada else coberturaVazia,
                )
            },
        )

    private fun interpreted(outcome: InterpretationOutcome): InterpretedReading {
        assertTrue(outcome is InterpretationOutcome.Interpreted, "esperava leitura, veio: $outcome")
        return outcome.reading
    }

    private fun rejected(outcome: InterpretationOutcome): String {
        assertTrue(outcome is InterpretationOutcome.Rejected, "esperava recusa, veio: $outcome")
        return outcome.reason
    }

    @Test
    fun `a folha inteira vira resposta, uma por questao`() {
        val leitura = interpreted(SheetInterpreter.interpret(folhaCom("B"), region, limiar))

        assertEquals(40, leitura.answers.size, "a prova de referencia tem 40 questoes")
        assertEquals(160, leitura.judgements.size, "e 160 bolhas")
        assertTrue(leitura.answers.all { it is QuestionAnswer.Marcada && it.option == "B" })
    }

    @Test
    fun `a cobertura sobrevive ao veredito`() {
        // O cenario da spec. O veredito nao substitui o numero: quem revisa precisa dos dois.
        val leitura = interpreted(SheetInterpreter.interpret(folhaCom("A"), region, limiar))

        val marcadas = leitura.judgements.filter { it.verdict == BubbleVerdict.MARCADA }
        val vazias = leitura.judgements.filter { it.verdict == BubbleVerdict.VAZIA }

        assertEquals(40, marcadas.size)
        assertEquals(120, vazias.size)
        assertTrue(marcadas.all { it.measurement.coveragePerMille == 900 })
        assertTrue(vazias.all { it.measurement.coveragePerMille == 40 })
    }

    @Test
    fun `o conjunto de bolhas julgadas e exatamente o que a regiao declara`() {
        val leitura = interpreted(SheetInterpreter.interpret(folhaCom("C"), region, limiar))

        assertEquals(
            region.bubbles.map { "${it.questionId}/${it.option}" }.toSet(),
            leitura.judgements.map { it.id }.toSet(),
        )
    }

    @Test
    fun `folha cujo corredor exclui o limiar e recusada`() {
        val outroCorredor = region.copy(
            inkBudget = region.inkBudget.copy(thresholdFloor = 100, thresholdCeiling = 180),
        )

        val motivo = rejected(SheetInterpreter.interpret(folhaCom("A"), outroCorredor, limiar))

        assertTrue(motivo.contains("300"), "a mensagem precisa nomear o limiar: $motivo")
        assertTrue(motivo.contains("180"), "a mensagem precisa nomear o corredor da folha: $motivo")
    }

    @Test
    fun `a recusa da leitura atravessa a interpretacao com o motivo original`() {
        val motivo = rejected(
            SheetInterpreter.interpret(
                OmrReading.Rejected("marcadores encontrados: 3, esperados 4"),
                region,
                limiar,
            ),
        )

        assertEquals("marcadores encontrados: 3, esperados 4", motivo)
    }

    @Test
    fun `medicao faltando e recusada, e nao vira questao em branco`() {
        // O modo de falha que a conferencia existe para pegar. Uma bolha ausente nao quebra nada
        // sozinha: a questao dela viraria "em branco", e em branco vale zero, em silencio.
        val incompleta = folhaCom("A").let { it.copy(measurements = it.measurements.drop(1)) }

        val motivo = rejected(SheetInterpreter.interpret(incompleta, region, limiar))

        assertTrue(motivo.contains("faltando"), "a mensagem precisa dizer o que faltou: $motivo")
        assertTrue(
            motivo.contains(region.bubbles.first().questionId),
            "a mensagem precisa nomear a bolha: $motivo",
        )
    }

    @Test
    fun `medicao de bolha nao declarada e recusada`() {
        val comIntrusa = folhaCom("A").let {
            it.copy(measurements = it.measurements + OmrMeasurement("q99", "A", 500))
        }

        val motivo = rejected(SheetInterpreter.interpret(comIntrusa, region, limiar))

        assertTrue(motivo.contains("q99/A"), "a mensagem precisa nomear a bolha intrusa: $motivo")
    }

    @Test
    fun `a mesma leitura interpretada duas vezes da o mesmo resultado`() {
        val leitura = folhaCom("D")

        assertEquals(
            SheetInterpreter.interpret(leitura, region, limiar),
            SheetInterpreter.interpret(leitura, region, limiar),
        )
    }

    @Test
    fun `os quatro casos de resposta aparecem numa folha real`() {
        // Uma folha com rasura, uma em branco, uma duvidosa e o resto respondido — o que uma turma
        // de verdade entrega.
        val porBolha = mutableMapOf<String, Int>()
        for (bubble in region.bubbles) {
            val id = "${bubble.questionId}/${bubble.option}"
            porBolha[id] = when {
                bubble.questionId == "q01" -> if (bubble.option in listOf("A", "C")) 900 else 40
                bubble.questionId == "q02" -> 40
                bubble.questionId == "q03" -> if (bubble.option == "B") 300 else 40
                else -> if (bubble.option == "A") 900 else 40
            }
        }
        val leitura = OmrReading.Read(
            payload,
            region.bubbles.map {
                OmrMeasurement(it.questionId, it.option, porBolha.getValue("${it.questionId}/${it.option}"))
            },
        )

        val respostas = interpreted(SheetInterpreter.interpret(leitura, region, limiar))
            .answers.associateBy { it.questionId }

        assertEquals(QuestionAnswer.MultiplaMarcacao("q01", listOf("A", "C")), respostas["q01"])
        assertEquals(QuestionAnswer.EmBranco("q02"), respostas["q02"])
        assertEquals(QuestionAnswer.Indecisa("q03", listOf("B")), respostas["q03"])
        assertEquals(QuestionAnswer.Marcada("q04", "A"), respostas["q04"])
    }
}
