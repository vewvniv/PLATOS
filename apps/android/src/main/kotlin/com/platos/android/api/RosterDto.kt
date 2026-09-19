package com.platos.android.api

import com.platos.android.roster.AlunoDoRoster
import com.platos.domain.transport.RosterEntryDto
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// ------------------------------------------------------------------- o espelho, ainda de pe
//
// Este e o unico par em que os dois lados ja usavam o MESMO nome, entao aqui o import de
// `com.platos.domain.transport.RosterEntryDto` convive com uma declaracao homonima do proprio
// pacote. O import vence -- medido nesta sessao com sonda e canario (tarefa 2.1), e nao suposto.
// Sai no commit 4.

/**
 * O contrato de `GET /organizations/{id}/exams/{shortId}/roster`, espelhado do servidor.
 *
 * A fonte e `apps/api/src/main/kotlin/com/platos/api/http/dto/ExamDto.kt` (`RosterEntryDto`), e vale
 * aqui o mesmo que vale em [ProvaDto] e [OrganizacaoDto]: **espelho, e nao arquivo compartilhado**.
 *
 * A deriva entre os dois lados e fechada pelo JSON literal de `ObtencaoDeRosterTest.corpoComDois`, e
 * a rota que o cliente bate esta prendida em `a_obtencao_bate_na_rota_que_o_servidor_expoe`.
 *
 * **A primeira redacao desta KDoc dizia que quem fixava o literal era `ApiPlatosTest`** — que nao tem
 * uma linha sobre roster. A frase foi copiada de [ProvaDto], onde ela e verdadeira, sem conferir que
 * valia aqui, e mandava quem lesse para uma cobertura inexistente. Fica dito em vez de apagado (P7):
 * KDoc que aponta cobertura e afirmacao verificavel, e esta era falsa.
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
