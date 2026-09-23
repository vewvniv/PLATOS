package com.platos.domain.transport

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A evidencia de uma questao, como ela viaja — **um** declarante (ADR-0015).
 *
 * Era `AnswerObservationDto` na API e `ObservacaoDto` no aparelho.
 *
 * Os quatro valores de [answerKind] sao os de `QuestionAnswer`, e estao em [AnswerKind]. O `check`
 * da migration os repete de proposito, e `tools/parity/answer-kind.mjs` amarra os dois.
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
 * Contrato de `POST /organizations/{organizationId}/exams/{shortId}/results` — **um** declarante.
 *
 * Era `ResultSubmissionDto` na API e `EnvioDeResultadoDto` no aparelho.
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
 *
 * **Os defaults de [studentToken] e de `answerOptions` sao tolerancia de entrada do servidor**, e
 * ficam (ADR-0015, "como isto poderia falhar em silencio"). Eles vieram do lado da API, onde um
 * corpo que omita o campo e aceito hoje; remove-los transformaria esse corpo num 400, que seria
 * mudanca de comportamento. Quem **codifica** com defaults presentes precisa de
 * `encodeDefaults = true`, ou o campo deixa de ser emitido — e e por isso que o aparelho declara
 * essa opcao explicitamente em `corpoDoEnvio`.
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
