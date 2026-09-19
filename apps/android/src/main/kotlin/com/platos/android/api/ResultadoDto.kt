package com.platos.android.api

import com.platos.android.outbox.ResultadoPendente
import com.platos.domain.transport.AnswerObservationDto
import com.platos.domain.transport.ResultSubmissionDto
import com.platos.domain.transport.answerKind
import com.platos.domain.transport.answerOptions
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.Instant

// ------------------------------------------------------------------- os espelhos, ainda de pe
// Sem leitor desde que [corpoDoEnvio] passou a montar os tipos de `com.platos.domain.transport`.
// Saem no commit 4, junto com os do servidor.

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
 * `explicitNulls` fica ligado: `student_token` precisa viajar como `null` explicito na folha
 * avulsa, porque a ausencia do campo e a presenca dele em nulo significam a mesma coisa para o
 * servidor **hoje** — e depender disso amarraria o aparelho a um default do outro lado que ninguem
 * prometeu manter.
 *
 * **`encodeDefaults` passou a entrar, e a razao e o ADR-0015.** A primeira redacao desta KDoc dizia
 * "`encodeDefaults` nao entra", e a frase era verdadeira enquanto o DTO deste lado nao tinha
 * default nenhum: sem default, `encodeDefaults` nao tem sobre o que agir. O tipo e agora o do
 * dominio, e ele carrega os defaults **do servidor** — `student_token = null` e
 * `answer_options = emptyList()` —, que sao tolerancia de **entrada** e nao podiam ser removidos
 * sem transformar num 400 um corpo hoje aceito.
 *
 * Com default presente e `encodeDefaults` desligado, `kotlinx.serialization` **omite** o campo cujo
 * valor iguala o default. Seria exatamente a regressao que a folha avulsa nao pode ter, e que o
 * cenario `folha avulsa manda student_token nulo, e nao omite o campo` existe para pegar. Ligar
 * `encodeDefaults` devolve ao fio os mesmos bytes de antes — e a prova disso nao e este comentario,
 * e a comparacao byte a byte da tarefa 3.4.
 */
private val JSON = Json {
    explicitNulls = true
    encodeDefaults = true
}

fun ResultadoPendente.corpoDoEnvio(): String = JSON.encodeToString(
    ResultSubmissionDto.serializer(),
    ResultSubmissionDto(
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
            AnswerObservationDto(
                itemId = it.questionId,
                answerKind = it.answer.answerKind(),
                answerOptions = it.answer.answerOptions(),
                worth = it.worth,
                earned = it.earned,
            )
        },
    ),
)
