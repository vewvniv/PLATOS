package com.platos.api.http.dto

import kotlinx.serialization.Serializable

/**
 * Contrato de `GET /me/organizations`.
 *
 * `kind` e `role` viajam como texto porque sao valores de dominio versionados no banco por CHECK;
 * transforma-los em enum do transporte obrigaria a subir versao de contrato para acrescentar um
 * papel, o que a §3.3 preve (coordenacao, direcao).
 */
@Serializable
data class OrganizationDto(
    val id: String,
    val name: String,
    val kind: String,
    val role: String,
)
