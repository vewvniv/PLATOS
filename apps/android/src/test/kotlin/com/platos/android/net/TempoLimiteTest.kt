package com.platos.android.net

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Servidor que aceita a conexao e nao responde.
 *
 * E o caso que nenhuma das outras verificacoes alcanca: `UnknownHostException` e `ConnectException`
 * chegam depressa, e este nao chega nunca. Sem tempo limite, `Consultando` fica para sempre — que e
 * o que a spec proibe ao dizer que o aplicativo nao fica em carregamento sem desfecho.
 *
 * O atraso e do `MockEngine`, e o tempo limite entra por parametro: verificar os 90 s de producao
 * custaria 90 s por rodada, e o que se afirma aqui e o **mecanismo** — que o estouro vira `SemRede`
 * pelo caminho que ja existia, e nao por classificacao nova.
 */
class TempoLimiteTest {

    private fun clienteQueDemora(atrasoMs: Long, tempoLimiteMs: Long) =
        clienteHttp(
            engine = MockEngine {
                delay(atrasoMs)
                // Texto puro, e nao JSON: sem o tempo limite a chamada **completa**, e o teste
                // falha dizendo que veio `Respondeu` onde se esperava `SemRede`. Com um corpo JSON
                // ela falharia por desserializacao, e o vermelho falaria de outra coisa.
                respond("ok", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, ContentType.Text.Plain.toString()))
            },
            tempoLimiteMs = tempoLimiteMs,
        )

    @Test
    fun `servidor que nao responde vira SemRede, e nao espera para sempre`() = runBlocking {
        val http = clienteQueDemora(atrasoMs = 2_000, tempoLimiteMs = 200)

        val retorno = retornoDe<String> { http.get("https://api.platos.example/me/organizations") }

        // Pelo caminho de sempre: o estouro do Ktor e `IOException`, e `retornoDe` ja o classifica.
        assertInstanceOf(Retorno.SemRede::class.java, retorno)
        Unit
    }

    @Test
    fun `resposta dentro do tempo limite passa normalmente`() = runBlocking {
        val http = clienteQueDemora(atrasoMs = 10, tempoLimiteMs = 5_000)

        val resposta: HttpResponse = http.get("https://api.platos.example/me/organizations")

        assertTrue(resposta.status.value == 200)
    }

    @Test
    fun `o tempo limite de producao e generoso o bastante para um cold start`() {
        // Nao e o numero que se afirma — ele e provisorio ate a tarefa 4.7a medir. O que se afirma e
        // o piso: abaixo de 60 s um cold start do Render seria interrompido e apresentado como falta
        // de rede, e o professor leria "confira a conexao" com a conexao boa.
        assertTrue(
            TEMPO_LIMITE_DE_PEDIDO_MS >= 60_000,
            "tempo limite em $TEMPO_LIMITE_DE_PEDIDO_MS ms: cold start viraria SemRede",
        )
    }
}
