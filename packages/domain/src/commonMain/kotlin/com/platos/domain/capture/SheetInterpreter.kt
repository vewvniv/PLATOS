package com.platos.domain.capture

import com.platos.domain.layout.ScannableRegion

/**
 * Uma folha lida e interpretada.
 *
 * Traz as tres camadas juntas, e de proposito: [judgements] carrega a cobertura de cada bolha ao
 * lado do veredito, e [answers] traz a resposta por questao. Quem revisa uma pendencia precisa das
 * duas — a resposta diz o que ficou em duvida, a cobertura diz o quanto.
 */
data class InterpretedReading(
    val payload: CapturePayload,
    val judgements: List<BubbleJudgement>,
    val answers: List<QuestionAnswer>,
)

/** O que saiu de uma tentativa de interpretar uma leitura. */
sealed interface InterpretationOutcome {

    data class Interpreted(val reading: InterpretedReading) : InterpretationOutcome

    data class Rejected(val reason: String) : InterpretationOutcome
}

/**
 * Da medicao ao veredito e a resposta, sem tocar em pixel.
 *
 * O passo que faltava entre a fatia 3a — que mede e para — e o scoring. Ele e puro: recebe a
 * leitura que o adaptador produziu, o mapa que a descreve e o limiar do aplicativo, e nao consulta
 * mais nada.
 *
 * A validacao do corredor mora aqui, e nao no adaptador, porque e ela que decide se **esta folha**
 * pode ser lida por **este aplicativo** (ADR-0010). E uma pergunta sobre o par folha-leitor, e a
 * resposta e a mesma no emulador, no aparelho e no teste de host.
 */
object SheetInterpreter {

    fun interpret(
        reading: OmrReading,
        region: ScannableRegion,
        threshold: OmrThreshold,
    ): InterpretationOutcome {
        val read = when (reading) {
            is OmrReading.Rejected -> return InterpretationOutcome.Rejected(reading.reason)
            is OmrReading.Read -> reading
        }

        threshold.validateAgainst(region.inkBudget)?.let {
            return InterpretationOutcome.Rejected(it)
        }

        // A medicao da 3a ja garante que o conjunto medido e o declarado. A conferencia aqui e
        // contra regressao de composicao: se um dia a leitura entregar um subconjunto, a resposta
        // que sumisse viraria "em branco" no scoring — e em branco vale zero, em silencio.
        //
        // **Repeticao antes de conjunto**, e nesta ordem: `Set` nao ve repeticao. Uma medicao
        // duplicada deixa o conjunto identico ao declarado, e a questao afetada ganha uma bolha a
        // mais — duas marcadas iguais viram multipla marcacao, uma pendencia que a leitura
        // inventou. Conferir so o conjunto passaria por isso calado.
        val repetidas = read.measurements.groupingBy { it.id }.eachCount()
            .filterValues { it > 1 }.keys
        if (repetidas.isNotEmpty()) {
            return InterpretationOutcome.Rejected(
                "bolha repetida na medicao da regiao ${region.index}: " +
                    repetidas.sorted().joinToString(", "),
            )
        }

        val declared = region.bubbles.map { "${it.questionId}/${it.option}" }.toSet()
        val measured = read.measurements.map { it.id }.toSet()
        if (measured != declared) {
            val faltando = (declared - measured).sorted()
            val sobrando = (measured - declared).sorted()
            return InterpretationOutcome.Rejected(
                "bolhas medidas divergem das declaradas na regiao ${region.index}" +
                    (if (faltando.isNotEmpty()) "; faltando: ${faltando.joinToString(", ")}" else "") +
                    (if (sobrando.isNotEmpty()) "; nao declaradas: ${sobrando.joinToString(", ")}" else ""),
            )
        }

        val judgements = read.measurements.map { threshold.judge(it) }
        return InterpretationOutcome.Interpreted(
            InterpretedReading(
                payload = read.payload,
                judgements = judgements,
                answers = AnswerSheet.of(judgements),
            ),
        )
    }
}
