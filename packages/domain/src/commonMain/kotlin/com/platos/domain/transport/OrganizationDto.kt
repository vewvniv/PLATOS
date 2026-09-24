package com.platos.domain.transport

import kotlinx.serialization.Serializable

/**
 * Contrato de `GET /me/organizations` — **um** declarante, para os dois lados (ADR-0015).
 *
 * Ele era digitado duas vezes: `OrganizationDto` na API e `OrganizacaoDto` no aparelho. Os nomes de
 * campo sao os do fio, em ingles, como eles viajam.
 *
 * `kind` e `role` viajam como texto porque sao valores de dominio versionados no banco por CHECK;
 * transforma-los em enum do transporte obrigaria a subir versao de contrato para acrescentar um
 * papel, o que a §3.3 preve (coordenacao, direcao).
 *
 * **Os quatro campos sao obrigatorios**, sem default. A razao veio do lado do aparelho e vale para
 * o tipo unico: este contrato esta neste repositorio, com teste do outro lado. Campo que sumir e
 * contrato quebrado, e contrato quebrado precisa estourar em vez de virar string vazia que a tela
 * apresentaria como nome de organizacao.
 *
 * `kind` e `role` ainda nao tem uso em tela no aparelho. Ficam porque o contrato e o contrato
 * inteiro: campo que existe no servidor e nao existe aqui e campo que ninguem ve desaparecer.
 */
@Serializable
data class OrganizationDto(
    val id: String,
    val name: String,
    val kind: String,
    val role: String,
)
