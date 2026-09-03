package com.platos.android.api

import com.platos.android.net.Retorno
import com.platos.android.net.clienteHttp
import com.platos.android.session.Organizacao
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.JsonConvertException
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.io.IOException
import java.net.UnknownHostException

/**
 * O adaptador da API, sem rede e sem aparelho.
 *
 * `MockEngine` responde no lugar do servidor, entao o que entra aqui e resposta **de verdade** —
 * status, cabecalhos e corpo. Mesma fronteira de `AutenticacaoSupabaseTest`: la se verifica a
 * traducao da entrada, aqui a da consulta.
 *
 * O corpo de sucesso nao foi inventado. Ele e a forma que `OrganizationDto` serializa, com os
 * valores que `MeOrganizationsTest` afirma do lado do servidor — `personal` e `owner` para a
 * organizacao pessoal do primeiro acesso. **E este literal que fecha a deriva** entre o contrato do
 * servidor e o espelho do aparelho: se um dos dois mudar sozinho, e aqui que aparece.
 */
class ApiPlatosTest {

    private val corpoDeUma =
        """[{"id":"org-1","name":"Nova Professora","kind":"personal","role":"owner"}]"""

    private val corpoDeDuas =
        """[{"id":"org-a","name":"Escola A","kind":"school","role":"teacher"},""" +
            """{"id":"org-b","name":"Escola B","kind":"school","role":"teacher"}]"""

    private sealed interface Resposta {
        data class Corpo(val status: Int, val corpo: String) : Resposta
        data class Estoura(val erro: IOException) : Resposta
    }

    private val pedidos = mutableListOf<HttpRequestData>()

    private fun api(
        credencial: () -> String? = { "tok-abc" },
        responder: (HttpRequestData) -> Resposta,
    ): ApiPlatos {
        val engine = MockEngine { pedido ->
            pedidos += pedido
            when (val r = responder(pedido)) {
                is Resposta.Corpo -> respond(
                    content = r.corpo,
                    status = HttpStatusCode.fromValue(r.status),
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                )
                is Resposta.Estoura -> throw r.erro
            }
        }
        return ApiPlatos(
            http = clienteHttp(engine),
            urlBase = "https://api.platos.example",
            credencial = credencial,
        )
    }

    private fun respondendo(corpo: String, status: Int = 200) =
        api { Resposta.Corpo(status, corpo) }

    /**
     * O valor de um [Retorno.Respondeu].
     *
     * `assertInstanceOf(Retorno.Respondeu::class.java, ...)` nao serve aqui: `::class.java` de
     * uma classe generica devolve o tipo cru, `valor` sai como `Any?`, e o teste passaria a
     * afirmar sobre nada. Com `is`, o smart cast do Kotlin preserva `T` e nao ha cast
     * nao-verificado.
     */
    private fun <T> valorDe(retorno: Retorno<T>): T {
        if (retorno !is Retorno.Respondeu) throw AssertionError("esperava Respondeu, veio $retorno")
        return retorno.valor
    }

    // --- A credencial em cada chamada ---

    @Test
    fun `a credencial da sessao viaja como Bearer na chamada`() = runBlocking {
        respondendo(corpoDeUma).organizacoes()

        assertEquals("Bearer tok-abc", pedidos.single().headers[HttpHeaders.Authorization])
    }

    @Test
    fun `a credencial e lida a cada chamada, e nao congelada na construcao`() = runBlocking {
        var atual: String? = "tok-do-primeiro"
        val api = api(credencial = { atual }) { Resposta.Corpo(200, corpoDeUma) }

        api.organizacoes()
        atual = "tok-do-segundo"
        api.organizacoes()

        assertEquals("Bearer tok-do-primeiro", pedidos[0].headers[HttpHeaders.Authorization])
        assertEquals("Bearer tok-do-segundo", pedidos[1].headers[HttpHeaders.Authorization])
    }

    @Test
    fun `sem sessao guardada a chamada sai sem Authorization`() = runBlocking {
        api(credencial = { null }) { Resposta.Corpo(401, "{}") }.organizacoes()

        assertNull(pedidos.single().headers[HttpHeaders.Authorization])
    }

    @Test
    fun `a consulta bate na rota que o servidor expoe`() = runBlocking {
        respondendo(corpoDeUma).organizacoes()

        assertEquals("/me/organizations", pedidos.single().url.encodedPath)
    }

    // --- O contrato ---

    @Test
    fun `o nome apresentado vem do campo name do contrato`() = runBlocking {
        val retorno = respondendo(corpoDeUma).organizacoes()

        val organizacoes = valorDe(retorno)
        assertEquals("org-1", organizacoes.single().id)
        assertEquals("Nova Professora", organizacoes.single().nome)
    }

    @Test
    fun `duas organizacoes chegam na ordem em que o servidor mandou`() = runBlocking {
        val retorno = respondendo(corpoDeDuas).organizacoes()

        val organizacoes = valorDe(retorno)
        assertEquals(listOf("Escola A", "Escola B"), organizacoes.map { it.nome })
    }

    @Test
    fun `lista vazia chega como lista vazia, e nao como falha`() = runBlocking {
        val retorno = respondendo("[]").organizacoes()

        assertEquals(emptyList<Organizacao>(), valorDe(retorno))
    }

    @Test
    fun `campo novo no servidor nao derruba a consulta`() = runBlocking {
        val comCampoNovo =
            """[{"id":"org-1","name":"Nova Professora","kind":"personal","role":"owner",""" +
                """"created_at":"2026-09-03T00:00:00Z"}]"""

        val retorno = respondendo(comCampoNovo).organizacoes()

        assertEquals("Nova Professora", valorDe(retorno).single().nome)
    }

    @Test
    fun `contrato quebrado estoura em vez de virar organizacao sem nome`() {
        val semNome = """[{"id":"org-1","kind":"personal","role":"owner"}]"""

        assertThrows(JsonConvertException::class.java) {
            runBlocking { respondendo(semNome).organizacoes() }
        }
    }

    // --- As falhas, no vocabulario unico de `Retorno` ---

    @Test
    fun `401 chega cru, e nao interpretado como sessao expirada aqui`() = runBlocking {
        val retorno = respondendo("{}", status = 401).organizacoes()

        assertEquals(401, assertInstanceOf(Retorno.Recusou::class.java, retorno).status)
    }

    @Test
    fun `falha de transporte e SemRede`() = runBlocking {
        val retorno = api { Resposta.Estoura(UnknownHostException("api.platos.example")) }
            .organizacoes()

        assertInstanceOf(Retorno.SemRede::class.java, retorno)
        Unit
    }
}
