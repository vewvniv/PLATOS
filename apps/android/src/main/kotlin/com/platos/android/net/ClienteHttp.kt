package com.platos.android.net

import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

/**
 * O cliente HTTP do aparelho.
 *
 * [engine] e parametro para que o teste de JVM possa passar `MockEngine`. O padrao e OkHttp, que e
 * o engine do Android e o mesmo que o probe da tarefa 1.1 mediu — trocar o engine trocaria o tipo
 * da excecao de transporte, e a classificacao de [retornoDe] depende dela.
 *
 * `expectSuccess` fica falso (padrao do Ktor): resposta 4xx ou 5xx **nao** vira excecao, e chega a
 * [retornoDe] como resposta com status. Liga-lo faria falha de servidor e falha de transporte
 * entrarem pelo mesmo `catch`, que e exatamente a distincao que esta fatia precisa manter.
 */
fun clienteHttp(engine: HttpClientEngine = OkHttp.create()): HttpClient = HttpClient(engine) {
    install(ContentNegotiation) {
        json(
            Json {
                // O provedor acrescenta campos sem avisar, e campo novo nao pode derrubar a
                // entrada do professor. O que o aplicativo exige esta declarado no DTO.
                ignoreUnknownKeys = true
            },
        )
    }
}
