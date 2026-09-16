package com.platos.android.api

import com.platos.android.roster.AlunoDoRoster
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * O contrato de `GET /organizations/{id}/exams/{shortId}/roster`, espelhado do servidor.
 *
 * A fonte e `apps/api/src/main/kotlin/com/platos/api/http/dto/ExamDto.kt` (`RosterEntryDto`), e vale
 * aqui o mesmo que vale em [ProvaDto] e [OrganizacaoDto]: **espelho, e nao arquivo compartilhado**,
 * com a deriva entre os dois lados fechada pelo JSON literal que `ApiPlatosTest` fixa.
 *
 * **Dois campos, e a ausencia dos outros e o requisito** — o mesmo que o DTO do servidor registra.
 * `exam_roster` guarda tambem `class_group` e `enrollment_id`, e eles nao descem: cada campo que
 * desce vira dado pessoal em cache no aparelho, e dado que nao desce nao precisa de regra de
 * apagamento. Acrescentar um campo aqui e acrescentar dado pessoal no aparelho, e exige requisito que
 * o justifique.
 *
 * Os dois sao obrigatorios, sem default, pela razao que [ProvaDto] registra: campo ausente viraria
 * string vazia, e nome vazio na tela e indistinguivel de aluno sem nome cadastrado.
 */
@Serializable
data class RosterEntryDto(
    @SerialName("student_token") val studentToken: String,
    @SerialName("display_name") val displayName: String,
)

/** A traducao para o vocabulario do aparelho, no mesmo ponto unico dos outros DTOs. */
fun RosterEntryDto.paraAluno(): AlunoDoRoster =
    AlunoDoRoster(token = studentToken, nome = displayName)
