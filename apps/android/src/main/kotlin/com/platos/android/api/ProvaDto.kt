package com.platos.android.api

import com.platos.android.session.ProvaPublicada
import com.platos.domain.transport.ExamSummaryDto
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// ------------------------------------------------------------------- o espelho, ainda de pe
// Mesma situacao de [OrganizacaoDto]: sem leitor desde que `ApiPlatos` passou a importar
// `com.platos.domain.transport.ExamSummaryDto`. Sai no commit 4.

/**
 * O contrato de `GET /organizations/{id}/exams`, espelhado do servidor.
 *
 * A fonte e `apps/api/src/main/kotlin/com/platos/api/http/dto/ExamDto.kt`, e vale aqui o mesmo que
 * vale em [OrganizacaoDto]: **espelho, e nao arquivo compartilhado**, com a deriva entre os dois
 * lados fechada pelo JSON literal que `ApiPlatosTest` fixa.
 *
 * Os tres campos sao obrigatorios pela mesma razao daquele: o contrato esta neste repositorio, com
 * teste do outro lado. `content_hash` ausente viraria string vazia, e string vazia como hash
 * declarado e a forma silenciosa de desligar a conferencia de integridade.
 */
@Serializable
data class ProvaDto(
    @SerialName("short_id") val shortId: String,
    val title: String,
    @SerialName("content_hash") val contentHash: String,
)

/** A traducao para o vocabulario do preparo, no mesmo ponto unico de [OrganizacaoDto]. */
fun ExamSummaryDto.paraProva(): ProvaPublicada =
    ProvaPublicada(shortId = shortId, titulo = title, contentHash = contentHash)
