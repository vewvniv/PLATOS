package com.platos.android.api

import com.platos.android.net.Retorno
import com.platos.android.net.retornoDe
import com.platos.android.session.Organizacao
import io.ktor.client.HttpClient
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse

/**
 * A API do PLATOS, vista do aparelho.
 *
 * E o segundo destino de rede da decisao 2: a credencial nasce no Supabase Auth, e todo dado de
 * dominio vem daqui. Adaptador fino, como `AutenticacaoSupabase` — traduz, e nao decide.
 *
 * **A credencial entra por `defaultRequest`, e nao em cada chamada.** A diferenca e estrutural, e e
 * a mesma razao da decisao 3 para o 401: o que depende de quem escreve a proxima chamada lembrar
 * vale hoje e fura no proximo endpoint. Aqui existe um metodo so, e mesmo assim o cabecalho nao e
 * posto por ele — quem acrescentar o segundo nao tem como esquecer, porque nao ha nada para
 * lembrar.
 *
 * [credencial] e funcao, e nao valor: a sessao troca — entra outro usuario, ou o token e apagado ao
 * sair — e um valor lido na construcao congelaria a credencial de quem entrou primeiro. Devolver
 * `null` e legitimo, e a chamada sai sem `Authorization`; o servidor responde 401, que e o que a
 * ausencia de sessao significa.
 *
 * **O 401 nao e interpretado aqui.** [organizacoes] devolve `Retorno` cru, e sessao expirada nasce
 * num ponto unico do cliente — tarefa 4.3. Traduzi-lo neste metodo seria o defeito que a 4.4 existe
 * para demonstrar.
 */
class ApiPlatos(
    http: HttpClient,
    private val urlBase: String,
    credencial: () -> String?,
) {

    private val autenticado: HttpClient = http.config {
        defaultRequest {
            credencial()?.let { bearerAuth(it) }
        }
    }

    /**
     * As organizacoes do usuario da sessao.
     *
     * A rota ja faz o provisionamento idempotente da organizacao pessoal antes de listar (D-0.4), e
     * esta fatia nao mexe nisso: do lado do aparelho, primeira abertura e abertura seguinte sao a
     * mesma chamada.
     */
    suspend fun organizacoes(): Retorno<List<Organizacao>> =
        when (val retorno = retornoDe<List<OrganizacaoDto>> { pedirOrganizacoes() }) {
            is Retorno.Respondeu -> Retorno.Respondeu(retorno.valor.map { it.paraOrganizacao() })
            is Retorno.Recusou -> retorno
            is Retorno.SemRede -> Retorno.SemRede
        }

    private suspend fun pedirOrganizacoes(): HttpResponse =
        autenticado.get("$urlBase/me/organizations")
}
