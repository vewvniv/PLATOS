package com.platos.android.api

import com.platos.android.net.Retorno
import com.platos.android.net.clienteHttp
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Test

/** A nota do professor sobe por `POST .../results/graded`, e o 4xx/5xx chega como o aparelho ja os classifica. */
class ApiPlatosNotaTest {

    private val pedidos = mutableListOf<HttpRequestData>()

    private fun api(status: Int): ApiPlatos {
        val engine = MockEngine { pedido ->
            pedidos += pedido
            respond("{}", HttpStatusCode.fromValue(status), headersOf(HttpHeaders.ContentType, "application/json"))
        }
        return ApiPlatos(
            http = clienteHttp(engine),
            urlBase = "https://api.platos.example",
            credencial = { "tok-abc" },
            aoExpirarSessao = {},
        )
    }

    @Test
    fun `a nota vai para a rota graded, com a credencial`() = runBlocking {
        val retorno = api(200).enviarNota("org-1", "prova-r", """{"capture_id":"cap-nota-1"}""")

        assertInstanceOf(Retorno.Respondeu::class.java, retorno)
        val pedido = pedidos.single()
        assertEquals(HttpMethod.Post, pedido.method)
        assertEquals("https://api.platos.example/organizations/org-1/exams/prova-r/results/graded", pedido.url.toString())
        assertEquals("Bearer tok-abc", pedido.headers[HttpHeaders.Authorization])
    }

    @Test
    fun `o resultado continua indo para a rota de sempre`() = runBlocking {
        api(200).enviarResultado("org-1", "prova-r", "{}")

        assertEquals("https://api.platos.example/organizations/org-1/exams/prova-r/results", pedidos.single().url.toString())
    }

    @Test
    fun `400 e 500 da nota chegam como recusa com o status`() = runBlocking {
        assertEquals(Retorno.Recusou(400), api(400).enviarNota("o", "p", "{}"))
        assertEquals(Retorno.Recusou(500), api(500).enviarNota("o", "p", "{}"))
    }
}
