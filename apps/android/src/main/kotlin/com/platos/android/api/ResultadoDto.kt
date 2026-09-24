package com.platos.android.api

import com.platos.android.outbox.ResultadoPendente
import com.platos.domain.transport.AnswerObservationDto
import com.platos.domain.transport.ResultSubmissionDto
import com.platos.domain.transport.answerKind
import com.platos.domain.transport.answerOptions
import kotlinx.serialization.json.Json
import java.time.Instant

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
