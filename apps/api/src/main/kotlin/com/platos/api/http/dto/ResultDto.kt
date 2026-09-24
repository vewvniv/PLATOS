package com.platos.api.http.dto

import com.platos.domain.capture.QuestionAnswer
import com.platos.domain.scoring.ObjectiveScore
import com.platos.domain.scoring.PendingQuestion
import com.platos.domain.scoring.PendingReason
import com.platos.domain.scoring.QuestionOutcome
import com.platos.domain.transport.AnswerKind
import com.platos.domain.transport.AnswerObservationDto
import com.platos.domain.transport.ResultSubmissionDto
import kotlinx.serialization.Serializable

/**
 * O que a rota responde, e ela responde a mesma coisa nas duas vezes.
 *
 * Reenvio recebe a resposta que a primeira gravacao recebeu — mesma revisao, mesmo status. Um
 * segundo envio que respondesse diferente obrigaria o aparelho a distinguir "gravou" de "ja estava
 * gravado" para decidir se pode expurgar, e as duas significam a mesma coisa para ele: o resultado
 * esta no servidor.
 */
@Serializable
data class ResultAcceptedDto(
    val revision: Int,
)

/**
 * O corpo recebido, traduzido para o tipo de dominio que o aparelho usou para produzi-lo.
 *
 * **E aqui que a validacao de coerencia acontece, e ela nao e reescrita.** `ObjectiveScore` ja
 * recusa, na construcao, nota fora da escala, evidencia que nao soma a nota, item repetido e
 * desencontro entre a evidencia e a lista de pendencias. Repetir essas regras em SQL ou numa
 * validacao propria da API seria uma segunda implementacao da mesma regra, e duas implementacoes
 * divergem — a regra 7 do `CLAUDE.md` existe para isso. O servidor confere com o **mesmo** codigo
 * que o aparelho usou.
 *
 * Lanca [IllegalArgumentException] quando o corpo e incoerente; a rota a traduz em 400.
 */
fun ResultSubmissionDto.paraNota(): ObjectiveScore {
    val outcomes = observations.map { it.paraOutcome() }
    val pending = outcomes.filter { it.pendente }.map {
        PendingQuestion(
            questionId = it.questionId,
            reason = when (it.answer) {
                is QuestionAnswer.MultiplaMarcacao -> PendingReason.MULTIPLA_MARCACAO
                else -> PendingReason.INDECISA
            },
            points = it.worth,
        )
    }

    // `closed` nao e recalculado aqui e depois comparado: ele e conferido contra a lista de
    // pendencias, que e o que o define. Um corpo que dissesse "fechada" com pendencia na evidencia
    // apresentaria duvida como resultado, e e a forma de falha que a invariante de revisao humana
    // existe para impedir.
    require(closed == pending.isEmpty()) {
        "o corpo diz closed=$closed e traz ${pending.size} questao(oes) pendente(s) na evidencia"
    }

    return ObjectiveScore(
        packageHash = packageHash,
        variantId = variantId,
        points = points,
        maxScore = maxScore,
        pending = pending,
        outcomes = outcomes,
    )
}

/**
 * O caminho de volta: o valor recebido no fio vira o tipo de dominio que o produziu.
 *
 * **As quatro ramificacoes sao as constantes de [AnswerKind], e nao literais repetidos aqui.** Este
 * `when` era o terceiro registro Kotlin dos mesmos quatro valores; ramificar sobre a constante e o
 * que faz a unificacao alcancar tambem quem **le** o campo, e nao so quem o escreve (ADR-0015).
 */
private fun AnswerObservationDto.paraOutcome(): QuestionOutcome = QuestionOutcome(
    questionId = itemId,
    answer = when (answerKind) {
        AnswerKind.MARCADA -> QuestionAnswer.Marcada(
            itemId,
            answerOptions.singleOrNull()
                ?: throw IllegalArgumentException(
                    "item '$itemId' e 'marcada' e traz ${answerOptions.size} alternativa(s); " +
                        "marcada e exatamente uma",
                ),
        )
        AnswerKind.EM_BRANCO -> {
            require(answerOptions.isEmpty()) {
                "item '$itemId' e 'em_branco' e mesmo assim nomeia alternativa"
            }
            QuestionAnswer.EmBranco(itemId)
        }
        AnswerKind.MULTIPLA_MARCACAO -> QuestionAnswer.MultiplaMarcacao(itemId, answerOptions)
        AnswerKind.INDECISA -> QuestionAnswer.Indecisa(itemId, answerOptions)
        else -> throw IllegalArgumentException(
            "item '$itemId' declara answer_kind '$answerKind', que nao existe",
        )
    },
    worth = worth,
    earned = earned,
)
