package com.platos.android.api

import com.platos.android.outbox.ResultadoPendente
import com.platos.domain.capture.QuestionAnswer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.Instant

/**
 * O contrato de `POST /organizations/{id}/exams/{shortId}/results`, espelhado do servidor.
 *
 * A fonte e `apps/api/src/main/kotlin/com/platos/api/http/dto/ResultDto.kt`, e vale aqui o mesmo que
 * vale em [ProvaDto], [OrganizacaoDto] e [RosterEntryDto]: **espelho, e nao arquivo compartilhado**.
 * A deriva entre os dois lados e fechada pelo JSON literal que os testes dos dois prendem — o do
 * servidor monta o corpo a mao em `ResultRouteTest`, e o daqui confere o que [corpoDoEnvio] produz.
 *
 * **Nao ha campo de nome, turma, matricula nem habilidade, e a ausencia e o requisito.** O que liga
 * a folha ao aluno e o token (I5, ADR-0002), e o vinculo item->habilidade vive no `ExamPackage`, que
 * e imutavel e hasheado — o fato analitico e derivado dele no servidor, e nao enviado daqui.
 */
@Serializable
data class ObservacaoDto(
    @SerialName("item_id") val itemId: String,
    @SerialName("answer_kind") val answerKind: String,
    @SerialName("answer_options") val answerOptions: List<String>,
    val worth: Int,
    val earned: Int,
)

@Serializable
data class EnvioDeResultadoDto(
    @SerialName("capture_id") val captureId: String,
    @SerialName("student_token") val studentToken: String?,
    @SerialName("package_hash") val packageHash: String,
    @SerialName("variant_id") val variantId: String,
    val points: Int,
    @SerialName("max_score") val maxScore: Int,
    val closed: Boolean,
    @SerialName("captured_at") val capturedAt: String,
    val observations: List<ObservacaoDto>,
)

/**
 * O corpo que sobe, congelado no momento da apuracao.
 *
 * **`encodeDefaults` nao entra**, e `explicitNulls` fica ligado: `student_token` precisa viajar como
 * `null` explicito na folha avulsa, porque a ausencia do campo e a presenca dele em nulo significam
 * a mesma coisa para o servidor **hoje** — e depender disso amarraria o aparelho a um default do
 * outro lado que ninguem prometeu manter.
 */
private val JSON = Json { explicitNulls = true }

fun ResultadoPendente.corpoDoEnvio(): String = JSON.encodeToString(
    EnvioDeResultadoDto.serializer(),
    EnvioDeResultadoDto(
        captureId = captureId,
        studentToken = studentToken,
        packageHash = nota.packageHash,
        variantId = nota.variantId,
        points = nota.points,
        maxScore = nota.maxScore,
        closed = nota.closed,
        // ISO-8601 em UTC, que e o que `OffsetDateTime.parse` do servidor aceita. O instante e o da
        // apuracao no aparelho, e nao o da chegada: no modelo offline os dois podem estar a dias de
        // distancia, e e o primeiro que diz quando o aluno foi corrigido.
        capturedAt = Instant.ofEpochMilli(apuradoEm).toString(),
        observations = nota.outcomes.map {
            ObservacaoDto(
                itemId = it.questionId,
                answerKind = it.answer.tipoNoEnvio(),
                answerOptions = it.answer.alternativasNoEnvio(),
                worth = it.worth,
                earned = it.earned,
            )
        },
    ),
)

/** Os quatro valores que o `check` da migration admite, escritos num lugar so deste lado tambem. */
private fun QuestionAnswer.tipoNoEnvio(): String = when (this) {
    is QuestionAnswer.Marcada -> "marcada"
    is QuestionAnswer.EmBranco -> "em_branco"
    is QuestionAnswer.MultiplaMarcacao -> "multipla_marcacao"
    is QuestionAnswer.Indecisa -> "indecisa"
}

/** Todas as envolvidas, e nunca a "vencedora": desempatar transformaria rasura em resposta. */
private fun QuestionAnswer.alternativasNoEnvio(): List<String> = when (this) {
    is QuestionAnswer.Marcada -> listOf(option)
    is QuestionAnswer.EmBranco -> emptyList()
    is QuestionAnswer.MultiplaMarcacao -> options
    is QuestionAnswer.Indecisa -> options
}
