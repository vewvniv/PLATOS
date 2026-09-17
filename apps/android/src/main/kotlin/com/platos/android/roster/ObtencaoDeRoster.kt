package com.platos.android.roster

import com.platos.android.api.ApiPlatos
import com.platos.android.net.Retorno
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
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

/**
 * Prepara o roster para a abertura da sessao, e devolve **se o pull foi esperado**.
 *
 * **Espera so quando nao ha roster guardado.** Sem guardado, esperar tem significado: o gate barra
 * sem ele. Com guardado, esperar seria pior do que nao pedir — `obterPacote` nao toca a rede quando o
 * pacote esta em disco, entao a espera seria **so** do roster, e numa rede de escola associada a um
 * ponto sem saida nao ha falha rapida: a tela ficaria parada ate o tempo limite antes de abrir com o
 * que ja estava ali. Seria a sala sem sinal piorada pelo que existe para ela.
 *
 * **O custo esta no requisito, e nao escondido aqui:** um nome corrigido no servidor aparece na
 * escolha **seguinte** daquela prova. A atualizacao corre em [escopo] e chega a tempo da proxima.
 *
 * O booleano de retorno existe para o teste, e nao para o chamador — nenhuma tela decide nada com
 * ele. Sem ele, "nao esperou" so seria observavel por cronometro, e cronometro em teste e a forma
 * mais comum de medicao que nao mede.
 */
suspend fun prepararRoster(
    cache: RostersGuardados,
    api: ApiPlatos,
    organizacao: String,
    prova: ProvaPublicada,
    agora: Long,
    escopo: CoroutineScope,
): Boolean {
    if (cache.ler(organizacao, prova.shortId) != null) {
        escopo.launch { obterRoster(cache, api, organizacao, prova, agora) }
        return false
    }

    obterRoster(cache, api, organizacao, prova, agora)
    return true
}
