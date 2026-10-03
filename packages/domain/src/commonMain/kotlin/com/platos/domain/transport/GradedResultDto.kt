package com.platos.domain.transport

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A nota que o professor deu a uma discursiva, como viaja: o item e a pontuacao **em string**
 * (`"1.75"`), porque numero JSON passa por `Double` e `0.1 + 0.2 != 0.3` (ADR-0021).
 */
@Serializable
data class EssayGradeDto(
    @SerialName("item_id") val itemId: String,
    val earned: String,
)

/**
 * Contrato de `POST /organizations/{organizationId}/exams/{shortId}/results/graded` — **um** declarante
 * (ADR-0015; `slice-5c-2-a-nota-do-professor`, ADR-0021).
 *
 * **E autocontido**: leva a parte objetiva ([observations]) e a nota de cada discursiva
 * ([essayGrades]), de modo que o servidor o aceita mesmo sem ter recebido antes a parcial da captura.
 * [completesCaptureId] diz **qual captura da parcial** a nota completa; e com ele que a revisao do
 * professor prevalece sobre a automatica da mesma captura, em qualquer ordem de chegada.
 *
 * **[captureId] e a chave de idempotencia desta correcao**, distinta da de [completesCaptureId]: corrigir
 * de novo a mesma captura e outra correcao, logo outro valor, logo revisao nova.
 *
 * [origin] e [path] chegam declarados para que o servidor os recuse quando forem de fatia posterior
 * (`ai`, `text`): hoje so `teacher` e `image`.
 *
 * [points] e o total **declarado**, em string; o servidor o recalcula e compara. [closed] idem.
 *
 * **Nao ha imagem, nome de arquivo, nome, turma nem matricula, e a ausencia e o requisito** (I5; spec
 * `result-sync`). A resposta fica no aparelho (`result-sync`; ADR-0012, decisao 6).
 */
@Serializable
data class GradedResultSubmissionDto(
    @SerialName("capture_id") val captureId: String,
    @SerialName("completes_capture_id") val completesCaptureId: String,
    @SerialName("student_token") val studentToken: String? = null,
    @SerialName("package_hash") val packageHash: String,
    @SerialName("variant_id") val variantId: String,
    val origin: String,
    val path: String,
    val points: String,
    @SerialName("max_score") val maxScore: Int,
    val closed: Boolean,
    @SerialName("captured_at") val capturedAt: String,
    val observations: List<AnswerObservationDto>,
    @SerialName("essay_grades") val essayGrades: List<EssayGradeDto>,
)
