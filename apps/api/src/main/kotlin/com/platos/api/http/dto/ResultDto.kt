package com.platos.api.http.dto

import com.platos.domain.capture.QuestionAnswer
import com.platos.domain.scoring.ObjectiveScore
import com.platos.domain.scoring.PendingQuestion
import com.platos.domain.scoring.PendingReason
import com.platos.domain.scoring.QuestionOutcome
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A evidencia de uma questao, como ela viaja.
 *
 * Os quatro valores de [answerKind] sao os de `QuestionAnswer`, e o `check` da migration os repete.
 * Nao ha um quinto: "em branco" e afirmacao sobre o que o aluno fez, "indecisa" e afirmacao sobre o
 * que a leitura conseguiu apurar, e as duas so parecem iguais ate a nota.
 */
@Serializable
data class AnswerObservationDto(
    @SerialName("item_id") val itemId: String,
    @SerialName("answer_kind") val answerKind: String,
    @SerialName("answer_options") val answerOptions: List<String> = emptyList(),
    val worth: Int,
    val earned: Int,
)

/**
 * Contrato de `POST /organizations/{organizationId}/exams/{shortId}/results`.
 *
 * **[captureId] e a chave de idempotencia, e ele nasce no aparelho.** Reenvio do mesmo resultado — a
 * confirmacao que se perdeu no caminho — chega com o mesmo valor. Recaptura da mesma folha e outra
 * captura, logo outro valor, logo revisao nova. Comparar conteudo no lugar disto seria errado: uma
 * recaptura que desse exatamente a mesma nota e recaptura, e nao reenvio.
 *
 * **[studentToken] e nulo na folha avulsa**, e nunca string vazia. O aluno fora da lista tem nota
 * valida; o que falta e a atribuicao (§7). Vazio faria todas as avulsas da mesma prova colidirem.
 *
 * **Nao ha campo de nome, turma ou matricula, e a ausencia e o requisito.** O resultado e fato sobre
 * a folha, e o que liga a folha ao aluno e o token — I5 e ADR-0002. Acrescentar um campo aqui e
 * acrescentar dado pessoal no transporte e no banco, e exige requisito que o justifique.
 *
 * **Tambem nao ha campo de habilidade.** O vinculo item->habilidade vive no `ExamPackage`, que e
 * imutavel e hasheado; o fato analitico e derivado dele por juncao, e nao enviado pelo aparelho.
 */
@Serializable
data class ResultSubmissionDto(
    @SerialName("capture_id") val captureId: String,
    @SerialName("student_token") val studentToken: String? = null,
    @SerialName("package_hash") val packageHash: String,
    @SerialName("variant_id") val variantId: String,
    val points: Int,
    @SerialName("max_score") val maxScore: Int,
    val closed: Boolean,
    @SerialName("captured_at") val capturedAt: String,
    val observations: List<AnswerObservationDto>,
)

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

private fun AnswerObservationDto.paraOutcome(): QuestionOutcome = QuestionOutcome(
    questionId = itemId,
    answer = when (answerKind) {
        "marcada" -> QuestionAnswer.Marcada(
            itemId,
            answerOptions.singleOrNull()
                ?: throw IllegalArgumentException(
                    "item '$itemId' e 'marcada' e traz ${answerOptions.size} alternativa(s); " +
                        "marcada e exatamente uma",
                ),
        )
        "em_branco" -> {
            require(answerOptions.isEmpty()) {
                "item '$itemId' e 'em_branco' e mesmo assim nomeia alternativa"
            }
            QuestionAnswer.EmBranco(itemId)
        }
        "multipla_marcacao" -> QuestionAnswer.MultiplaMarcacao(itemId, answerOptions)
        "indecisa" -> QuestionAnswer.Indecisa(itemId, answerOptions)
        else -> throw IllegalArgumentException(
            "item '$itemId' declara answer_kind '$answerKind', que nao existe",
        )
    },
    worth = worth,
    earned = earned,
)
