package com.platos.android.net

import io.ktor.client.call.body
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.readRawBytes
import io.ktor.http.isSuccess
import java.io.IOException

/**
 * O que uma chamada HTTP produziu, no **vocabulario unico** do aplicativo.
 *
 * Unico e a palavra que importa. A autenticacao e a consulta de dados sao dois destinos diferentes
 * (decisao 2), e se cada uma classificasse a falha do seu jeito o mesmo aparelho sem rede diria uma
 * coisa na entrada e outra na consulta. Quem interpreta o resultado decide o que ele significa —
 * 401 e sessao expirada para a API e credencial recusada para a entrada —, mas a classificacao
 * bruta acontece aqui, num lugar so.
 */
sealed interface Retorno<out T> {

    /** O servidor respondeu com sucesso, e o corpo foi lido. */
    data class Respondeu<T>(val valor: T) : Retorno<T>

    /**
     * O servidor respondeu, e a resposta nao foi sucesso. [status] chega cru de proposito: o que um
     * 401 significa depende de quem perguntou, e essa decisao nao e desta camada.
     */
    data class Recusou(val status: Int) : Retorno<Nothing>

    /**
     * Nao houve resposta: o pedido nao chegou, ou a resposta nao voltou.
     *
     * DNS que nao resolve, porta que recusa, tempo esgotado e rede que cai no meio caem todos aqui.
     * A decisao 8 registra por que eles nao sao separados: para quem le a tela a acao e a mesma, e
     * separa-los exigiria classificar por texto de mensagem.
     */
    data object SemRede : Retorno<Nothing>
}

/**
 * Roda [chamada] e classifica o que sair dela **pelo lado da falha**, nunca pelo texto.
 *
 * `IOException` e a fronteira, e ela e larga de proposito: `UnknownHostException`,
 * `ConnectException` e o `HttpRequestTimeoutException` do Ktor sao todos `IOException` na JVM, e o
 * probe da tarefa 1.1 confirmou os dois primeiros chegando assim do engine OkHttp, no aparelho.
 *
 * **Nada mais e capturado.** Corpo que nao bate com o DTO chega como `JsonConvertException`, que
 * desce de `Exception` e nao de `IOException` — entao sobe, e sobe de proposito: engoli-lo aqui
 * transformaria contrato quebrado em "sem rede", que e a forma de falha silenciosa desta camada.
 * Quem chama nao trata o desconhecido; ele estoura.
 */
suspend inline fun <reified T> retornoDe(chamada: () -> HttpResponse): Retorno<T> =
    try {
        val resposta = chamada()
        if (resposta.status.isSuccess()) {
            Retorno.Respondeu(resposta.body<T>())
        } else {
            Retorno.Recusou(resposta.status.value)
        }
    } catch (e: IOException) {
        Retorno.SemRede
    }

/**
 * O corpo cru de uma resposta, com o cabecalho que o descreve.
 *
 * Os dois viajam juntos porque quem confere precisa dos dois: separa-los em duas leituras abriria a
 * possibilidade de conferir bytes contra o hash de outra resposta.
 */
data class CorpoCru(
    val bytes: ByteArray,
    val cabecalhos: Map<String, String>,
) {
    // `ByteArray` compara por identidade, e este tipo e usado dentro de `Retorno`, que e `data`.
    // Sem estes dois, dois corpos com os mesmos bytes seriam diferentes, e o teste que afirma
    // "o que chegou e o que foi mandado" passaria a depender de qual instancia foi comparada.
    override fun equals(other: Any?): Boolean =
        this === other ||
            (other is CorpoCru && bytes.contentEquals(other.bytes) && cabecalhos == other.cabecalhos)

    override fun hashCode(): Int = 31 * bytes.contentHashCode() + cabecalhos.hashCode()
}

/**
 * Roda [chamada] e devolve os **bytes exatos** que chegaram, sem passar pela desserializacao.
 *
 * Irmao de [retornoDe], e nao substituto: a classificacao da falha e a mesma — `IOException` e a
 * fronteira, tudo o mais sobe — porque a decisao 8 da fatia 4a-zero fixou um vocabulario unico de
 * falha para o aplicativo inteiro, e um segundo classificador aqui faria o mesmo aparelho sem rede
 * dizer uma coisa na consulta e outra no pull.
 *
 * **O que muda e so o corpo.** `retornoDe` chama `body<T>()`, que passa pelo `ContentNegotiation`;
 * para o pacote isso e errado por construcao, porque o que a conferencia de integridade hasheia
 * precisa ser o byte que chegou, e nao o resultado de desserializar e reserializar.
 */
suspend fun retornoDeBytes(
    cabecalhosDesejados: List<String> = emptyList(),
    chamada: suspend () -> HttpResponse,
): Retorno<CorpoCru> =
    try {
        val resposta = chamada()
        if (resposta.status.isSuccess()) {
            Retorno.Respondeu(
                CorpoCru(
                    bytes = resposta.readRawBytes(),
                    cabecalhos = cabecalhosDesejados.mapNotNull { nome ->
                        resposta.headers[nome]?.let { nome to it }
                    }.toMap(),
                ),
            )
        } else {
            Retorno.Recusou(resposta.status.value)
        }
    } catch (e: IOException) {
        Retorno.SemRede
    }
