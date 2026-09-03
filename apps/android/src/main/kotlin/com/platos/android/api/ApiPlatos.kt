package com.platos.android.api

import com.platos.android.net.Retorno
import com.platos.android.net.retornoDe
import com.platos.android.session.Organizacao
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse

/**
 * A API do PLATOS, vista do aparelho.
 *
 * E o segundo destino de rede da decisao 2: a credencial nasce no Supabase Auth, e todo dado de
 * dominio vem daqui. Adaptador fino, como `AutenticacaoSupabase` — traduz, e nao decide.
 *
 * **Nem a credencial nem o 401 sao tratados aqui.** Os dois vivem em [clienteApi], que e o ponto
 * unico da decisao 3 — e existe como funcao separada para que "nenhuma tela trata 401 por conta
 * propria" seja verificavel sobre o cliente, e nao sobre a disciplina de quem escreve a proxima
 * chamada. Este arquivo so sabe a rota e a forma do contrato.
 *
 * [organizacoes] devolve `Retorno` cru de proposito: o 401 ja levou a sessao de volta a entrada
 * pelo interceptador antes de esta funcao retornar, e traduzi-lo tambem aqui seria a duplicacao que
 * a tarefa 4.4 existe para demonstrar.
 */
class ApiPlatos(
    http: HttpClient,
    private val urlBase: String,
    credencial: () -> String?,
    aoExpirarSessao: () -> Unit,
) {

    private val autenticado: HttpClient = clienteApi(http, credencial, aoExpirarSessao)

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
