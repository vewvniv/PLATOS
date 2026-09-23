package com.platos.android.api

import com.platos.android.roster.AlunoDoRoster
import com.platos.domain.transport.RosterEntryDto

/** A traducao para o vocabulario do aparelho, no mesmo ponto unico dos outros DTOs. */
fun RosterEntryDto.paraAluno(): AlunoDoRoster =
    AlunoDoRoster(token = studentToken, nome = displayName)
