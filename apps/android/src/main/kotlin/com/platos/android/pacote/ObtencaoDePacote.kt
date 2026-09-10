package com.platos.android.pacote

import com.platos.android.api.ApiPlatos
import com.platos.android.net.Retorno
import com.platos.android.session.ProvaPublicada
import com.platos.android.session.ResultadoDoPacote

/**
 * De onde o pacote vem: do que o aparelho ja guarda, ou da rede.
 *
 * **O cache e consultado primeiro, e a chave e o `content_hash` que a listagem trouxe.** E o que faz
 * a segunda abertura funcionar sem rede — que e o caso para o qual esta fatia inteira existe. Sem
 * essa consulta o aplicativo continuaria funcionando **com** rede e falharia exatamente onde
 * precisava funcionar, que e uma forma de defeito que nenhuma tela acusa.
 *
 * O hash vindo da listagem torna a decisao barata e correta: ADR-0009 fixa prova<->pacote em 1:1 e
 * imutavel, entao hash igual e conteudo igual, e nao "provavelmente igual".
 *
 * Funcao, e nao classe: ela nao tem estado. Quem tem e [com.platos.android.session.PreparoDaProva],
 * e esta aqui so produz o resultado que aquela consome.
 */
suspend fun obterPacote(
    cache: PacotesGuardados,
    api: ApiPlatos,
    organizacao: String,
    prova: ProvaPublicada,
): ResultadoDoPacote {
    // `ler` reconfere: o que volta daqui ja passou pelas duas camadas.
    cache.ler(organizacao, prova.contentHash)?.let { guardado ->
        return ResultadoDoPacote.Conferido(guardado, prova.contentHash)
    }

    return when (val retorno = api.pacote(organizacao, prova.shortId)) {
        is Retorno.SemRede -> ResultadoDoPacote.SemRede

        // 404 e a resposta de prova inexistente, prova sem pacote e prova de outra organizacao — a
        // rota nao distingue as tres de proposito. Qualquer outro status tambem cai aqui: para quem
        // segura o aparelho a acao e a mesma, e nao ha o que fazer com o numero.
        is Retorno.Recusou -> ResultadoDoPacote.Ausente

        is Retorno.Respondeu -> conferir(cache, organizacao, prova, retorno.valor.bytes, retorno.valor.hashDeclarado)
    }
}

/**
 * Confere o que chegou e so entao guarda.
 *
 * **A ordem importa e e esta.** Guardar antes de conferir poria no disco um pacote que a proxima
 * leitura teria de recusar — e enquanto ele estivesse la, o caminho de cache o encontraria e
 * gastaria uma reconferencia para chegar a mesma recusa, sem nunca tentar o pull de novo.
 *
 * O hash sob o qual se guarda e o **declarado pelo servidor**, ja conferido contra os bytes. Guardar
 * sob o hash pedido pela listagem seria assumir que os dois coincidem; eles coincidem, e afirmar
 * isso em vez de assumir e o que faz uma divergencia entre listagem e entrega aparecer.
 */
private fun conferir(
    cache: PacotesGuardados,
    organizacao: String,
    prova: ProvaPublicada,
    bytes: ByteArray,
    hashDeclarado: String?,
): ResultadoDoPacote =
    when (val conferencia = verificarPacote(bytes, hashDeclarado)) {
        is Conferencia.Recusado -> ResultadoDoPacote.Recusado(conferencia.motivo)

        is Conferencia.Conferido ->
            if (conferencia.contentHash != prova.contentHash.lowercase()) {
                // A listagem disse um hash e a entrega devolveu outro, ambos internamente coerentes.
                // O pacote e integro, e nao e o que foi escolhido — guarda-lo seria gravar sob um
                // endereco que ninguem vai procurar, e aceita-lo seria escanear com outro pacote.
                ResultadoDoPacote.Recusado(MotivoDaRecusa.INTEGRIDADE)
            } else {
                cache.guardar(organizacao, conferencia.contentHash, bytes)
                ResultadoDoPacote.Conferido(conferencia.pacote, conferencia.contentHash)
            }
    }
