package com.platos.android.api

import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpResponseValidator
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.bearerAuth
import io.ktor.http.HttpStatusCode

/**
 * O cliente que fala com a API do PLATOS: credencial em todo pedido, 401 num ponto so.
 *
 * **Esta funcao e o "ponto unico" da decisao 3, e ela e uma funcao justamente para poder ser
 * verificada como tal.** Se a credencial e o 401 vivessem dentro de `ApiPlatos.organizacoes()`,
 * afirmar "nenhuma tela trata 401 por conta propria" exigiria acreditar em quem escreve a proxima
 * chamada. Aqui a afirmacao e sobre o cliente: qualquer pedido que passe por ele carrega a
 * credencial e tem o 401 tratado, inclusive um endpoint que ainda nao existe.
 *
 * [aoExpirarSessao] roda **antes** de a chamada retornar, e nao numa corrotina a parte. E por isso
 * que `HttpResponseValidator` esta aqui no lugar de `ResponseObserver`: o observador roda solto, e
 * a tela poderia desenhar o resultado da chamada antes de a sessao saber que expirou.
 *
 * **O 401 nao e conferido contra ter havido credencial.** Sem sessao guardada o pedido sai sem
 * `Authorization` e o servidor responde 401 do mesmo jeito, e o estado a que isso leva — de volta a
 * entrada — e o certo nos dois casos. Distinguir exigiria uma segunda decisao dentro do transporte,
 * que e onde esta fatia nao quer decisao nenhuma.
 *
 * **Isto vale para a API, e nao para o Supabase Auth.** La um 401 ou 400 significa credencial
 * recusada, e nao sessao expirada; `AutenticacaoSupabase` recebe o cliente cru de `clienteHttp` e
 * classifica por conta propria, como a decisao 8 fixou.
 */
fun clienteApi(
    http: HttpClient,
    credencial: () -> String?,
    aoExpirarSessao: () -> Unit,
): HttpClient = http.config {
    defaultRequest {
        credencial()?.let { bearerAuth(it) }
    }

    HttpResponseValidator {
        validateResponse { resposta ->
            if (resposta.status == HttpStatusCode.Unauthorized) aoExpirarSessao()
        }
    }
}
