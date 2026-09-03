package com.platos.android.net

import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
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
/**
 * Tempo limite de um pedido inteiro. **Provisorio: 90 s nao foi medido contra o nosso servidor.**
 *
 * O numero tem base, e nao e chute: o cold start do plano gratuito do Render fica tipicamente entre
 * 30 e 60 s — `docs/deploy-api.md` registra que o servico e suspenso depois de ~15 min sem trafego —
 * e 90 s da folga sobre o pior caso relatado. Errar para baixo e o erro caro: um cold start
 * interrompido chega como `IOException`, vira `SemRede`, e o professor le "confira a conexao" com a
 * conexao boa, na primeira vez que abre o aplicativo no dia. Errar para cima so custa espera.
 *
 * Quem fecha o numero e a tarefa 4.7a, medindo contra o servico real, junto da secao 6 — nenhuma
 * das conferencias em aparelho acontece sem esse servico existir.
 */
const val TEMPO_LIMITE_DE_PEDIDO_MS: Long = 90_000

fun clienteHttp(
    engine: HttpClientEngine = OkHttp.create(),
    tempoLimiteMs: Long = TEMPO_LIMITE_DE_PEDIDO_MS,
): HttpClient = HttpClient(engine) {
    /**
     * Sem isto, servidor que aceita a conexao e nunca responde deixa `Consultando` para sempre — e a
     * spec proibe ficar em carregamento sem desfecho. `Retorno` ja tinha sido escrito para este
     * caso: o comentario dele cita `HttpRequestTimeoutException` entre os `IOException` que viram
     * `SemRede`. O plugin e que faltava; classificacao nova, nenhuma.
     *
     * So `requestTimeoutMillis`. Ele cobre o pedido inteiro, que e o que o requisito pede; fixar
     * tambem conexao e soquete seria tres numeros para medir e justificar, quando um responde.
     */
    install(HttpTimeout) {
        requestTimeoutMillis = tempoLimiteMs
    }

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
