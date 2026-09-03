package com.platos.android.auth

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** O corpo do pedido de entrada. Dois campos, e o endpoint nao aceita mais nada aqui. */
@Serializable
internal data class PedidoDeEntrada(
    val email: String,
    val password: String,
)

/**
 * O que o endpoint devolve quando a entrada fecha.
 *
 * **O corpo de sucesso nao foi medido.** O probe da tarefa 1.1 caracterizou o corpo de **erro** —
 * `{"code":400,"error_code":"invalid_credentials","msg":"..."}` — porque ele entra com senha errada
 * de proposito; nenhuma rodada chegou a autenticar. Os nomes abaixo vem da documentacao do Supabase
 * Auth, e nao de uma medicao desta base. Quem fecha isso e a tarefa 6.1, que entra com credencial
 * valida num projeto real.
 *
 * Por isso [accessToken] e o unico campo sem padrao: e o unico de que o aplicativo depende, e se ele
 * nao vier a desserializacao estoura em vez de produzir uma sessao vazia que so falharia depois, na
 * primeira chamada a API, com mensagem que nao seria sobre a entrada. Os outros ganham padrao para
 * que um campo ausente nao derrube a entrada do professor por causa de dado que ninguem usa —
 * [refreshToken] inclusive, que so tera uso quando refresh existir, e refresh e Non-Goal desta fatia.
 */
@Serializable
data class CredencialDeSessao(
    @SerialName("access_token") val accessToken: String,
    @SerialName("token_type") val tokenType: String = "bearer",
    @SerialName("expires_in") val expiresIn: Long = 0,
    @SerialName("refresh_token") val refreshToken: String = "",
)
