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

/**
 * Contrato de `GET /organizations/{organizationId}/exams/{shortId}/roster`.
 *
 * **Dois campos, e a ausencia dos outros e o requisito.** `exam_roster` guarda tambem `class_group`
 * e `enrollment_id`, e eles **nao** entram aqui: quem recebe o roster precisa saber de quem e a
 * folha, e nao a matricula institucional do aluno. Cada campo que desce vira dado pessoal em cache
 * no aparelho, e cai na lacuna da classe H que o §16 registra — enumerar e apagar. Dado que nao
 * desce nao precisa de regra de apagamento.
 *
 * **Sem hash, e isso e decisao registrada e nao esquecimento.** ADR-0002 recusou um segundo hash
 * sobre o roster: ou ele trava o dado que **deve** poder mudar — e a eliminacao morre com isso — ou
 * e recalculado a cada mudanca e nao garante nada. O `content_hash` do pacote continua cobrindo so
 * o que precisa ser imutavel.
 *
 * `studentToken` e a chave que atravessa a fronteira (ADR-0002, I5): ele esta no QR impresso e em
 * `assignments[]` dentro do pacote, e e por ele que o nome daqui se liga a folha lida.
 */
@Serializable
data class RosterEntryDto(
    @SerialName("student_token") val studentToken: String,
    @SerialName("display_name") val displayName: String,
)
