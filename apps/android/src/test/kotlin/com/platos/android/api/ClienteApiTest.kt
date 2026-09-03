package com.platos.android.api

import com.platos.android.net.clienteHttp
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.get
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.io.IOException
import java.net.UnknownHostException

/**
 * O ponto unico da decisao 3, verificado como ponto unico.
 *
 * Os testes aqui nao passam por `ApiPlatos`, e isso e o metodo, nao economia. A afirmacao da tarefa
 * 4.3 e "nenhuma tela trata 401 por conta propria" — uma afirmacao sobre **qualquer** chamada,
 * inclusive as que ninguem escreveu ainda. Exercitar o cliente direto, em rotas inventadas na hora,
 * e o que permite afirmar isso sem confiar na disciplina de quem escrever a proxima.
 */
class ClienteApiTest {

    private var expiracoes = 0

    private fun cliente(
        status: Int = 200,
        erro: IOException? = null,
        credencial: () -> String? = { "tok-abc" },
    ): HttpClient {
        val engine = MockEngine {
            if (erro != null) throw erro
            respond(
                content = "{}",
                status = HttpStatusCode.fromValue(status),
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        return clienteApi(
            http = clienteHttp(engine),
            credencial = credencial,
            aoExpirarSessao = { expiracoes++ },
        )
    }

    // --- O 401, venha de onde vier ---

    @Test
    fun `401 leva a sessao a expirar`() = runBlocking {
        cliente(status = 401).get("https://api.platos.example/me/organizations")

        assertEquals(1, expiracoes)
    }

    @Test
    fun `401 numa rota que ninguem previu tambem leva`() = runBlocking {
        val http = cliente(status = 401)

        // Nao existe metodo em `ApiPlatos` para nenhuma destas. E esse o ponto: o endpoint que
        // alguem escrever na 4a ja nasce coberto, sem que essa pessoa faca nada.
        http.get("https://api.platos.example/exam-packages/42")
        http.get("https://api.platos.example/qualquer/coisa/futura")

        assertEquals(2, expiracoes)
    }

    @Test
    fun `um 401 dispara uma expiracao, e nao duas`() = runBlocking {
        cliente(status = 401).get("https://api.platos.example/me/organizations")

        assertEquals(1, expiracoes)
    }

    // --- O que nao e 401 nao mexe na sessao ---

    @Test
    fun `resposta de sucesso nao expira sessao`() = runBlocking {
        cliente(status = 200).get("https://api.platos.example/me/organizations")

        assertEquals(0, expiracoes)
    }

    @Test
    fun `403 nao e sessao expirada`() = runBlocking {
        cliente(status = 403).get("https://api.platos.example/me/organizations")

        assertEquals(0, expiracoes)
    }

    @Test
    fun `500 nao e sessao expirada`() = runBlocking {
        cliente(status = 500).get("https://api.platos.example/me/organizations")

        assertEquals(0, expiracoes)
    }

    @Test
    fun `falha de transporte nao e sessao expirada`() {
        val http = cliente(erro = UnknownHostException("api.platos.example"))

        runCatching {
            runBlocking { http.get("https://api.platos.example/me/organizations") }
        }

        // Sem rede o aparelho nao sabe nada sobre a sessao. Apaga-la aqui mandaria o professor
        // digitar a senha de novo por causa de um tunel que caiu.
        assertEquals(0, expiracoes)
    }

    // --- A credencial, pelo mesmo ponto unico ---

    @Test
    fun `a credencial vai em qualquer rota, e nao so nas previstas`() = runBlocking {
        var vistos = mutableListOf<String?>()
        val engine = MockEngine { pedido ->
            vistos += pedido.headers[HttpHeaders.Authorization]
            respond("{}", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
        }
        val http = clienteApi(clienteHttp(engine), { "tok-abc" }, aoExpirarSessao = {})

        http.get("https://api.platos.example/exam-packages/42")
        http.get("https://api.platos.example/outra/rota")

        assertEquals(listOf("Bearer tok-abc", "Bearer tok-abc"), vistos)
    }
}
