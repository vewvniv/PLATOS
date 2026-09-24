package com.platos.android.api

import com.platos.android.session.Organizacao
import com.platos.domain.transport.OrganizationDto

/**
 * A traducao para o vocabulario da sessao.
 *
 * `Organizacao` tem `nome` e o contrato tem `name`: a fronteira entre os dois idiomas fica aqui, num
 * lugar so, em vez de espalhada por quem le o DTO.
 */
fun OrganizationDto.paraOrganizacao(): Organizacao = Organizacao(id = id, nome = name)
