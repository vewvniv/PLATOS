package com.platos.android.api

import com.platos.android.session.ProvaPublicada
import com.platos.domain.transport.ExamSummaryDto

/**
 * A traducao para o vocabulario do preparo, no mesmo ponto unico das outras.
 *
 * **O nome do arquivo diz `ProvaDto` e nao ha mais nenhum `ProvaDto` aqui** — o contrato e
 * `com.platos.domain.transport.ExamSummaryDto` desde o ADR-0015. O nome fica: renomear arquivo e
 * refatoracao fora do escopo desta mudanca (regra 6, P25), e um `git mv` misturado com a remocao
 * dos espelhos tornaria o diff do commit 4 ilegivel justo onde ele precisa ser conferido. Fica
 * dito, como `ResultQueries.findPublishedExamId` ja faz com o proprio nome.
 */
fun ExamSummaryDto.paraProva(): ProvaPublicada =
    ProvaPublicada(shortId = shortId, titulo = title, contentHash = contentHash)
