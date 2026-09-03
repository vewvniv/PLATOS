package com.platos.android.api

import com.platos.android.session.Organizacao
import kotlinx.serialization.Serializable

/**
 * O contrato de `GET /me/organizations`, espelhado do servidor.
 *
 * A fonte e `apps/api/src/main/kotlin/com/platos/api/http/dto/OrganizationDto.kt`, e os nomes de
 * campo sao os dele — em ingles, como viajam no JSON.
 *
 * **Espelho, e nao arquivo compartilhado.** Os dois modulos dependem de `packages:domain`, entao
 * mover o DTO para la e possivel e seria o compartilhamento de verdade; esta fatia declara
 * `packages/domain` e `apps/api` intocados (proposal, e tarefa 7.4), e por isso o contrato e
 * repetido em vez de dividido. O custo e deriva silenciosa entre os dois lados, e quem paga por ela
 * e `ApiPlatosTest`, que fixa o JSON literal que o servidor emite.
 *
 * **Os quatro campos sao obrigatorios**, ao contrario de `CredencialDeSessao`, que da padrao a tudo
 * menos ao token. A diferenca nao e estilo. Aquele corpo nunca foi medido e vem da documentacao de
 * terceiro, entao campo ausente e possibilidade real e o padrao evita derrubar a entrada por dado
 * que ninguem usa. Este esta neste repositorio, com teste do outro lado: campo que sumir e contrato
 * quebrado, e contrato quebrado precisa estourar em vez de virar string vazia que a tela
 * apresentaria como nome de organizacao.
 *
 * `kind` e `role` ainda nao tem uso em tela. Ficam porque o espelho e do contrato inteiro: campo que
 * existe no servidor e nao existe aqui e campo que ninguem ve desaparecer.
 */
@Serializable
data class OrganizacaoDto(
    val id: String,
    val name: String,
    val kind: String,
    val role: String,
)

/**
 * A traducao para o vocabulario da sessao.
 *
 * `Organizacao` tem `nome` e o contrato tem `name`: a fronteira entre os dois idiomas fica aqui, num
 * lugar so, em vez de espalhada por quem le o DTO.
 */
fun OrganizacaoDto.paraOrganizacao(): Organizacao = Organizacao(id = id, nome = name)
