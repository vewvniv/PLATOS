package com.platos.android.auth

import com.platos.android.net.Retorno
import com.platos.android.net.retornoDe
import com.platos.android.session.ResultadoDaEntrada
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.contentType

/** O que a autenticacao produziu. E [ResultadoDaEntrada] mais a credencial, que a sessao nao ve. */
sealed interface ResultadoDaAutenticacao {

    /**
     * [credencial] fica fora de [ResultadoDaEntrada] de proposito: `DeviceSession` decide o que a
     * tela mostra, e para isso nao precisa do token. Quem recebe daqui guarda a credencial
     * (tarefa 4.5) e entrega a sessao so o que ela usa.
     */
    data class Autenticado(val credencial: CredencialDeSessao) : ResultadoDaAutenticacao

    data object CredencialRecusada : ResultadoDaAutenticacao

    data object SemRede : ResultadoDaAutenticacao
}

/** A traducao para o que a maquina de estados aceita. Sem decisao: e um `when` exaustivo. */
fun ResultadoDaAutenticacao.paraSessao(): ResultadoDaEntrada = when (this) {
    is ResultadoDaAutenticacao.Autenticado -> ResultadoDaEntrada.Autenticado
    is ResultadoDaAutenticacao.CredencialRecusada -> ResultadoDaEntrada.CredencialRecusada
    is ResultadoDaAutenticacao.SemRede -> ResultadoDaEntrada.SemRede
}

/**
 * Entrada por e-mail e senha, direto no endpoint de auth do Supabase.
 *
 * **Adaptador fino, e sem `supabase-kt`.** A decisao 9 registra por que; a 8 traz a medicao que a
 * fechou. O probe da tarefa 1.1 bateu
 * no mesmo endpoint pelas duas vias e recebeu a mesma resposta, e a biblioteca ainda descartava a
 * causa da excecao de transporte (`HttpRequestException.cause == null`), que e justamente o que a
 * distincao exigida pela spec usa. Sem ela o modulo fica em `compileSdk 35`, sem `androidx.browser`
 * e com uma pilha HTTP so.
 *
 * [urlBase] e [chaveAnonima] entram por parametro. Este arquivo nao le configuracao: de onde eles
 * vem e a tarefa 2.2, e a decisao 6 continua valendo sem mudanca — nada disso e versionado.
 */
class AutenticacaoSupabase(
    private val http: HttpClient,
    private val urlBase: String,
    private val chaveAnonima: String,
) {

    /**
     * Uma tentativa de entrar.
     *
     * O `when` sobre o status e o unico julgamento aqui, e ele e deliberado:
     *
     * - **4xx e credencial recusada.** O servidor respondeu e recusou o que foi enviado. O probe
     *   mediu o caso concreto: HTTP 400 com `error_code: invalid_credentials`.
     * - **Qualquer outra resposta sem sucesso — 5xx inclusive — e [ResultadoDaAutenticacao.SemRede].**
     *   Nao e imprecisao: nesta fatia `SemRede` **significa** "nao deu para falar com o servidor", e
     *   a decisao 8 ja o define assim ao colapsar DNS e porta recusada. Um 500 nao e culpa da senha
     *   de quem esta entrando, e apresenta-lo como credencial recusada mandaria o professor
     *   redigitar uma senha correta ate desistir. Essa e a troca, e ela e a favor de quem le a tela.
     */
    suspend fun entrar(email: String, senha: String): ResultadoDaAutenticacao {
        val retorno = retornoDe<CredencialDeSessao> { pedir(email, senha) }
        return when (retorno) {
            is Retorno.Respondeu -> ResultadoDaAutenticacao.Autenticado(retorno.valor)
            is Retorno.Recusou ->
                if (retorno.status in 400..499) {
                    ResultadoDaAutenticacao.CredencialRecusada
                } else {
                    ResultadoDaAutenticacao.SemRede
                }
            is Retorno.SemRede -> ResultadoDaAutenticacao.SemRede
        }
    }

    private suspend fun pedir(email: String, senha: String): HttpResponse =
        http.post("$urlBase/auth/v1/token?grant_type=password") {
            // A chave anonima identifica o projeto, e nao o usuario. Ela nao e segredo — e o token
            // que sai daqui e que e.
            header("apikey", chaveAnonima)
            contentType(ContentType.Application.Json)
            setBody(PedidoDeEntrada(email = email, password = senha))
        }
}
