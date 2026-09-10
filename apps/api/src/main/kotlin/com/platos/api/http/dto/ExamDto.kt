package com.platos.api.http.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Contrato de `GET /organizations/{organizationId}/exams`.
 *
 * **`contentHash` nao e detalhe de implementacao vazando para o transporte.** E ele que permite ao
 * aparelho saber, antes de pedir o pacote, se ja tem o conteudo: ADR-0009 fixa prova<->pacote em 1:1
 * e imutavel, entao hash igual e conteudo igual, e nao "provavelmente igual". Sem ele todo pull
 * seria feito para descobrir que era desnecessario, e a fatia inteira existe para o caso sem rede.
 *
 * `shortId` e o identificador que o QR impresso carrega (§8), e e por ele que o pacote e pedido —
 * nao pelo `id` da prova. Um segundo identificador no caminho obrigaria o aparelho a guardar dois.
 */
@Serializable
data class ExamSummaryDto(
    @SerialName("short_id") val shortId: String,
    val title: String,
    @SerialName("content_hash") val contentHash: String,
)
