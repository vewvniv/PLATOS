package com.platos.android.roster

import com.platos.android.api.ApiPlatos
import com.platos.android.net.Retorno
import com.platos.android.session.ProvaPublicada

/**
 * De onde o roster vem: da rede quando ela responde, e do que o aparelho ja guarda quando nao.
 *
 * **A ordem e o inverso da do pacote, e a razao e o que cada um e.** O pacote e imutavel e
 * endereçado por hash, entao consultar o cache primeiro e correto por construcao — hash igual e
 * conteudo igual (ADR-0009). O roster e **mutavel por construcao** (ADR-0002): o nome de um aluno
 * pode ter sido corrigido, e um aluno pode ter entrado ou saido desde o ultimo pull. Preferir o
 * guardado quando ha rede apresentaria um nome que o servidor ja corrigiu, e nada na tela diria
 * isso.
 *
 * **Falha de rede nao apaga o que ja estava guardado.** Sem rede, o roster de ontem continua
 * servindo — e a marca de dado cacheado diz de quando ele e. O que nao acontece e o pull falhado
 * deixar o aparelho pior do que estava: e o mesmo criterio de "atualizar sem rede nao esvazia a
 * tela" que `device-session` ja exige da visao.
 *
 * **Recusa tambem preserva.** 404 aqui e prova inexistente, prova de outra organizacao e — pela
 * decisao da rota — nada mais; nenhum dos casos e razao para jogar fora um roster que o aparelho
 * puxou legitimamente antes. Quem transforma ausencia em recusa explicada e o gate, com frase
 * propria, e nao esta funcao.
 *
 * [agora] entra por parametro, e nao e lido de um relogio aqui dentro, pela mesma razao registrada
 * em [RosterDaProva]: relogio dentro da funcao torna o instante impossivel de afirmar num teste, e o
 * instante e o que a marca de cache apresenta.
 *
 * Funcao, e nao classe: ela nao tem estado, como `obterPacote`.
 */
suspend fun obterRoster(
    cache: RostersGuardados,
    api: ApiPlatos,
    organizacao: String,
    prova: ProvaPublicada,
    agora: Long,
): RosterDaProva? {
    when (val retorno = api.roster(organizacao, prova.shortId)) {
        is Retorno.Respondeu -> {
            val roster = RosterDaProva(retorno.valor, agora)
            cache.guardar(organizacao, prova.shortId, roster)
            return roster
        }

        is Retorno.SemRede, is Retorno.Recusou -> Unit
    }

    return cache.ler(organizacao, prova.shortId)
}
